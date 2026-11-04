package com.aparcar.api.entity.puntos;

/** Por que cambio el saldo de puntos. */
public enum TipoMovimientoPuntos {
    /** Puntos de la franja horaria de una reserva usada (al ingresar). */
    ACREDITACION_FRANJA,
    /** Bonus por llegar a un multiplo de dias de racha. */
    BONUS_RACHA,
    /** Puntos gastados como descuento al crear una reserva. */
    CANJE,
    /** Devolucion de los puntos canjeados al cancelar la reserva antes de usarla. */
    REINTEGRO
}
