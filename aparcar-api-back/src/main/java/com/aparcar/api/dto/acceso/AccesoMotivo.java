package com.aparcar.api.dto.acceso;

/** Por que un codigo de acceso no se puede usar en este momento. */
public enum AccesoMotivo {
    /** Todavia no abrio la ventana de ingreso. */
    ANTICIPADO,
    /** Se cerro la ventana de ingreso sin que el vehiculo entrara. */
    VENCIDA,
    CANCELADA,
    /** Ya se registraron el ingreso y el egreso. */
    YA_UTILIZADA,
    /** El ingreso es demasiado reciente para registrar la salida. */
    PERMANENCIA_MINIMA,
    /** Se pidio un ingreso y el vehiculo ya esta adentro. */
    INGRESO_YA_REGISTRADO,
    /** La reserva anterior de la cochera todavia no registro su egreso. */
    COCHERA_OCUPADA,
    /** El mismo vehiculo ya ingreso con otra reserva. */
    VEHICULO_EN_CURSO,
    /** Se pidio un egreso y el vehiculo nunca ingreso. */
    SIN_INGRESO
}
