package com.aparcar.api.component;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.impl.CodigoAcceso;
import com.aparcar.api.service.impl.ReglasAcceso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Le da codigo de acceso a las reservas CONFIRMADAS vigentes que no lo tienen:
 * las creadas antes de esta feature.
 *
 * <p>No puede ser parte del changelog de Liquibase porque el codigo es un HMAC
 * con un secreto del servidor, que SQL no calcula. Corre en cada arranque y es
 * idempotente: si no queda ninguna sin codigo, no hace nada.
 *
 * <p>La emision queda fechada ahora, y la tolerancia de ingreso tardio se
 * cuenta desde ahi: una reserva que ya habia empezado al migrar no vence en el
 * acto, tiene su margen completo para que el vehiculo entre.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodigosAccesoBackfill implements ApplicationRunner {
    private final ReservaRepository reservaRepository;
    private final CodigoAcceso codigoAcceso;
    private final ReglasAcceso reglasAcceso;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        asignarFaltantes();
    }

    @Transactional
    public int asignarFaltantes() {
        List<Reserva> sinCodigo = reservaRepository.findByEstadoAndCodigoIsNullAndHastaAfter(
                ReservaEstado.CONFIRMADA, reglasAcceso.ahora());
        if (sinCodigo.isEmpty()) {
            return 0;
        }

        Instant emitido = reglasAcceso.instante();
        for (Reserva reserva : sinCodigo) {
            codigoAcceso.asignar(reserva, emitido);
            // Se guarda de a una: asignar() consulta la base para descartar
            // colisiones, y tiene que ver los codigos ya asignados en esta pasada.
            reservaRepository.saveAndFlush(reserva);
        }

        log.info("Se asignaron codigos de acceso a {} reservas existentes.", sinCodigo.size());
        return sinCodigo.size();
    }
}
