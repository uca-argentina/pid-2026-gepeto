package com.aparcar.api.service.impl;

import com.aparcar.api.dto.acceso.AccesoAccion;
import com.aparcar.api.dto.acceso.AccesoConsultaResponseDto;
import com.aparcar.api.dto.acceso.AccesoMotivo;
import com.aparcar.api.dto.acceso.AccesoMovimientoResponseDto;
import com.aparcar.api.dto.acceso.AccesoRegistroResponseDto;
import com.aparcar.api.dto.acceso.AccesoReservaDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.AccesoOperacion;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaAccion;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.ReservaMovimiento;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaMovimientoRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IAccesoService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.aparcar.api.service.impl.RegistroIntentosAcceso.CONFLICTO;
import static com.aparcar.api.service.impl.RegistroIntentosAcceso.FORMATO_INVALIDO;
import static com.aparcar.api.service.impl.RegistroIntentosAcceso.INEXISTENTE;
import static com.aparcar.api.service.impl.RegistroIntentosAcceso.OK;
import static com.aparcar.api.service.impl.RegistroIntentosAcceso.YA_REGISTRADO;

@Service
@RequiredArgsConstructor
public class AccesoService implements IAccesoService {
    private static final int MAX_MOVIMIENTOS = 200;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA_Y_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ReservaRepository reservaRepository;
    private final ReservaMovimientoRepository reservaMovimientoRepository;
    private final VisitanteRepository visitanteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final CocheraRepository cocheraRepository;
    private final ReglasAcceso reglasAcceso;
    private final RegistroIntentosAcceso registroIntentos;
    private final PlanificacionReservas planificacion;

    /** Lo que corresponde hacer con una reserva ahora, o por que no se puede. */
    private record Evaluacion(AccesoAccion accion, AccesoMotivo motivo, String mensaje) {
        boolean valida() {
            return motivo == null;
        }
    }

    // noRollbackFor: si la consulta descubre que la reserva ya estaba vencida,
    // ese cambio se conserva aunque despues se rechace el pedido.
    @Override
    @Transactional(noRollbackFor = {ValidationException.class, NotFoundException.class})
    public AccesoConsultaResponseDto consultar(String entrada, String operadorEmail) {
        planificacion.bloquear();
        Reserva reserva = buscar(entrada, AccesoOperacion.CONSULTA, operadorEmail);
        LocalDateTime ahora = reglasAcceso.ahora();
        asentarSiVencio(reserva, ahora);

        Evaluacion evaluacion = evaluar(reserva, ahora);
        registroIntentos.registrar(AccesoOperacion.CONSULTA, reserva.getCodigo(), reserva.getId(),
                evaluacion.valida() ? OK : evaluacion.motivo().name(), operadorEmail);

        return new AccesoConsultaResponseDto(
                CodigoAcceso.formatear(reserva.getCodigo()),
                evaluacion.valida(),
                evaluacion.accion(),
                evaluacion.motivo(),
                evaluacion.mensaje(),
                toReservaDto(reserva, ahora));
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, NotFoundException.class})
    public AccesoRegistroResponseDto registrar(String entrada, AccesoAccion accion, String operadorEmail) {
        planificacion.bloquear();
        AccesoOperacion operacion = accion == AccesoAccion.INGRESO ? AccesoOperacion.INGRESO : AccesoOperacion.EGRESO;
        Reserva reserva = buscar(entrada, operacion, operadorEmail);
        Visitante operador = visitanteRepository.findByEmail(operadorEmail)
                .orElseThrow(() -> new NotFoundException("Cuenta del autor no encontrada."));
        if (accion == AccesoAccion.INGRESO && reserva.getEstado() == ReservaEstado.CONFIRMADA) {
            // Siempre en el mismo orden. El lock de la reserva no alcanza:
            // dos codigos distintos pueden compartir vehiculo o cochera.
            vehiculoRepository.findByIdForUpdate(reserva.getVehiculo().getId())
                    .orElseThrow(() -> new NotFoundException("Vehiculo no encontrado."));
            cocheraRepository.findByIdForUpdate(reserva.getCochera().getId())
                    .orElseThrow(() -> new NotFoundException("Cochera no encontrada."));
        }
        LocalDateTime ahora = reglasAcceso.ahora();
        // Nunca se confia en una consulta previa: todo se vuelve a evaluar aca.
        asentarSiVencio(reserva, ahora);

        return accion == AccesoAccion.INGRESO
                ? registrarIngreso(reserva, operador, ahora)
                : registrarEgreso(reserva, operador, ahora);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccesoMovimientoResponseDto> movimientosDelDia() {
        return reservaMovimientoRepository.findRecientes(
                        List.of(ReservaAccion.INGRESO, ReservaAccion.EGRESO),
                        reglasAcceso.inicioDelDia(),
                        Limit.of(MAX_MOVIMIENTOS))
                .stream()
                .map(this::toMovimientoDto)
                .toList();
    }

    private AccesoRegistroResponseDto registrarIngreso(Reserva reserva, Visitante operador, LocalDateTime ahora) {
        switch (reserva.getEstado()) {
            case EN_CURSO -> {
                if (reglasAcceso.recienRegistrado(reserva.getIngresoAt(), ahora)) {
                    return yaRegistrado(reserva, AccesoAccion.INGRESO, reserva.getIngresoAt(), operador, ahora);
                }
                throw rechazar(reserva, AccesoOperacion.INGRESO, AccesoMotivo.INGRESO_YA_REGISTRADO,
                        "El ingreso ya se registró a las %s. El código ahora solo sirve para el egreso."
                                .formatted(fecha(reserva.getIngresoAt(), ahora)), operador);
            }
            case CONFIRMADA -> {
                Evaluacion evaluacion = evaluarIngreso(reserva, ahora);
                if (!evaluacion.valida()) {
                    throw rechazar(reserva, AccesoOperacion.INGRESO, evaluacion.motivo(),
                            evaluacion.mensaje(), operador);
                }
                reserva.setEstado(ReservaEstado.EN_CURSO);
                reserva.setIngresoAt(ahora);
                reserva.registrarAccion(ReservaAccion.INGRESO, operador, true, reglasAcceso.instante());
                guardar(reserva, AccesoOperacion.INGRESO, operador);
                registroIntentos.registrar(AccesoOperacion.INGRESO, reserva.getCodigo(), reserva.getId(), OK,
                        operador.getEmail());
                return new AccesoRegistroResponseDto(AccesoAccion.INGRESO, false, ahora, false, null,
                        "Ingreso registrado. Cochera %s.".formatted(reserva.getCochera().getNumero()),
                        toReservaDto(reserva, ahora));
            }
            default -> throw rechazarTerminal(reserva, AccesoOperacion.INGRESO, operador, ahora);
        }
    }

    private AccesoRegistroResponseDto registrarEgreso(Reserva reserva, Visitante operador, LocalDateTime ahora) {
        switch (reserva.getEstado()) {
            case FINALIZADA -> {
                if (reglasAcceso.recienRegistrado(reserva.getEgresoAt(), ahora)) {
                    return yaRegistrado(reserva, AccesoAccion.EGRESO, reserva.getEgresoAt(), operador, ahora);
                }
                throw rechazarTerminal(reserva, AccesoOperacion.EGRESO, operador, ahora);
            }
            case EN_CURSO -> {
                if (reglasAcceso.egresoPrematuro(reserva, ahora)) {
                    throw rechazar(reserva, AccesoOperacion.EGRESO, AccesoMotivo.PERMANENCIA_MINIMA,
                            mensajePermanencia(reserva, ahora), operador);
                }
                Integer exceso = reglasAcceso.minutosExceso(reserva, ahora);
                reserva.setEstado(ReservaEstado.FINALIZADA);
                reserva.setEgresoAt(ahora);
                reserva.setMinutosExceso(exceso);
                reserva.registrarAccion(ReservaAccion.EGRESO, operador, true, reglasAcceso.instante());
                guardar(reserva, AccesoOperacion.EGRESO, operador);
                planificacion.promover();
                registroIntentos.registrar(AccesoOperacion.EGRESO, reserva.getCodigo(), reserva.getId(), OK,
                        operador.getEmail());
                String mensaje = exceso == null
                        ? "Egreso registrado. La cochera %s quedó libre.".formatted(reserva.getCochera().getNumero())
                        : "Egreso registrado con exceso: se pasó %d minutos de su horario.".formatted(exceso);
                return new AccesoRegistroResponseDto(AccesoAccion.EGRESO, false, ahora, exceso != null, exceso,
                        mensaje, toReservaDto(reserva, ahora));
            }
            case CONFIRMADA -> throw rechazar(reserva, AccesoOperacion.EGRESO, AccesoMotivo.SIN_INGRESO,
                    "No se puede registrar el egreso: el vehículo no tiene un ingreso registrado.", operador);
            default -> throw rechazarTerminal(reserva, AccesoOperacion.EGRESO, operador, ahora);
        }
    }

    private AccesoRegistroResponseDto yaRegistrado(Reserva reserva, AccesoAccion accion, LocalDateTime cuando,
                                                   Visitante operador, LocalDateTime ahora) {
        AccesoOperacion operacion = accion == AccesoAccion.INGRESO ? AccesoOperacion.INGRESO : AccesoOperacion.EGRESO;
        registroIntentos.registrar(operacion, reserva.getCodigo(), reserva.getId(), YA_REGISTRADO, operador.getEmail());
        Integer exceso = accion == AccesoAccion.EGRESO ? reserva.getMinutosExceso() : null;
        return new AccesoRegistroResponseDto(accion, true, cuando, exceso != null, exceso,
                "El %s ya estaba registrado a las %s; no se registró de nuevo."
                        .formatted(accion == AccesoAccion.INGRESO ? "ingreso" : "egreso", fecha(cuando, ahora)),
                toReservaDto(reserva, ahora));
    }

    /** Lo que corresponde segun el estado, sin tocar nada. */
    private Evaluacion evaluar(Reserva reserva, LocalDateTime ahora) {
        return switch (reserva.getEstado()) {
            case CONFIRMADA -> evaluarIngreso(reserva, ahora);
            case EN_CURSO -> {
                if (reglasAcceso.egresoPrematuro(reserva, ahora)) {
                    yield new Evaluacion(AccesoAccion.EGRESO, AccesoMotivo.PERMANENCIA_MINIMA,
                            mensajePermanencia(reserva, ahora));
                }
                Integer exceso = reglasAcceso.minutosExceso(reserva, ahora);
                yield new Evaluacion(AccesoAccion.EGRESO, null, exceso == null
                        ? "Puede salir."
                        : "Puede salir. Se pasó %d minutos de su horario.".formatted(exceso));
            }
            case FINALIZADA -> new Evaluacion(null, AccesoMotivo.YA_UTILIZADA, mensajeYaUtilizada(reserva, ahora));
            case VENCIDA -> new Evaluacion(null, AccesoMotivo.VENCIDA, mensajeVencida(reserva, ahora));
            case CANCELADA -> new Evaluacion(null, AccesoMotivo.CANCELADA,
                    "La reserva fue cancelada: el código ya no es válido.");
            case LISTA_ESPERA -> new Evaluacion(null, AccesoMotivo.SIN_INGRESO,
                    "La reserva sigue en lista de espera y todavia no tiene acceso habilitado.");
        };
    }

    private Evaluacion evaluarIngreso(Reserva reserva, LocalDateTime ahora) {
        if (reglasAcceso.ingresoAnticipado(reserva, ahora)) {
            return new Evaluacion(AccesoAccion.INGRESO, AccesoMotivo.ANTICIPADO, mensajeAnticipado(reserva, ahora));
        }
        // Se verifica la ocupacion real, tambien fuera de la franja reservada:
        // la tolerancia anticipada y un egreso tardio pueden hacerla solapar.
        if (reservaRepository.existsByCocheraIdAndEstadoAndIdNot(
                reserva.getCochera().getId(), ReservaEstado.EN_CURSO, reserva.getId())) {
            return new Evaluacion(AccesoAccion.INGRESO, AccesoMotivo.COCHERA_OCUPADA,
                    "La cochera todavía está ocupada. Registrá el egreso anterior antes de permitir el ingreso.");
        }
        if (reservaRepository.existsByVehiculoIdAndEstadoAndIdNot(
                reserva.getVehiculo().getId(), ReservaEstado.EN_CURSO, reserva.getId())) {
            return new Evaluacion(AccesoAccion.INGRESO, AccesoMotivo.VEHICULO_EN_CURSO,
                    "El vehículo ya tiene un ingreso con otra reserva. Registrá su egreso antes de volver a ingresar.");
        }
        return new Evaluacion(AccesoAccion.INGRESO, null, "Puede ingresar.");
    }

    /** Rechazo por un estado que ya no admite ninguna accion. */
    private ValidationException rechazarTerminal(Reserva reserva, AccesoOperacion operacion, Visitante operador,
                                                 LocalDateTime ahora) {
        Evaluacion evaluacion = evaluar(reserva, ahora);
        return rechazar(reserva, operacion, evaluacion.motivo(), evaluacion.mensaje(), operador);
    }

    private ValidationException rechazar(Reserva reserva, AccesoOperacion operacion, AccesoMotivo motivo,
                                         String mensaje, Visitante operador) {
        registroIntentos.registrar(operacion, reserva.getCodigo(), reserva.getId(), motivo.name(), operador.getEmail());
        return new ValidationException(mensaje, List.of(motivo.name()));
    }

    /**
     * El codigo puede venir con el prefijo del QR, guiones o espacios. Un
     * formato invalido o un codigo inexistente tambien quedan registrados.
     */
    private Reserva buscar(String entrada, AccesoOperacion operacion, String operadorEmail) {
        String codigo;
        try {
            codigo = CodigoAcceso.normalizar(entrada);
        } catch (ValidationException e) {
            registroIntentos.registrar(operacion, entrada, null, FORMATO_INVALIDO, operadorEmail);
            throw e;
        }
        return reservaRepository.findByCodigo(codigo).orElseThrow(() -> {
            registroIntentos.registrar(operacion, codigo, null, INEXISTENTE, operadorEmail);
            return new NotFoundException(
                    "No existe ninguna reserva con el código %s.".formatted(CodigoAcceso.formatear(codigo)));
        });
    }

    private void asentarSiVencio(Reserva reserva, LocalDateTime ahora) {
        if (reglasAcceso.marcarSiVencio(reserva, ahora)) {
            reservaRepository.save(reserva);
            planificacion.promover();
        }
    }

    /**
     * El flush adentro del metodo hace saltar aca el conflicto de version (dos
     * operadores con el mismo codigo a la vez), para poder registrarlo antes de
     * que el handler lo convierta en 409.
     */
    private void guardar(Reserva reserva, AccesoOperacion operacion, Visitante operador) {
        try {
            reservaRepository.saveAndFlush(reserva);
        } catch (OptimisticLockingFailureException e) {
            registroIntentos.registrar(operacion, reserva.getCodigo(), reserva.getId(), CONFLICTO, operador.getEmail());
            throw e;
        }
    }

    private String mensajeAnticipado(Reserva reserva, LocalDateTime ahora) {
        return "Todavía no puede ingresar: la ventana de ingreso abre a las %s."
                .formatted(fecha(reglasAcceso.inicioIngreso(reserva), ahora));
    }

    private String mensajePermanencia(Reserva reserva, LocalDateTime ahora) {
        return ("El ingreso se registró hace menos de %d segundos. Si el vehículo realmente sale, "
                + "esperá %d segundos y volvé a intentar.")
                .formatted(reglasAcceso.permanenciaMinimaSeg(), reglasAcceso.segundosParaEgreso(reserva, ahora));
    }

    private String mensajeVencida(Reserva reserva, LocalDateTime ahora) {
        return "La reserva venció: no se registró el ingreso a tiempo (la ventana cerró a las %s)."
                .formatted(fecha(reglasAcceso.finIngreso(reserva), ahora));
    }

    private String mensajeYaUtilizada(Reserva reserva, LocalDateTime ahora) {
        if (reserva.getIngresoAt() == null || reserva.getEgresoAt() == null) {
            return "La reserva ya finalizó: el código ya no es válido.";
        }
        return "El código ya se usó: el vehículo ingresó a las %s y salió a las %s."
                .formatted(fecha(reserva.getIngresoAt(), ahora), fecha(reserva.getEgresoAt(), ahora));
    }

    /** Solo la hora si es de hoy; con el dia si no, para que no se lea mal. */
    private static String fecha(LocalDateTime momento, LocalDateTime ahora) {
        return momento.toLocalDate().equals(ahora.toLocalDate()) ? momento.format(HORA) : momento.format(DIA_Y_HORA);
    }

    private AccesoReservaDto toReservaDto(Reserva reserva, LocalDateTime ahora) {
        Integer exceso = switch (reserva.getEstado()) {
            case FINALIZADA -> reserva.getMinutosExceso();
            case EN_CURSO -> reglasAcceso.minutosExceso(reserva, ahora);
            default -> null;
        };
        return new AccesoReservaDto(
                reserva.getId(),
                reserva.getEstado(),
                reserva.getDesde(),
                reserva.getHasta(),
                reserva.getVisitante().getNombre(),
                reserva.getVehiculo().getPatente(),
                reserva.getVehiculo().getTipo(),
                reserva.getCochera().getNumero(),
                reserva.getCochera().getSector(),
                reserva.getCochera().getTipo(),
                reglasAcceso.inicioIngreso(reserva),
                reglasAcceso.finIngreso(reserva),
                reserva.getIngresoAt(),
                reserva.getEgresoAt(),
                exceso);
    }

    private AccesoMovimientoResponseDto toMovimientoDto(ReservaMovimiento movimiento) {
        Reserva reserva = movimiento.getReserva();
        boolean egreso = movimiento.getAccion() == ReservaAccion.EGRESO;
        return new AccesoMovimientoResponseDto(
                movimiento.getId(),
                egreso ? AccesoAccion.EGRESO : AccesoAccion.INGRESO,
                movimiento.getFecha(),
                reserva.getId(),
                reserva.getVisitante().getNombre(),
                reserva.getVehiculo().getPatente(),
                reserva.getVehiculo().getTipo(),
                reserva.getCochera().getNumero(),
                reserva.getCochera().getSector(),
                movimiento.getActorNombre(),
                egreso ? reserva.getMinutosExceso() : null);
    }
}
