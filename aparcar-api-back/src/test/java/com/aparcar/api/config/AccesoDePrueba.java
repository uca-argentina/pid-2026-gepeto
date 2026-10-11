package com.aparcar.api.config;

import com.aparcar.api.service.impl.ReglasAcceso;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Las reglas de acceso con sus valores por defecto, para tests sin contexto de Spring. */
public final class AccesoDePrueba {
    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    public static final String SECRETO = "secreto-de-prueba";

    private AccesoDePrueba() {
    }

    /** 15 min de anticipo, 30 de tolerancia, 15 de gracia, 60 s de permanencia, 120 s de idempotencia. */
    public static AccesoProperties propiedades() {
        return new AccesoProperties(15, 30, 15, 60, 120, 60, SECRETO);
    }

    /** Un reloj detenido en ese momento del predio. */
    public static Clock relojEn(LocalDateTime momento) {
        return Clock.fixed(momento.atZone(ZONA).toInstant(), ZONA);
    }

    public static ReglasAcceso reglas(Clock clock) {
        return new ReglasAcceso(propiedades(), clock);
    }

    /** Reglas con el reloj real: para tests que arman sus datos relativos a ahora. */
    public static ReglasAcceso reglasConRelojReal() {
        return reglas(Clock.system(ZONA));
    }
}
