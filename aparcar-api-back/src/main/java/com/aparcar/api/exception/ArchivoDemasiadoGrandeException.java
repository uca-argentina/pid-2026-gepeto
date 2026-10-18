package com.aparcar.api.exception;

/** El archivo supera el tamaño permitido (413). */
public class ArchivoDemasiadoGrandeException extends RuntimeException {
    public ArchivoDemasiadoGrandeException(String message) {
        super(message);
    }
}
