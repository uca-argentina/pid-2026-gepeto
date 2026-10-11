package com.aparcar.api.component;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.impl.ReglasAcceso;
import com.aparcar.api.service.impl.PlanificacionReservas;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pasa a VENCIDA (no-show) las reservas CONFIRMADAS a las que se les cerro la
 * ventana de ingreso sin que el vehiculo entrara: paso la tolerancia de
 * ingreso tardio o termino la franja.
 *
 * <p>Ya no hay reservas que pasen a FINALIZADA por tiempo: FINALIZADA quiere
 * decir que el vehiculo egreso, y eso lo registra el operador. Una reserva
 * EN_CURSO nunca vence sola; sigue ocupando hasta el egreso.
 *
 * <p>Que la reserva deje de ocupar no depende de esta tarea: la validacion del
 * codigo hace la misma evaluacion al momento ({@link ReglasAcceso#marcarSiVencio}),
 * y una CONFIRMADA cuya franja termino ya no se solapa con nada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservasVencidas {
    private final ReservaRepository reservaRepository;
    private final ReglasAcceso reglasAcceso;
    private final PlanificacionReservas planificacion;

    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void marcarVencidas() {
        planificacion.bloquear();
        LocalDateTime ahora = reglasAcceso.ahora();
        List<Reserva> vencidas = reservaRepository.findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, ahora)
                .stream()
                .filter(reserva -> reglasAcceso.marcarSiVencio(reserva, ahora))
                .toList();

        if (vencidas.isEmpty()) {
            planificacion.promover();
            return;
        }

        reservaRepository.saveAll(vencidas);
        planificacion.promover();

        log.info("Se marcaron {} reservas como VENCIDA.", vencidas.size());
    }
}
