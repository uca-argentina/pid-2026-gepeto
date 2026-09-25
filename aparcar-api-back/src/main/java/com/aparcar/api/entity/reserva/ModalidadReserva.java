package com.aparcar.api.entity.reserva;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Como esta reservando el cliente, deducido de cuanto dura la franja.
 *
 * <p>Se clasifica por duracion y no por horario de arranque: media jornada es
 * medio dia de estadia, empiece a las 8 o a las 15.
 *
 * <p>No se guarda en la base: es una lectura de la franja, asi que si cambian
 * los umbrales las reservas viejas se reinterpretan solas en vez de quedar
 * etiquetadas con un criterio viejo.
 */
public enum ModalidadReserva {
    /** Lo habitual: un rato puntual. */
    FRANJA("Por franja horaria"),
    /** Media jornada o mas, pero menos de un dia. */
    MEDIA_JORNADA("Media jornada"),
    /** Un dia entero o mas. */
    JORNADA_COMPLETA("Jornada completa");

    private static final long MINUTOS_MEDIA_JORNADA = 12 * 60;
    private static final long MINUTOS_JORNADA_COMPLETA = 24 * 60;

    private final String etiqueta;

    ModalidadReserva(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static ModalidadReserva de(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null) {
            return FRANJA;
        }
        return deMinutos(Duration.between(desde, hasta).toMinutes());
    }

    public static ModalidadReserva deMinutos(long minutos) {
        if (minutos >= MINUTOS_JORNADA_COMPLETA) {
            return JORNADA_COMPLETA;
        }
        if (minutos >= MINUTOS_MEDIA_JORNADA) {
            return MEDIA_JORNADA;
        }
        return FRANJA;
    }
}
