package com.aparcar.api.component;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;
import com.aparcar.api.repository.AmonestacionRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.IAmonestacionService;
import com.aparcar.api.service.impl.ConfiguracionSancionesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca sobre la tarea que aplica faltas sola.
 *
 * <p>Lo que se prueba acá no se puede ver mirando la pantalla, y es lo más
 * peligroso de todo el régimen: la tarea corre <b>cada minuto</b> y una reserva
 * a la que no se le registró el ingreso sigue sin ingreso para siempre. Sin el
 * guard de idempotencia, olvidarse de marcar una entrada <b>una sola vez</b>
 * acumularía una falta por minuto y bloquearía la cuenta en cuatro minutos.
 *
 * <p>Ese fallo no se nota revisando el código ni probando a mano una vez: hay
 * que correr la tarea dos veces seguidas, que es lo que hacen estos tests.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmonestacionesAutomaticasTests {

    @Mock private ReservaRepository reservaRepository;
    @Mock private AmonestacionRepository amonestacionRepository;
    @Mock private IAmonestacionService amonestaciones;
    @Mock private ConfiguracionSancionesService configuracion;

    @InjectMocks private AmonestacionesAutomaticas tarea;

    private Reserva reserva;

    @BeforeEach
    void init() {
        when(configuracion.obtener()).thenReturn(ConfiguracionSanciones.porDefecto());

        Visitante visitante = new Visitante("Juan Perez", "30111222", "juan@test.com", "x", null,
                Set.of(AppAuthority.USER), true);
        visitante.setId(UUID.randomUUID());

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("ABC123");
        vehiculo.setTipo(VehiculoTipo.AUTO);

        reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);

        // Por omisión: nada sin ingreso ni sin egreso.
        when(reservaRepository.findSinIngresoDesdeAntesDe(any())).thenReturn(List.of());
        when(reservaRepository.findSinEgresoHastaAntesDe(any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("Aplica una falta a la reserva sin ingreso")
    void aplicaFaltaPorFaltaDeIngreso() {
        when(reservaRepository.findSinIngresoDesdeAntesDe(any())).thenReturn(List.of(reserva));

        tarea.aplicar();

        verify(amonestaciones).registrar(
                eq(reserva.getVisitante()), eq(reserva), eq("ABC123"),
                eq(OrigenAmonestacion.SIN_INGRESO), any(), eq(null));
    }

    @Test
    @DisplayName("Aplica una falta a la reserva sin egreso")
    void aplicaFaltaPorFaltaDeEgreso() {
        when(reservaRepository.findSinEgresoHastaAntesDe(any())).thenReturn(List.of(reserva));

        tarea.aplicar();

        verify(amonestaciones).registrar(
                any(), any(), any(), eq(OrigenAmonestacion.SIN_EGRESO), any(), eq(null));
    }

    @Test
    @DisplayName("Correr dos veces no aplica la falta dos veces")
    void noRepiteLaFaltaEnLaSegundaCorrida() {
        when(reservaRepository.findSinIngresoDesdeAntesDe(any())).thenReturn(List.of(reserva));

        // Primera vuelta: todavía no existe, se aplica.
        when(amonestacionRepository.existsByReservaIdAndOrigen(
                reserva.getId(), OrigenAmonestacion.SIN_INGRESO)).thenReturn(false);
        tarea.aplicar();

        // Segunda vuelta, un minuto después: la reserva sigue sin ingreso y la
        // consulta la devuelve igual, pero la falta ya está registrada.
        when(amonestacionRepository.existsByReservaIdAndOrigen(
                reserva.getId(), OrigenAmonestacion.SIN_INGRESO)).thenReturn(true);
        tarea.aplicar();

        verify(amonestaciones, times(1)).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("La falta de ingreso y la de egreso son independientes: una no tapa a la otra")
    void ingresoYEgresoSeCuentanPorSeparado() {
        when(reservaRepository.findSinIngresoDesdeAntesDe(any())).thenReturn(List.of(reserva));
        when(reservaRepository.findSinEgresoHastaAntesDe(any())).thenReturn(List.of(reserva));
        // Ya existe la de ingreso, pero no la de egreso.
        when(amonestacionRepository.existsByReservaIdAndOrigen(
                reserva.getId(), OrigenAmonestacion.SIN_INGRESO)).thenReturn(true);
        when(amonestacionRepository.existsByReservaIdAndOrigen(
                reserva.getId(), OrigenAmonestacion.SIN_EGRESO)).thenReturn(false);

        tarea.aplicar();

        verify(amonestaciones, times(1)).registrar(
                any(), any(), any(), eq(OrigenAmonestacion.SIN_EGRESO), any(), any());
        verify(amonestaciones, never()).registrar(
                any(), any(), any(), eq(OrigenAmonestacion.SIN_INGRESO), any(), any());
    }

    @Test
    @DisplayName("Sin reservas atrasadas no toca nada")
    void sinAtrasosNoHaceNada() {
        tarea.aplicar();

        verify(amonestaciones, never()).registrar(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Usa la tolerancia configurada, no un número fijo")
    void respetaLaToleranciaConfigurada() {
        ConfiguracionSanciones config = ConfiguracionSanciones.porDefecto();
        config.setToleranciaIngresoMin(45);
        when(configuracion.obtener()).thenReturn(config);
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime antes = LocalDateTime.now();

        tarea.aplicar();

        // El corte tiene que ser ~45 minutos atrás: si fuera fijo, cambiar el
        // número en la pantalla no tendría ningún efecto.
        verify(reservaRepository).findSinIngresoDesdeAntesDe(captor.capture());
        assertThat(captor.getValue())
                .isBefore(antes.minusMinutes(44))
                .isAfter(antes.minusMinutes(46));
    }

    @Test
    @DisplayName("El motivo que queda escrito dice cuántos minutos se toleraron")
    void elMotivoExplicaLaTolerancia() {
        when(reservaRepository.findSinIngresoDesdeAntesDe(any())).thenReturn(List.of(reserva));
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        tarea.aplicar();

        verify(amonestaciones).registrar(any(), any(), any(), any(), captor.capture(), any());
        // Queda en el historial permanente de la persona, así que tiene que
        // explicarse solo meses después.
        assertThat(captor.getValue()).contains("15 minutos");
    }
}
