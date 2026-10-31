package com.aparcar.api.service.impl;

import com.aparcar.api.dto.puntos.BilleteraResponseDto;
import com.aparcar.api.dto.puntos.MovimientoPuntosResponseDto;
import com.aparcar.api.dto.puntos.ResumenPuntosResponseDto;
import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.dto.reserva.ReservaPuntosDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.puntos.FranjaPuntos;
import com.aparcar.api.entity.puntos.MovimientoPuntos;
import com.aparcar.api.entity.puntos.TipoMovimientoPuntos;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.BilleteraPuntosRepository;
import com.aparcar.api.repository.MovimientoPuntosRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IPuntosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PuntosService implements IPuntosService {
    private static final int MAX_MOVIMIENTOS = 100;
    private static final DateTimeFormatter DIA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final BilleteraPuntosRepository billeteraRepository;
    private final MovimientoPuntosRepository movimientoRepository;
    private final VisitanteRepository visitanteRepository;
    private final CreadorBilleteras creadorBilleteras;
    private final ReglasPuntos reglas;
    private final Clock clock;

    // ---- Consultas ----

    @Override
    @Transactional(readOnly = true)
    public BilleteraResponseDto billetera(String email) {
        Visitante visitante = buscarVisitante(email);
        // Sin billetera todavia: se muestra en cero, sin crearla.
        BilleteraPuntos billetera = billeteraRepository.findByVisitanteId(visitante.getId())
                .orElseGet(() -> new BilleteraPuntos(visitante.getId()));
        int racha = reglas.rachaVigente(billetera, LocalDate.now(clock));
        List<BilleteraResponseDto.FranjaDto> franjas = Arrays.stream(FranjaPuntos.values())
                .map(f -> new BilleteraResponseDto.FranjaDto(f.name(), f.getNombre(),
                        f.getInicio().format(HORA), f.getFin().format(HORA), f.getPuntos()))
                .toList();
        return new BilleteraResponseDto(
                billetera.getSaldo(), racha, billetera.getMejorRacha(), billetera.getUltimoDiaRacha(),
                reglas.diasParaBonus(racha), reglas.rachaDias(), reglas.bonusRacha(),
                reglas.descuentoMaxPct(), ReglasPuntos.PUNTOS_POR_PORCENTAJE, reglas.maxPuntosCanjeables(),
                reglas.duracionMinimaMin(), reglas.maxAcreditacionesDia(), franjas);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoPuntosResponseDto> movimientos(String email) {
        Visitante visitante = buscarVisitante(email);
        return movimientoRepository.findByVisitanteIdOrderByFechaDescIdDesc(visitante.getId(), Limit.of(MAX_MOVIMIENTOS))
                .stream()
                .map(m -> new MovimientoPuntosResponseDto(m.getId(), m.getTipo(), m.getPuntos(), m.getReservaId(),
                        m.getDescripcion(), m.getFecha()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenPuntosResponseDto resumen() {
        long emitidos = movimientoRepository.sumarPorTipo(TipoMovimientoPuntos.ACREDITACION_FRANJA)
                + movimientoRepository.sumarPorTipo(TipoMovimientoPuntos.BONUS_RACHA);
        // Los canjes son negativos y los reintegros positivos: netos, en positivo.
        long canjeados = -(movimientoRepository.sumarPorTipo(TipoMovimientoPuntos.CANJE)
                + movimientoRepository.sumarPorTipo(TipoMovimientoPuntos.REINTEGRO));
        long conRacha = billeteraRepository.countByRachaActualGreaterThanAndUltimoDiaRachaGreaterThanEqual(
                0, LocalDate.now(clock).minusDays(1));
        return new ResumenPuntosResponseDto(emitidos, canjeados, emitidos - canjeados, conRacha,
                billeteraRepository.maximaMejorRacha());
    }

    @Override
    @Transactional(readOnly = true)
    public CotizacionResponseDto cotizar(CotizacionResponseDto base, String email, LocalDateTime desde,
                                         LocalDateTime hasta, BigDecimal puntos, boolean usarTodos) {
        Visitante visitante = email == null ? null : visitanteRepository.findByEmail(email).orElse(null);
        if (visitante == null) return base;

        int saldo = billeteraRepository.findByVisitanteId(visitante.getId()).map(BilleteraPuntos::getSaldo).orElse(0);
        ReglasPuntos.Estimacion estimacion = reglas.estimar(desde, hasta);
        int maxUsable = Math.min(saldo, reglas.maxPuntosCanjeables());
        ReglasPuntos.Descuento descuento = puntos == null && !usarTodos
                ? reglas.descuento(base.total(), 0)
                : reglas.descuento(base.total(), resolverPuntos(visitante, saldo, puntos, usarTodos));
        return new CotizacionResponseDto(base.tipo(), descuento.precioFinal(), base.moneda(), base.jornadas(),
                base.mediasJornadas(), base.horas(), base.fracciones(), base.total(), descuento.porcentaje(),
                descuento.monto(), descuento.puntos(), saldo, maxUsable, estimacion.puntos(), estimacion.motivo());
    }

    // ---- Canje y reintegro ----

    @Override
    @Transactional
    public ReglasPuntos.Descuento prepararCanje(Visitante titular, BigDecimal subtotal, BigDecimal puntos,
                                                boolean usarTodos) {
        // Bloqueada hasta el fin de la transaccion de la reserva.
        int saldo = billeteraRepository.findParaActualizar(titular.getId()).map(BilleteraPuntos::getSaldo).orElse(0);
        return reglas.descuento(subtotal, resolverPuntos(titular, saldo, puntos, usarTodos));
    }

    @Override
    @Transactional
    public void confirmarCanje(Visitante titular, Reserva reserva, ReglasPuntos.Descuento descuento) {
        BilleteraPuntos billetera = billeteraRepository.findParaActualizar(titular.getId())
                .orElseThrow(() -> new ConflictoException("Tus puntos cambiaron mientras reservabas. Volvé a intentar."));
        if (billetera.getSaldo() < descuento.puntos()) {
            throw new ConflictoException("Tus puntos cambiaron mientras reservabas. Volvé a intentar.");
        }
        billetera.setSaldo(billetera.getSaldo() - descuento.puntos());
        guardar(billetera, List.of(new MovimientoPuntos(titular.getId(), TipoMovimientoPuntos.CANJE, -descuento.puntos(),
                reserva.getId(), "Descuento del %s %% en la reserva del %s"
                .formatted(descuento.porcentaje().stripTrailingZeros().toPlainString(),
                        reserva.getDesde().format(DIA_Y_HORA)), clock.instant())));
    }

    @Override
    @Transactional
    public void reintegrar(Reserva reserva) {
        Integer usados = reserva.getPuntosUsados();
        if (usados == null || usados <= 0 || reserva.getPuntosReintegradosAt() != null) return;
        UUID visitanteId = reserva.getVisitante().getId();
        creadorBilleteras.crearSiFalta(visitanteId);
        // Con la billetera bloqueada, un segundo intento en paralelo ya ve el primero.
        BilleteraPuntos billetera = billeteraRepository.findParaActualizar(visitanteId).orElseThrow();
        if (movimientoRepository.existsByVisitanteIdAndReservaIdAndTipo(
                visitanteId, reserva.getId(), TipoMovimientoPuntos.REINTEGRO)) return;
        billetera.setSaldo(billetera.getSaldo() + usados);
        reserva.setPuntosReintegradosAt(clock.instant());
        guardar(billetera, List.of(new MovimientoPuntos(visitanteId, TipoMovimientoPuntos.REINTEGRO, usados,
                reserva.getId(), "Reintegro por cancelar la reserva del %s".formatted(reserva.getDesde().format(DIA_Y_HORA)),
                clock.instant())));
    }

    // ---- Acreditacion al ingresar ----

    @Override
    @Transactional
    public void acreditarIngreso(Reserva reserva, LocalDateTime ingreso) {
        if (reserva.getPuntosAcreditados() != null) return;
        UUID visitanteId = reserva.getVisitante().getId();
        creadorBilleteras.crearSiFalta(visitanteId);
        // Con la billetera bloqueada, un segundo intento en paralelo ya ve el primero.
        BilleteraPuntos billetera = billeteraRepository.findParaActualizar(visitanteId).orElseThrow();
        if (movimientoRepository.existsByVisitanteIdAndReservaIdAndTipo(
                visitanteId, reserva.getId(), TipoMovimientoPuntos.ACREDITACION_FRANJA)) {
            return;
        }
        List<MovimientoPuntos> movimientos = new ArrayList<>();
        Instant ahora = clock.instant();
        LocalDate dia = ingreso.toLocalDate();

        ReglasPuntos.Estimacion estimacion = reglas.estimar(reserva.getDesde(), reserva.getHasta());
        String motivo = estimacion.motivo();
        if (motivo == null && acreditacionesDelDia(visitanteId, dia) >= reglas.maxAcreditacionesDia()) {
            motivo = reglas.motivoTopeDiario();
        }
        if (motivo == null) {
            billetera.setSaldo(billetera.getSaldo() + estimacion.puntos());
            movimientos.add(new MovimientoPuntos(visitanteId, TipoMovimientoPuntos.ACREDITACION_FRANJA,
                    estimacion.puntos(), reserva.getId(), "Reserva del %s · %s"
                    .formatted(reserva.getDesde().format(DIA_Y_HORA), estimacion.franja().getNombre()), ahora));
            reserva.setPuntosAcreditados(estimacion.puntos());
        } else {
            reserva.setPuntosAcreditados(0);
            reserva.setPuntosMotivo(motivo);
        }

        // La racha cuenta el dia de cualquier ingreso, sume o no puntos de franja.
        if (reglas.contarDia(billetera, dia) && reglas.tocaBonus(billetera.getRachaActual())) {
            billetera.setSaldo(billetera.getSaldo() + reglas.bonusRacha());
            movimientos.add(new MovimientoPuntos(visitanteId, TipoMovimientoPuntos.BONUS_RACHA, reglas.bonusRacha(),
                    reserva.getId(), "¡Racha de %d días seguidos!".formatted(billetera.getRachaActual()), ahora));
        }
        guardar(billetera, movimientos);
    }

    private long acreditacionesDelDia(UUID visitanteId, LocalDate dia) {
        Instant desde = dia.atStartOfDay(clock.getZone()).toInstant();
        Instant hasta = dia.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return movimientoRepository.countByVisitanteIdAndTipoAndFechaGreaterThanEqualAndFechaLessThan(
                visitanteId, TipoMovimientoPuntos.ACREDITACION_FRANJA, desde, hasta);
    }

    @Override
    public ReservaPuntosDto deReserva(Reserva reserva) {
        int usados = reserva.getPuntosUsados() == null ? 0 : reserva.getPuntosUsados();
        ReglasPuntos.Estimacion estimacion = reglas.estimar(reserva.getDesde(), reserva.getHasta());
        boolean noSeUso = reserva.getEstado() == ReservaEstado.CANCELADA || reserva.getEstado() == ReservaEstado.VENCIDA;
        String motivo = reserva.getPuntosAcreditados() != null ? reserva.getPuntosMotivo()
                : noSeUso ? "La reserva no se usó: no suma puntos." : estimacion.motivo();
        int aGanar = reserva.getPuntosAcreditados() != null || noSeUso ? 0 : estimacion.puntos();
        return new ReservaPuntosDto(usados, reserva.getDescuentoPct(), reserva.getDescuentoMonto(),
                reserva.getPrecioFinal(), reserva.getPuntosReintegradosAt() != null, reserva.getPuntosAcreditados(),
                aGanar, motivo);
    }

    // ---- Auxiliares ----

    /**
     * Cuantos puntos se usan: un entero positivo que no pase del saldo ni del
     * tope, o con "usar todos" el minimo entre los dos. Los rechazos quedan en
     * el log con el id de la cuenta, sin datos personales.
     */
    private int resolverPuntos(Visitante visitante, int saldo, BigDecimal pedidos, boolean usarTodos) {
        int tope = reglas.maxPuntosCanjeables();
        if (usarTodos) {
            int puntos = Math.min(saldo, tope);
            if (puntos <= 0) throw rechazo(visitante, "No tenés puntos para usar.");
            return puntos;
        }
        if (pedidos == null || pedidos.signum() <= 0) {
            throw rechazo(visitante, "Indicá una cantidad de puntos mayor a cero.");
        }
        if (pedidos.stripTrailingZeros().scale() > 0) {
            throw rechazo(visitante, "Los puntos se usan enteros, sin decimales.");
        }
        if (pedidos.compareTo(BigDecimal.valueOf(saldo)) > 0) {
            throw rechazo(visitante, "No tenés puntos suficientes: tu saldo es de %d.".formatted(saldo));
        }
        if (pedidos.compareTo(BigDecimal.valueOf(tope)) > 0) {
            throw rechazo(visitante, "Podés usar hasta %d puntos por reserva: llegan al tope del %d %% de descuento."
                    .formatted(tope, reglas.descuentoMaxPct()));
        }
        return pedidos.intValueExact();
    }

    private ValidationException rechazo(Visitante visitante, String mensaje) {
        log.warn("Canje de puntos rechazado para la cuenta {}: {}", visitante.getId(), mensaje);
        return new ValidationException(mensaje);
    }

    /**
     * Flush adentro del metodo: un choque de version o de la restriccion unica
     * salta aca y se traduce a 409, en vez de explotar en el commit.
     */
    private void guardar(BilleteraPuntos billetera, List<MovimientoPuntos> movimientos) {
        try {
            billeteraRepository.saveAndFlush(billetera);
            movimientoRepository.saveAllAndFlush(movimientos);
        } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new ConflictoException("Tus puntos cambiaron al mismo tiempo por otra operación. Volvé a intentar.");
        }
    }

    private Visitante buscarVisitante(String email) {
        return visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }
}
