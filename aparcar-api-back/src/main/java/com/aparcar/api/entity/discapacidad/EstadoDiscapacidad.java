package com.aparcar.api.entity.discapacidad;

/**
 * Como esta la verificacion de una persona, mirando su ultima solicitud. Es lo
 * que se le muestra en el perfil.
 *
 * <p>No se guarda: se deduce de la solicitud mas reciente (una CANCELADA
 * cuenta como no haber pedido nada).
 */
public enum EstadoDiscapacidad {
    SIN_SOLICITUD,
    EN_REVISION,
    VERIFICADA,
    RECHAZADA,
    REVOCADA,
    VENCIDA;

    public static EstadoDiscapacidad de(VerificacionEstado estado) {
        if (estado == null) return SIN_SOLICITUD;
        return switch (estado) {
            case EN_REVISION -> EN_REVISION;
            case VERIFICADA -> VERIFICADA;
            case RECHAZADA -> RECHAZADA;
            case REVOCADA -> REVOCADA;
            case VENCIDA -> VENCIDA;
            case CANCELADA -> SIN_SOLICITUD;
        };
    }
}
