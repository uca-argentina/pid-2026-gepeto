package com.aparcar.api.service.impl;

import com.aparcar.api.entity.reserva.AccesoIntento;
import com.aparcar.api.entity.reserva.AccesoOperacion;
import com.aparcar.api.repository.AccesoIntentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Deja asentado cada intento de validar un codigo.
 *
 * <p>Va en su propia transaccion: un intento rechazado o un conflicto de
 * concurrencia deshacen la operacion, pero el registro de que alguien lo
 * intento tiene que quedar igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistroIntentosAcceso {
    public static final String OK = "OK";
    public static final String YA_REGISTRADO = "YA_REGISTRADO";
    public static final String FORMATO_INVALIDO = "FORMATO_INVALIDO";
    public static final String INEXISTENTE = "INEXISTENTE";
    public static final String CONFLICTO = "CONFLICTO";

    private final AccesoIntentoRepository accesoIntentoRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(AccesoOperacion operacion, String codigo, UUID reservaId, String resultado,
                          String operadorEmail) {
        if (!OK.equals(resultado) && !YA_REGISTRADO.equals(resultado)) {
            log.warn("Validacion de acceso rechazada: operacion={} codigo={} reserva={} resultado={} operador={}",
                    operacion, codigo, reservaId, resultado, operadorEmail);
        }
        accesoIntentoRepository.save(new AccesoIntento(
                clock.instant(), operacion, codigo, reservaId, resultado, operadorEmail));
    }
}
