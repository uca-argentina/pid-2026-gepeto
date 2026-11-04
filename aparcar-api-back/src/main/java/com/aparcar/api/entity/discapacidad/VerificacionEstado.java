package com.aparcar.api.entity.discapacidad;

/**
 * Estado de una solicitud de verificacion de discapacidad.
 *
 * <p>EN_REVISION → VERIFICADA (aprobada) o RECHAZADA. Una VERIFICADA puede
 * pasar a VENCIDA (por su fecha de vencimiento) o a REVOCADA (por un ADMIN).
 * CANCELADA es la que retiro el propio visitante antes de que se revisara.
 * Solo VERIFICADA habilita las cocheras ACCESIBLE.
 */
public enum VerificacionEstado {
    EN_REVISION,
    VERIFICADA,
    RECHAZADA,
    REVOCADA,
    VENCIDA,
    CANCELADA
}
