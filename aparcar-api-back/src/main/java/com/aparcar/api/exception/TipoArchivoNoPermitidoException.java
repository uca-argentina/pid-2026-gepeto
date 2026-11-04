package com.aparcar.api.exception;

/**
 * El archivo no es de un tipo aceptado, o su contenido real no coincide con
 * la extension o el tipo que declara (415).
 */
public class TipoArchivoNoPermitidoException extends RuntimeException {
    public TipoArchivoNoPermitidoException(String message) {
        super(message);
    }
}
