package com.aparcar.api.component;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pasa a FINALIZADA las reservas cuya franja ya termino.
 *
 * <p>Importante: esto NO es lo que libera la cochera. La disponibilidad se
 * calcula por solapamiento de rangos, asi que una reserva vencida deja de
 * ocupar en el instante exacto en que termina, corra o no esta tarea. Lo que
 * hace es mantener el estado legible: sin esto una reserva del mes pasado
 * seguiria figurando como CONFIRMADA en el listado.
 *
 * <p>Que sea meramente informativo es deliberado: si el proceso se cae un fin
 * de semana, nadie se queda sin poder reservar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservasVencidas {
    private final ReservaRepository reservaRepository;

    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void finalizarVencidas() {
        List<Reserva> vencidas = reservaRepository.findByEstadoAndHastaLessThanEqual(
                ReservaEstado.CONFIRMADA, LocalDateTime.now());

        if (vencidas.isEmpty()) {
            return;
        }

        vencidas.forEach(reserva -> reserva.setEstado(ReservaEstado.FINALIZADA));
        reservaRepository.saveAll(vencidas);

        log.info("Se marcaron {} reservas como FINALIZADA.", vencidas.size());
    }
}
