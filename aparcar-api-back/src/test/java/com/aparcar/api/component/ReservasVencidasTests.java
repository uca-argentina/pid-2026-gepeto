package com.aparcar.api.component;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.impl.ReglasAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del barrido que pasa a VENCIDA (no-show) las reservas a las que
 * se les cerro la ventana de ingreso. Reloj fijo: 10:00 del 10/10/2026.
 *
 * <p>Ojo con lo que esta tarea NO hace: no es lo unico que detecta el no-show.
 * La validacion del codigo hace la misma evaluacion al momento
 * ({@code AccesoServiceTests}), asi que nada depende de que el barrido corra.
 */
@UnitTests
class ReservasVencidasTests {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 10, 10, 0);

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private com.aparcar.api.service.impl.PlanificacionReservas planificacion;

    private ReservasVencidas reservasVencidas;

    @BeforeEach
    void setUp() {
        ReglasAcceso reglas = AccesoDePrueba.reglas(AccesoDePrueba.relojEn(AHORA));
        reservasVencidas = new ReservasVencidas(reservaRepository, reglas, planificacion);
    }

    private static Reserva confirmada(LocalDateTime desde, LocalDateTime hasta) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reserva;
    }

    private List<Reserva> guardadas() {
        ArgumentCaptor<Iterable<Reserva>> captor = ArgumentCaptor.captor();
        verify(reservaRepository).saveAll(captor.capture());
        List<Reserva> guardadas = new ArrayList<>();
        captor.getValue().forEach(guardadas::add);
        return guardadas;
    }

    // Antes pasaban a FINALIZADA. Ahora FINALIZADA quiere decir "egreso", y
    // una reserva que nunca tuvo ingreso es un no-show.
    @Test
    @DisplayName("pasa a VENCIDA (no FINALIZADA) una reserva cuya franja termino sin ingreso")
    void marcaVencidaLaQueTerminoSinIngreso() {
        Reserva terminada = confirmada(AHORA.minusHours(3), AHORA.minusHours(1));
        when(reservaRepository.findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of(terminada));

        reservasVencidas.marcarVencidas();

        assertEquals(ReservaEstado.VENCIDA, guardadas().getFirst().getEstado());
    }

    @Test
    @DisplayName("pasa a VENCIDA una reserva en curso de horario si ya paso la tolerancia de ingreso")
    void marcaVencidaLaQuePasoLaTolerancia() {
        // Empezo a las 9:29: la tolerancia cerro a las 9:59.
        Reserva sinIngreso = confirmada(AHORA.minusMinutes(31), AHORA.plusHours(2));
        when(reservaRepository.findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of(sinIngreso));

        reservasVencidas.marcarVencidas();

        assertEquals(ReservaEstado.VENCIDA, guardadas().getFirst().getEstado());
    }

    @Test
    @DisplayName("no toca una reserva que todavia esta dentro de la tolerancia")
    void respetaLaTolerancia() {
        // Empezo hace exactamente 30 minutos: es el ultimo instante valido.
        Reserva enTolerancia = confirmada(AHORA.minusMinutes(30), AHORA.plusHours(2));
        when(reservaRepository.findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of(enTolerancia));

        reservasVencidas.marcarVencidas();

        assertEquals(ReservaEstado.CONFIRMADA, enTolerancia.getEstado());
        verify(reservaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("solo considera las CONFIRMADAS: una EN_CURSO no vence nunca por tiempo")
    void soloConsideraLasConfirmadas() {
        when(reservaRepository.findByEstadoAndDesdeLessThan(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of());

        reservasVencidas.marcarVencidas();

        verify(reservaRepository).findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, AHORA);
    }

    @Test
    @DisplayName("no escribe en la base cuando no hay nada vencido")
    void noEscribeSiNoHayVencidas() {
        when(reservaRepository.findByEstadoAndDesdeLessThan(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of());

        reservasVencidas.marcarVencidas();

        verify(reservaRepository, never()).saveAll(any());
        verify(planificacion).promover();
    }

    @Test
    @DisplayName("marca todas las vencidas de una, no solo la primera, y deja las vigentes")
    void marcaTodasLasVencidas() {
        Reserva vigente = confirmada(AHORA.minusMinutes(10), AHORA.plusHours(1));
        when(reservaRepository.findByEstadoAndDesdeLessThan(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of(
                        confirmada(AHORA.minusHours(3), AHORA.minusHours(1)),
                        vigente,
                        confirmada(AHORA.minusHours(5), AHORA.minusHours(4)),
                        confirmada(AHORA.minusHours(50), AHORA.minusHours(49))));

        reservasVencidas.marcarVencidas();

        List<Reserva> guardadas = guardadas();
        assertEquals(3, guardadas.size());
        assertTrue(guardadas.stream().allMatch(r -> r.getEstado() == ReservaEstado.VENCIDA));
        assertEquals(ReservaEstado.CONFIRMADA, vigente.getEstado());

        var orden = inOrder(planificacion, reservaRepository);
        orden.verify(planificacion).bloquear();
        orden.verify(reservaRepository).saveAll(any());
        orden.verify(planificacion).promover();
    }
}
