package com.aparcar.api.entity.sancion;

/** De dónde salió una amonestación. */
public enum OrigenAmonestacion {
    /** La cargó una persona desde el panel, con su motivo escrito. */
    MANUAL,
    /** No se registró el ingreso dentro de la tolerancia. La aplica el sistema. */
    SIN_INGRESO,
    /** No se registró el egreso dentro de la tolerancia. La aplica el sistema. */
    SIN_EGRESO
}
