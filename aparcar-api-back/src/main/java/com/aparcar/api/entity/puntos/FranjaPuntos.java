package com.aparcar.api.entity.puntos;

import java.time.LocalTime;

/**
 * Puntos por franja horaria: mas puntos donde hay menos demanda, para correr
 * reservas hacia los valles. Hardcodeado a proposito (no es configuracion ni
 * vive en la base): cambiar los puntos es una decision de producto que tiene
 * que pasar por el codigo y sus tests.
 *
 * <p>Hora local del predio. Cada franja incluye el inicio y excluye el fin, y
 * entre las cuatro cubren las 24 horas sin huecos ni solapamientos. Cuenta la
 * hora de inicio de la reserva ({@code desde}): es la que el usuario elige y
 * puede prever al reservar.
 */
public enum FranjaPuntos {
    NOCTURNA("Noche (valle de demanda)", LocalTime.of(22, 0), LocalTime.of(6, 0), 20),
    MANANA("Mañana (pico de demanda)", LocalTime.of(6, 0), LocalTime.of(10, 0), 10),
    MEDIODIA("Mediodía (demanda media)", LocalTime.of(10, 0), LocalTime.of(14, 0), 15),
    TARDE("Tarde (demanda alta)", LocalTime.of(14, 0), LocalTime.of(22, 0), 10);

    private final String nombre;
    private final LocalTime inicio;
    private final LocalTime fin;
    private final int puntos;

    FranjaPuntos(String nombre, LocalTime inicio, LocalTime fin, int puntos) {
        this.nombre = nombre;
        this.inicio = inicio;
        this.fin = fin;
        this.puntos = puntos;
    }

    public String getNombre() {
        return nombre;
    }

    public LocalTime getInicio() {
        return inicio;
    }

    public LocalTime getFin() {
        return fin;
    }

    public int getPuntos() {
        return puntos;
    }

    /** True si la hora cae en la franja: [inicio, fin), cruzando la medianoche si hace falta. */
    public boolean contiene(LocalTime hora) {
        if (inicio.isBefore(fin)) {
            return !hora.isBefore(inicio) && hora.isBefore(fin);
        }
        return !hora.isBefore(inicio) || hora.isBefore(fin);
    }

    public static FranjaPuntos de(LocalTime hora) {
        for (FranjaPuntos franja : values()) {
            if (franja.contiene(hora)) return franja;
        }
        throw new IllegalStateException("Ninguna franja cubre las " + hora);
    }

    /** Lo maximo que da una franja: para destacar las que mas suman. */
    public static int maximo() {
        int max = 0;
        for (FranjaPuntos franja : values()) max = Math.max(max, franja.puntos);
        return max;
    }
}
