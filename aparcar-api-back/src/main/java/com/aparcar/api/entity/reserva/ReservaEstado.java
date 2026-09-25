package com.aparcar.api.entity.reserva;

public enum ReservaEstado {
    /** La franja todavia no termino: ocupa la cochera. */
    CONFIRMADA,
    /** Dada de baja por el visitante o un admin antes de terminar. */
    CANCELADA,
    /**
     * La franja ya paso. Lo marca una tarea programada, pero es solo
     * informativo: la disponibilidad se calcula por solapamiento de rangos, asi
     * que una reserva terminada deja de ocupar aunque la tarea no haya corrido.
     */
    FINALIZADA
}
