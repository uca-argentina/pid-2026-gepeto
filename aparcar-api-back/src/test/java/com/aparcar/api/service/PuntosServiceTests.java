package com.aparcar.api.service;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.PuntosProperties;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.puntos.MovimientoPuntos;
import com.aparcar.api.entity.puntos.TipoMovimientoPuntos;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.BilleteraPuntosRepository;
import com.aparcar.api.repository.MovimientoPuntosRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.CreadorBilleteras;
import com.aparcar.api.service.impl.PuntosService;
import com.aparcar.api.service.impl.ReglasPuntos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Las reglas de puntos sobre una billetera en memoria, con el reloj fijo. */
@UnitTests
class PuntosServiceTests {
    /** Lunes 2/11/2026, 23:10 en el predio. */
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 2, 23, 10);
    private static final LocalDate HOY = AHORA.toLocalDate();

    @Mock BilleteraPuntosRepository billeteras;
    @Mock MovimientoPuntosRepository movimientos;
    @Mock VisitanteRepository visitantes;
    @Mock CreadorBilleteras creador;

    private PuntosService service;
    private Visitante visitante;
    private BilleteraPuntos billetera;

    @BeforeEach
    void setUp() {
        service = new PuntosService(billeteras, movimientos, visitantes, creador,
                new ReglasPuntos(new PuntosProperties(60, 2, 7, 10, 50)), AccesoDePrueba.relojEn(AHORA));
        visitante = new Visitante("Ana", "1", "ana@test.com", "hash", null, Set.of(AppAuthority.USER), true);
        visitante.setId(UUID.randomUUID());
        billetera = new BilleteraPuntos(visitante.getId());
    }

    private void conBilleteraBloqueable() {
        when(billeteras.findParaActualizar(visitante.getId())).thenReturn(Optional.of(billetera));
    }

    private Reserva reserva(LocalDateTime desde, LocalDateTime hasta) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setVisitante(visitante);
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reserva;
    }

    @SuppressWarnings("unchecked")
    private List<MovimientoPuntos> movimientosGuardados() {
        ArgumentCaptor<List<MovimientoPuntos>> captor = ArgumentCaptor.forClass(List.class);
        verify(movimientos).saveAllAndFlush(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("acreditacion al ingresar")
    class Acreditacion {
        @Test
        @DisplayName("Una reserva nocturna suma 20 y arranca la racha")
        void nocturna() {
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0));

            service.acreditarIngreso(reserva, AHORA);

            assertEquals(20, billetera.getSaldo());
            assertEquals(1, billetera.getRachaActual());
            assertEquals(20, reserva.getPuntosAcreditados());
            assertNull(reserva.getPuntosMotivo());
            List<MovimientoPuntos> guardados = movimientosGuardados();
            assertEquals(1, guardados.size());
            assertEquals(TipoMovimientoPuntos.ACREDITACION_FRANJA, guardados.getFirst().getTipo());
            assertEquals(20, guardados.getFirst().getPuntos());
            assertEquals(reserva.getId(), guardados.getFirst().getReservaId());
            verify(creador).crearSiFalta(visitante.getId());
        }

        @Test
        @DisplayName("Una reserva ya acreditada no vuelve a sumar")
        void yaAcreditada() {
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0));
            reserva.setPuntosAcreditados(20);

            service.acreditarIngreso(reserva, AHORA);

            verifyNoInteractions(billeteras, movimientos, creador);
        }

        @Test
        @DisplayName("Si ya existe el movimiento de esa reserva (otro intento en paralelo), no suma de nuevo")
        void movimientoExistente() {
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0));
            when(movimientos.existsByVisitanteIdAndReservaIdAndTipo(visitante.getId(), reserva.getId(),
                    TipoMovimientoPuntos.ACREDITACION_FRANJA)).thenReturn(true);

            service.acreditarIngreso(reserva, AHORA);

            assertEquals(0, billetera.getSaldo());
            verify(movimientos, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("Una reserva mas corta que el minimo no suma, pero cuenta para la racha")
        void corta() {
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.atTime(23, 45));

            service.acreditarIngreso(reserva, AHORA);

            assertEquals(0, billetera.getSaldo());
            assertEquals(0, reserva.getPuntosAcreditados());
            assertEquals("La reserva dura menos de 60 minutos: no suma puntos.", reserva.getPuntosMotivo());
            assertEquals(1, billetera.getRachaActual());
            assertTrue(movimientosGuardados().isEmpty());
        }

        @Test
        @DisplayName("Pasado el tope diario no suma, pero la reserva se usa igual")
        void topeDiario() {
            conBilleteraBloqueable();
            when(movimientos.countByVisitanteIdAndTipoAndFechaGreaterThanEqualAndFechaLessThan(eq(visitante.getId()),
                    eq(TipoMovimientoPuntos.ACREDITACION_FRANJA), any(), any())).thenReturn(2L);
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0));

            service.acreditarIngreso(reserva, AHORA);

            assertEquals(0, billetera.getSaldo());
            assertEquals(0, reserva.getPuntosAcreditados());
            assertEquals("Ya sumaste puntos por 2 reservas ese día: esta no suma.", reserva.getPuntosMotivo());
        }

        @Test
        @DisplayName("El septimo dia seguido suma el bonus como movimiento aparte")
        void bonusDeRacha() {
            billetera.setRachaActual(6);
            billetera.setMejorRacha(6);
            billetera.setUltimoDiaRacha(HOY.minusDays(1));
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0));

            service.acreditarIngreso(reserva, AHORA);

            assertEquals(30, billetera.getSaldo());
            assertEquals(7, billetera.getRachaActual());
            assertEquals(7, billetera.getMejorRacha());
            List<MovimientoPuntos> guardados = movimientosGuardados();
            assertEquals(List.of(TipoMovimientoPuntos.ACREDITACION_FRANJA, TipoMovimientoPuntos.BONUS_RACHA),
                    guardados.stream().map(MovimientoPuntos::getTipo).toList());
            assertEquals(10, guardados.get(1).getPuntos());
        }

        @Test
        @DisplayName("Un segundo ingreso el mismo dia del bonus no lo repite")
        void bonusUnaVezPorDia() {
            billetera.setRachaActual(7);
            billetera.setMejorRacha(7);
            billetera.setUltimoDiaRacha(HOY);
            conBilleteraBloqueable();

            service.acreditarIngreso(reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0)), AHORA);

            assertEquals(20, billetera.getSaldo());
            assertEquals(7, billetera.getRachaActual());
            assertEquals(1, movimientosGuardados().size());
        }

        @Test
        @DisplayName("Un choque de version se traduce a 409")
        void conflicto() {
            conBilleteraBloqueable();
            when(billeteras.saveAndFlush(billetera)).thenThrow(new OptimisticLockingFailureException("choque"));

            assertThrows(ConflictoException.class, () ->
                    service.acreditarIngreso(reserva(HOY.atTime(23, 0), HOY.plusDays(1).atTime(1, 0)), AHORA));
        }
    }

    @Nested
    @DisplayName("canje")
    class Canje {
        private static final BigDecimal SUBTOTAL = new BigDecimal("1000.00");

        @Test
        @DisplayName("100 puntos son 10 % de descuento")
        void cienPuntos() {
            billetera.setSaldo(300);
            conBilleteraBloqueable();

            var descuento = service.prepararCanje(visitante, SUBTOTAL, new BigDecimal("100"), false);

            assertEquals(100, descuento.puntos());
            assertEquals(new BigDecimal("10.00"), descuento.porcentaje());
            assertEquals(new BigDecimal("100.00"), descuento.monto());
            assertEquals(new BigDecimal("900.00"), descuento.precioFinal());
        }

        @Test
        @DisplayName("Usar todos con mucho saldo usa solo lo que llega al tope")
        void todosConTope() {
            billetera.setSaldo(800);
            conBilleteraBloqueable();

            var descuento = service.prepararCanje(visitante, SUBTOTAL, null, true);

            assertEquals(500, descuento.puntos());
            assertEquals(new BigDecimal("500.00"), descuento.precioFinal());
        }

        @Test
        @DisplayName("Usar todos con poco saldo usa el saldo")
        void todosConSaldo() {
            billetera.setSaldo(35);
            conBilleteraBloqueable();

            var descuento = service.prepararCanje(visitante, SUBTOTAL, null, true);

            assertEquals(35, descuento.puntos());
            assertEquals(new BigDecimal("3.50"), descuento.porcentaje());
        }

        @Test
        @DisplayName("Usar todos sin saldo es un 400")
        void todosSinSaldo() {
            when(billeteras.findParaActualizar(visitante.getId())).thenReturn(Optional.empty());

            var e = assertThrows(ValidationException.class,
                    () -> service.prepararCanje(visitante, SUBTOTAL, null, true));
            assertEquals("No tenés puntos para usar.", e.getMessage());
        }

        @ParameterizedTest(name = "\"{0}\" se rechaza")
        @ValueSource(strings = {"0", "-5", "1.5", "0.01"})
        @DisplayName("Cero, negativos y decimales se rechazan")
        void invalidos(String puntos) {
            billetera.setSaldo(300);
            conBilleteraBloqueable();

            assertThrows(ValidationException.class,
                    () -> service.prepararCanje(visitante, SUBTOTAL, new BigDecimal(puntos), false));
        }

        @Test
        @DisplayName("Mas puntos que el saldo es un 400")
        void saldoInsuficiente() {
            billetera.setSaldo(50);
            conBilleteraBloqueable();

            var e = assertThrows(ValidationException.class,
                    () -> service.prepararCanje(visitante, SUBTOTAL, new BigDecimal("100"), false));
            assertEquals("No tenés puntos suficientes: tu saldo es de 50.", e.getMessage());
        }

        @Test
        @DisplayName("Pasar el tope de descuento es un 400, aunque alcance el saldo")
        void pasaElTope() {
            billetera.setSaldo(800);
            conBilleteraBloqueable();

            var e = assertThrows(ValidationException.class,
                    () -> service.prepararCanje(visitante, SUBTOTAL, new BigDecimal("510"), false));
            assertTrue(e.getMessage().contains("hasta 500 puntos"));
        }

        @Test
        @DisplayName("Confirmar descuenta el saldo y deja un movimiento negativo")
        void confirmar() {
            billetera.setSaldo(300);
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.plusDays(1).atTime(10, 0), HOY.plusDays(1).atTime(12, 0));

            service.confirmarCanje(visitante, reserva, new ReglasPuntos.Descuento(100, new BigDecimal("10.00"),
                    new BigDecimal("100.00"), new BigDecimal("900.00")));

            assertEquals(200, billetera.getSaldo());
            MovimientoPuntos canje = movimientosGuardados().getFirst();
            assertEquals(TipoMovimientoPuntos.CANJE, canje.getTipo());
            assertEquals(-100, canje.getPuntos());
            assertEquals("Descuento del 10 % en la reserva del 03/11 10:00", canje.getDescripcion());
        }

        @Test
        @DisplayName("Si el saldo bajo entre la validacion y la confirmacion, es un 409 y no queda negativo")
        void confirmarSinSaldo() {
            billetera.setSaldo(50);
            conBilleteraBloqueable();

            assertThrows(ConflictoException.class, () -> service.confirmarCanje(visitante,
                    reserva(HOY.plusDays(1).atTime(10, 0), HOY.plusDays(1).atTime(12, 0)),
                    new ReglasPuntos.Descuento(100, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)));
            assertEquals(50, billetera.getSaldo());
        }
    }

    @Nested
    @DisplayName("reintegro")
    class Reintegro {
        @Test
        @DisplayName("Cancelar devuelve exactamente los puntos usados, una sola vez")
        void devuelve() {
            billetera.setSaldo(200);
            conBilleteraBloqueable();
            Reserva reserva = reserva(HOY.plusDays(1).atTime(10, 0), HOY.plusDays(1).atTime(12, 0));
            reserva.setPuntosUsados(100);

            service.reintegrar(reserva);

            assertEquals(300, billetera.getSaldo());
            assertNotNull(reserva.getPuntosReintegradosAt());
            MovimientoPuntos reintegro = movimientosGuardados().getFirst();
            assertEquals(TipoMovimientoPuntos.REINTEGRO, reintegro.getTipo());
            assertEquals(100, reintegro.getPuntos());

            service.reintegrar(reserva);
            assertEquals(300, billetera.getSaldo());
        }

        @Test
        @DisplayName("Sin puntos usados no hay nada que devolver")
        void sinPuntos() {
            service.reintegrar(reserva(HOY.plusDays(1).atTime(10, 0), HOY.plusDays(1).atTime(12, 0)));

            verifyNoInteractions(billeteras, movimientos, creador);
        }
    }

    @Nested
    @DisplayName("consultas")
    class Consultas {
        @Test
        @DisplayName("Sin billetera se muestra en cero, sin crearla")
        void billeteraVacia() {
            when(visitantes.findByEmail("ana@test.com")).thenReturn(Optional.of(visitante));
            when(billeteras.findByVisitanteId(visitante.getId())).thenReturn(Optional.empty());

            var dto = service.billetera("ana@test.com");

            assertEquals(0, dto.saldo());
            assertEquals(0, dto.rachaActual());
            assertEquals(7, dto.diasParaBonus());
            assertEquals(4, dto.franjas().size());
            assertEquals(500, dto.maxPuntosCanjeables());
            verifyNoInteractions(creador);
        }

        @Test
        @DisplayName("Una racha cortada se muestra en 0 y conserva la mejor")
        void rachaCortada() {
            billetera.setRachaActual(5);
            billetera.setMejorRacha(9);
            billetera.setUltimoDiaRacha(HOY.minusDays(3));
            when(visitantes.findByEmail("ana@test.com")).thenReturn(Optional.of(visitante));
            when(billeteras.findByVisitanteId(visitante.getId())).thenReturn(Optional.of(billetera));

            var dto = service.billetera("ana@test.com");

            assertEquals(0, dto.rachaActual());
            assertEquals(9, dto.mejorRacha());
        }

        @Test
        @DisplayName("La cotizacion sin puntos no cambia el precio y dice cuanto sumaria")
        void cotizacionSinPuntos() {
            billetera.setSaldo(120);
            when(visitantes.findByEmail("ana@test.com")).thenReturn(Optional.of(visitante));
            when(billeteras.findByVisitanteId(visitante.getId())).thenReturn(Optional.of(billetera));
            var base = new CotizacionResponseDto(CocheraTipo.AUTO, new BigDecimal("1000.00"), "ARS", 0, 0, 2, 0);

            var cotizacion = service.cotizar(base, "ana@test.com", HOY.plusDays(1).atTime(22, 0),
                    HOY.plusDays(2).atTime(0, 0), null, false);

            assertEquals(new BigDecimal("1000.00"), cotizacion.total());
            assertEquals(new BigDecimal("1000.00"), cotizacion.subtotal());
            assertEquals(0, cotizacion.puntosAplicados());
            assertEquals(120, cotizacion.saldoDisponible());
            assertEquals(120, cotizacion.maxPuntosCanjeables());
            assertEquals(20, cotizacion.puntosAGanar());
        }

        @Test
        @DisplayName("La cotizacion con puntos devuelve el total con descuento")
        void cotizacionConPuntos() {
            billetera.setSaldo(120);
            when(visitantes.findByEmail("ana@test.com")).thenReturn(Optional.of(visitante));
            when(billeteras.findByVisitanteId(visitante.getId())).thenReturn(Optional.of(billetera));
            var base = new CotizacionResponseDto(CocheraTipo.AUTO, new BigDecimal("1000.00"), "ARS", 0, 0, 2, 0);

            var cotizacion = service.cotizar(base, "ana@test.com", HOY.plusDays(1).atTime(8, 0),
                    HOY.plusDays(1).atTime(10, 0), new BigDecimal("100"), false);

            assertEquals(new BigDecimal("900.00"), cotizacion.total());
            assertEquals(new BigDecimal("1000.00"), cotizacion.subtotal());
            assertEquals(new BigDecimal("100.00"), cotizacion.descuento());
            assertEquals(100, cotizacion.puntosAplicados());
            assertEquals(10, cotizacion.puntosAGanar());
        }

        @Test
        @DisplayName("Sin cuenta, la cotizacion queda como estaba")
        void cotizacionSinCuenta() {
            var base = new CotizacionResponseDto(CocheraTipo.AUTO, new BigDecimal("1000.00"), "ARS", 0, 0, 2, 0);

            assertSame(base, service.cotizar(base, null, AHORA, AHORA.plusHours(1), null, false));
        }

        @Test
        @DisplayName("Una reserva pendiente muestra lo que sumaria; una cancelada, que no suma")
        void deReserva() {
            Reserva pendiente = reserva(HOY.plusDays(1).atTime(10, 0), HOY.plusDays(1).atTime(12, 0));
            assertEquals(15, service.deReserva(pendiente).puntosAGanar());
            assertNull(service.deReserva(pendiente).motivoSinPuntos());

            pendiente.setEstado(ReservaEstado.CANCELADA);
            pendiente.setPuntosUsados(100);
            pendiente.setPuntosReintegradosAt(AHORA.atZone(AccesoDePrueba.ZONA).toInstant());
            var dto = service.deReserva(pendiente);
            assertEquals(0, dto.puntosAGanar());
            assertEquals(100, dto.puntosUsados());
            assertTrue(dto.reintegrados());
            assertEquals("La reserva no se usó: no suma puntos.", dto.motivoSinPuntos());
        }

        @Test
        @DisplayName("Una reserva vencida con puntos usados no figura reintegrada")
        void vencidaSinReintegro() {
            Reserva vencida = reserva(HOY.minusDays(1).atTime(10, 0), HOY.minusDays(1).atTime(12, 0));
            vencida.setEstado(ReservaEstado.VENCIDA);
            vencida.setPuntosUsados(100);

            var dto = service.deReserva(vencida);

            assertFalse(dto.reintegrados());
            assertEquals(0, dto.puntosAGanar());
        }
    }
}
