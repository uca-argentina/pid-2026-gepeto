package com.aparcar.api.exception;

/**
 * El pedido choca con el estado actual de lo que intenta cambiar (409): por
 * ejemplo, otro administrador ya resolvio la misma solicitud.
 */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String message) {
        super(message);
    }
}
