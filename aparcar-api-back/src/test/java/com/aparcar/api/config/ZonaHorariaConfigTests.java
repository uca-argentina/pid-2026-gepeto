package com.aparcar.api.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caja blanca de la zona horaria de la aplicacion.
 *
 * <p>Estos tests existen por un bug concreto: el contenedor arrancaba en UTC
 * mientras el navegador mandaba hora local, y una reserva de las 16 a las 17
 * llegaba a un backend que creia que eran las 19, asi que la rechazaba por
 * "terminada en el pasado". No se habia detectado porque las pruebas corrian
 * con el backend sobre el host, donde la zona coincidia por casualidad.
 */
class ZonaHorariaConfigTests {

    private TimeZone original;

    @BeforeEach
    void guardarZonaOriginal() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restaurarZonaOriginal() {
        TimeZone.setDefault(original);
    }

    private static ZonaHorariaConfig configCon(String zona) {
        ZonaHorariaConfig config = new ZonaHorariaConfig();
        ReflectionTestUtils.setField(config, "zonaHoraria", zona);
        return config;
    }

    @Test
    @DisplayName("fija la zona horaria configurada como default de la JVM")
    void fijaLaZonaConfigurada() {
        configCon("America/Argentina/Buenos_Aires").fijarZonaHoraria();

        assertEquals(ZoneId.of("America/Argentina/Buenos_Aires"), TimeZone.getDefault().toZoneId());
    }

    // El caso que rompia: arrancar en UTC y no corregirlo dejaba a
    // LocalDateTime.now() tres horas adelantado respecto del reloj del usuario.
    @Test
    @DisplayName("corrige un arranque en UTC para que la hora local sea la del predio")
    void corrigeUnArranqueEnUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        LocalDateTime enUtc = LocalDateTime.now();

        configCon("America/Argentina/Buenos_Aires").fijarZonaHoraria();
        LocalDateTime enBuenosAires = LocalDateTime.now();

        // Buenos Aires va detras de UTC: la hora local tiene que ser anterior.
        assertTrue(enBuenosAires.isBefore(enUtc),
                "tras fijar la zona, la hora local deberia estar detras de la de UTC");
    }

    @Test
    @DisplayName("respeta una zona distinta si el despliegue la configura")
    void respetaOtraZonaConfigurada() {
        configCon("Europe/Madrid").fijarZonaHoraria();

        assertEquals(ZoneId.of("Europe/Madrid"), TimeZone.getDefault().toZoneId());
    }
}
