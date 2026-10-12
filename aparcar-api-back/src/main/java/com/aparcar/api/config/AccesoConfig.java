package com.aparcar.api.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

import static com.aparcar.api.config.ApplicationConstants.ACCESO_CODIGO_SECRET_DEFAULT;
import static com.aparcar.api.config.ApplicationConstants.PROD_ENV;

/**
 * Activa {@link AccesoProperties} y se niega a arrancar en produccion con el
 * secreto de desarrollo: con ese valor, cualquiera que conozca el id de una
 * reserva podria calcular su codigo.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AccesoProperties.class)
public class AccesoConfig {
    private final AccesoProperties properties;
    private final Environment env;

    @PostConstruct
    void verificarSecreto() {
        // Una variable definida pero vacia (ACCESO_CODIGO_SECRET=) le gana al
        // valor por defecto y dejaria el HMAC sin clave.
        if (properties.codigoSecret() == null || properties.codigoSecret().isBlank()) {
            throw new IllegalStateException(
                    "ACCESO_CODIGO_SECRET esta vacio: definilo o quitalo para usar el de desarrollo.");
        }
        if (!ACCESO_CODIGO_SECRET_DEFAULT.equals(properties.codigoSecret())) {
            return;
        }
        if (Arrays.asList(env.getActiveProfiles()).contains(PROD_ENV)) {
            throw new IllegalStateException(
                    "Falta ACCESO_CODIGO_SECRET: no se puede usar el secreto de desarrollo en produccion.");
        }
        log.warn("Se usa el secreto de desarrollo para los codigos de acceso. Defini ACCESO_CODIGO_SECRET fuera de dev.");
    }
}
