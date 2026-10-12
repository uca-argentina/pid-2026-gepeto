package com.aparcar.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** El secreto de los codigos de acceso: con el de desarrollo, produccion no arranca. */
class AccesoConfigTests {

    private static AccesoConfig config(String secreto, String... perfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfiles);
        return new AccesoConfig(new AccesoProperties(15, 30, 15, 60, 120, 60, secreto), env);
    }

    @Test
    @DisplayName("en produccion, el secreto de desarrollo impide arrancar")
    void produccionRechazaElSecretoDeDesarrollo() {
        assertThrows(IllegalStateException.class,
                () -> config(ApplicationConstants.ACCESO_CODIGO_SECRET_DEFAULT, "prod").verificarSecreto());
    }

    @Test
    @DisplayName("en desarrollo, el secreto por defecto se acepta (con una advertencia en el log)")
    void desarrolloAceptaElSecretoPorDefecto() {
        assertDoesNotThrow(() -> config(ApplicationConstants.ACCESO_CODIGO_SECRET_DEFAULT, "dev").verificarSecreto());
    }

    @Test
    @DisplayName("un secreto propio se acepta en produccion")
    void produccionAceptaUnSecretoPropio() {
        assertDoesNotThrow(() -> config("un-secreto-largo-y-propio", "prod").verificarSecreto());
    }

    @Test
    @DisplayName("una variable definida pero vacia se rechaza en vez de dejar el HMAC sin clave")
    void rechazaUnSecretoVacio() {
        assertThrows(IllegalStateException.class, () -> config("  ", "dev").verificarSecreto());
    }
}
