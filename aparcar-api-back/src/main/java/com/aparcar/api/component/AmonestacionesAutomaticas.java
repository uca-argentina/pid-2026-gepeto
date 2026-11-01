package com.aparcar.api.component;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;
import com.aparcar.api.repository.AmonestacionRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.IAmonestacionService;
import com.aparcar.api.service.impl.ConfiguracionSancionesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Aplica las faltas que nadie carga a mano: no haber registrado el ingreso o el
 * egreso dentro de la tolerancia.
 *
 * <p>Corre cada minuto y vuelve a mirar las mismas reservas una y otra vez,
 * porque una reserva sin ingreso sigue sin ingreso para siempre. Lo que impide
 * que acumule una falta por minuto es la consulta de
 * {@code existsByReservaIdAndOrigen}: cada reserva puede generar como mucho una
 * falta de cada tipo. Sin eso, olvidarse de marcar el ingreso una vez bloquearía
 * la cuenta en cuatro minutos.
 *
 * <p>Las tolerancias salen de la configuración que edita el administrador, y se
 * leen en cada vuelta: cambiar el número en la pantalla tiene efecto en la
 * corrida siguiente, sin reiniciar nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmonestacionesAutomaticas {

    private final ReservaRepository reservaRepository;
    private final AmonestacionRepository amonestacionRepository;
    private final IAmonestacionService amonestaciones;
    private final ConfiguracionSancionesService configuracion;

    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void aplicar() {
        ConfiguracionSanciones config = configuracion.obtener();
        LocalDateTime ahora = LocalDateTime.now();

        int sinIngreso = sancionar(
                reservaRepository.findSinIngresoDesdeAntesDe(
                        ahora.minusMinutes(config.getToleranciaIngresoMin())),
                OrigenAmonestacion.SIN_INGRESO,
                "No se registró el ingreso dentro de los %d minutos de tolerancia."
                        .formatted(config.getToleranciaIngresoMin()));

        int sinEgreso = sancionar(
                reservaRepository.findSinEgresoHastaAntesDe(
                        ahora.minusMinutes(config.getToleranciaEgresoMin())),
                OrigenAmonestacion.SIN_EGRESO,
                "No se registró el egreso dentro de los %d minutos de tolerancia."
                        .formatted(config.getToleranciaEgresoMin()));

        if (sinIngreso + sinEgreso > 0) {
            log.info("Faltas automáticas aplicadas: {} por falta de ingreso, {} por falta de egreso.",
                    sinIngreso, sinEgreso);
        }
    }

    private int sancionar(List<Reserva> reservas, OrigenAmonestacion origen, String motivo) {
        int aplicadas = 0;

        for (Reserva reserva : reservas) {
            if (amonestacionRepository.existsByReservaIdAndOrigen(reserva.getId(), origen)) {
                continue;
            }
            // El autor va en null: la aplicó el sistema, no una persona. El
            // historial lo muestra como automática en vez de inventar un autor.
            amonestaciones.registrar(
                    reserva.getVisitante(),
                    reserva,
                    reserva.getVehiculo() != null ? reserva.getVehiculo().getPatente() : null,
                    origen,
                    motivo,
                    null);
            aplicadas++;
        }

        return aplicadas;
    }
}
