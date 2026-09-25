package com.aparcar.api.component;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca de la tarea que marca como FINALIZADA las reservas vencidas.
 *
 * <p>Ojo con lo que esta tarea NO hace: no es lo que libera la cochera. Eso lo
 * resuelve el solapamiento de rangos ({@code ReservaSolapamientoTests}), que
 * funciona aunque la tarea nunca corra. Esto solo mantiene el estado legible.
 */
@UnitTests
class ReservasVencidasTests {

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private ReservasVencidas reservasVencidas;

    private static Reserva reservaQueTerminoHace(long horas) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setDesde(LocalDateTime.now().minusHours(horas + 2));
        reserva.setHasta(LocalDateTime.now().minusHours(horas));
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reserva;
    }

    @Test
    @DisplayName("pasa a FINALIZADA las reservas confirmadas cuya franja ya termino")
    void marcaLasVencidasComoFinalizadas() {
        Reserva vencida = reservaQueTerminoHace(1);
        when(reservaRepository.findByEstadoAndHastaLessThanEqual(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of(vencida));

        reservasVencidas.finalizarVencidas();

        ArgumentCaptor<Iterable<Reserva>> captor = ArgumentCaptor.captor();
        verify(reservaRepository).saveAll(captor.capture());
        assertEquals(ReservaEstado.FINALIZADA, captor.getValue().iterator().next().getEstado());
    }

    // Solo mira las CONFIRMADAS: una cancelada no tiene por que pasar a
    // finalizada, se dio de baja antes de tiempo y eso hay que poder verlo.
    @Test
    @DisplayName("solo considera las reservas CONFIRMADAS")
    void soloConsideraLasConfirmadas() {
        when(reservaRepository.findByEstadoAndHastaLessThanEqual(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of());

        reservasVencidas.finalizarVencidas();

        verify(reservaRepository).findByEstadoAndHastaLessThanEqual(eq(ReservaEstado.CONFIRMADA), any());
    }

    @Test
    @DisplayName("no escribe en la base cuando no hay nada vencido")
    void noEscribeSiNoHayVencidas() {
        when(reservaRepository.findByEstadoAndHastaLessThanEqual(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of());

        reservasVencidas.finalizarVencidas();

        verify(reservaRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("marca todas las vencidas de una, no solo la primera")
    void marcaTodasLasVencidas() {
        when(reservaRepository.findByEstadoAndHastaLessThanEqual(eq(ReservaEstado.CONFIRMADA), any()))
                .thenReturn(List.of(reservaQueTerminoHace(1), reservaQueTerminoHace(5), reservaQueTerminoHace(50)));

        reservasVencidas.finalizarVencidas();

        ArgumentCaptor<Iterable<Reserva>> captor = ArgumentCaptor.captor();
        verify(reservaRepository).saveAll(captor.capture());
        List<Reserva> guardadas = new ArrayList<>();
        captor.getValue().forEach(guardadas::add);
        assertEquals(3, guardadas.size());
        assertTrue(guardadas.stream().allMatch(r -> r.getEstado() == ReservaEstado.FINALIZADA));
    }
}
