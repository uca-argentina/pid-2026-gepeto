package com.aparcar.api.config;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Archivos y propiedades de prueba para la verificacion de discapacidad. */
public final class DiscapacidadDePrueba {
    private DiscapacidadDePrueba() {
    }

    /** 5 MB, 30 dias de retencion, 30 de gracia, 5 subidas por hora, sin mails. */
    public static DiscapacidadProperties propiedades(String directorio) {
        return new DiscapacidadProperties(5, 30, 30, directorio, 5, false);
    }

    public static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj << >> endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    }

    public static byte[] jpg() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F'};
    }

    /** Un PDF valido de exactamente esa cantidad de bytes. */
    public static byte[] pdfDe(int bytes) {
        byte[] contenido = Arrays.copyOf(pdf(), bytes);
        Arrays.fill(contenido, pdf().length, bytes, (byte) ' ');
        return contenido;
    }
}
