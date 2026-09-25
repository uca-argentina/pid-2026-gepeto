package com.aparcar.api.entity;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.ModalidadReserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Caja blanca de la deteccion de modalidad.
 *
 * <p>Los umbrales se prueban en el borde exacto a proposito: entre 11:45 y
 * 12:00 cambia la etiqueta, y un {@code >} que se vuelva {@code >=} sin que
 * nadie lo note haria que una reserva de doce horas dejara de contar como
 * media jornada.
 */
@UnitTests
class ModalidadReservaTests {

    @ParameterizedTest(name = "{0} minutos -> {1}")
    @CsvSource({
            "15,   FRANJA",
            "60,   FRANJA",
            "719,  FRANJA",
            "720,  MEDIA_JORNADA",
            "721,  MEDIA_JORNADA",
            "1439, MEDIA_JORNADA",
            "1440, JORNADA_COMPLETA",
            "2880, JORNADA_COMPLETA",
    })
    @DisplayName("la modalidad sale de la duracion, con los bordes en 12 h y 24 h")
    void laModalidadSaleDeLaDuracion(long minutos, ModalidadReserva esperada) {
        assertEquals(esperada, ModalidadReserva.deMinutos(minutos));
    }

    // Se clasifica por duracion, no por horario de arranque: media jornada es
    // medio dia de estadia, empiece a las 8 o a las 15.
    @Test
    @DisplayName("el horario de arranque no cambia la modalidad")
    void elHorarioDeArranqueNoCambiaLaModalidad() {
        LocalDateTime manana = LocalDateTime.of(2026, 10, 1, 8, 0);
        LocalDateTime tarde = LocalDateTime.of(2026, 10, 1, 15, 0);

        assertEquals(ModalidadReserva.MEDIA_JORNADA, ModalidadReserva.de(manana, manana.plusHours(12)));
        assertEquals(ModalidadReserva.MEDIA_JORNADA, ModalidadReserva.de(tarde, tarde.plusHours(12)));
    }

    @Test
    @DisplayName("una franja sin datos se considera franja horaria, sin romper")
    void unaFranjaSinDatosNoRompe() {
        assertEquals(ModalidadReserva.FRANJA, ModalidadReserva.de(null, null));
    }
}
