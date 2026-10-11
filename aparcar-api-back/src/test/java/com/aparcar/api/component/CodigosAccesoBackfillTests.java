package com.aparcar.api.component;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.impl.CodigoAcceso;
import com.aparcar.api.service.impl.ReglasAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del backfill que les da codigo a las reservas anteriores a la
 * feature. Reloj fijo: 10:00 del 10/10/2026.
 */
@UnitTests
class CodigosAccesoBackfillTests {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 10, 10, 0);

    @Mock
    private ReservaRepository reservaRepository;

    private CodigosAccesoBackfill backfill;
    private ReglasAcceso reglas;

    @BeforeEach
    void setUp() {
        reglas = AccesoDePrueba.reglas(AccesoDePrueba.relojEn(AHORA));
        CodigoAcceso codigoAcceso = new CodigoAcceso(AccesoDePrueba.propiedades(), reservaRepository);
        backfill = new CodigosAccesoBackfill(reservaRepository, codigoAcceso, reglas);
    }

    private static Reserva vigente(LocalDateTime desde, LocalDateTime hasta) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reserva;
    }

    @Test
    @DisplayName("asigna el codigo derivado del id a cada CONFIRMADA vigente sin codigo")
    void asignaCodigos() {
        Reserva primera = vigente(AHORA.plusHours(1), AHORA.plusHours(2));
        Reserva segunda = vigente(AHORA.plusDays(1), AHORA.plusDays(1).plusHours(3));
        when(reservaRepository.findByEstadoAndCodigoIsNullAndHastaAfter(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of(primera, segunda));

        assertEquals(2, backfill.asignarFaltantes());

        byte[] secreto = AccesoDePrueba.SECRETO.getBytes(StandardCharsets.UTF_8);
        assertEquals(CodigoAcceso.derivar(secreto, primera.getId(), 0), primera.getCodigo());
        assertEquals(CodigoAcceso.derivar(secreto, segunda.getId(), 0), segunda.getCodigo());
        verify(reservaRepository).saveAndFlush(primera);
        verify(reservaRepository).saveAndFlush(segunda);
    }

    // El caso que motivo fechar la emision: una reserva que ya habia empezado
    // cuando se migro no puede quedar vencida en el acto.
    @Test
    @DisplayName("una reserva ya empezada al migrar recibe su tolerancia completa desde la migracion")
    void laToleranciaCorreDesdeLaMigracion() {
        Reserva empezada = vigente(AHORA.minusHours(2), AHORA.plusHours(2));
        when(reservaRepository.findByEstadoAndCodigoIsNullAndHastaAfter(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of(empezada));

        backfill.asignarFaltantes();

        assertEquals(AHORA.atZone(AccesoDePrueba.ZONA).toInstant(), empezada.getCodigoEmitidoAt());
        assertEquals(AHORA.plusMinutes(30), reglas.limiteTolerancia(empezada));
        assertFalse(reglas.ingresoVencido(empezada, AHORA.plusMinutes(30)));
    }

    @Test
    @DisplayName("es idempotente: si no queda ninguna sin codigo, no escribe nada")
    void sinPendientesNoHaceNada() {
        when(reservaRepository.findByEstadoAndCodigoIsNullAndHastaAfter(ReservaEstado.CONFIRMADA, AHORA))
                .thenReturn(List.of());

        assertEquals(0, backfill.asignarFaltantes());
        verify(reservaRepository, never()).saveAndFlush(any());
    }
}
