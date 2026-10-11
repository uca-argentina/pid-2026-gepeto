package com.aparcar.api.entity.reserva;

/**
 * Ciclo de vida: CONFIRMADA → EN_CURSO (ingreso) → FINALIZADA (egreso).
 * CANCELADA y VENCIDA son finales y dejan el codigo de acceso sin valor.
 */
public enum ReservaEstado {
    /** Todavia no ingreso el vehiculo: ocupa la cochera durante su franja. */
    CONFIRMADA,
    /**
     * El vehiculo ingreso y todavia no salio. Ocupa la cochera aunque se haya
     * pasado de su horario: la libera el egreso, no el reloj.
     */
    EN_CURSO,
    /** Dada de baja por el visitante o un admin antes de terminar. */
    CANCELADA,
    /** El vehiculo ya egreso. */
    FINALIZADA,
    /**
     * No-show: se cerro la ventana de ingreso sin que el vehiculo entrara. Lo
     * marca el barrido programado o la primera validacion del codigo que lo
     * detecte, y libera la cochera.
     */
    VENCIDA
}
