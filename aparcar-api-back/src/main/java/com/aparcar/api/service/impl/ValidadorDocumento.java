package com.aparcar.api.service.impl;

import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.exception.ArchivoDemasiadoGrandeException;
import com.aparcar.api.exception.TipoArchivoNoPermitidoException;
import com.aparcar.api.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Valida un certificado antes de guardarlo.
 *
 * <p>No confia en nada de lo que manda el cliente: la extension, el
 * content-type declarado y los primeros bytes del archivo (su firma real)
 * tienen que coincidir los tres en el mismo formato. Un PDF renombrado a .png,
 * o un ejecutable con content-type de imagen, se rechazan.
 */
@Component
@RequiredArgsConstructor
public class ValidadorDocumento {
    /** Lo que queda de un archivo aceptado: todo calculado por el servidor. */
    public record DocumentoValidado(String extension, String tipo, String nombre, byte[] contenido, String sha256) {
    }

    private enum Formato {
        PDF("pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'}),
        JPG("jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("png", "image/png", new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        final String extension;
        final String tipo;
        final byte[] firma;

        Formato(String extension, String tipo, byte[] firma) {
            this.extension = extension;
            this.tipo = tipo;
            this.firma = firma;
        }

        boolean coincide(byte[] contenido) {
            if (contenido.length < firma.length) return false;
            for (int i = 0; i < firma.length; i++) {
                if (contenido[i] != firma[i]) return false;
            }
            return true;
        }
    }

    private static final Map<String, Formato> POR_EXTENSION = Map.of(
            "pdf", Formato.PDF, "jpg", Formato.JPG, "jpeg", Formato.JPG, "png", Formato.PNG);

    private static final String NO_PERMITIDO =
            "El certificado tiene que ser un PDF, JPG o PNG.";

    private final DiscapacidadProperties properties;

    public DocumentoValidado validar(String nombreOriginal, String tipoDeclarado, byte[] contenido) {
        if (contenido == null || contenido.length == 0) {
            throw new ValidationException("El archivo está vacío.");
        }
        if (contenido.length > properties.documentoMaxBytes()) {
            throw demasiadoGrande();
        }

        Formato porExtension = POR_EXTENSION.get(extension(nombreOriginal));
        if (porExtension == null) {
            throw new TipoArchivoNoPermitidoException(NO_PERMITIDO);
        }
        String tipo = tipoDeclarado == null ? "" : tipoDeclarado.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!porExtension.tipo.equals(tipo)) {
            throw new TipoArchivoNoPermitidoException(NO_PERMITIDO);
        }
        if (!porExtension.coincide(contenido)) {
            throw new TipoArchivoNoPermitidoException(
                    "El contenido del archivo no corresponde a un %s. Subí el certificado original."
                            .formatted(porExtension.name()));
        }

        return new DocumentoValidado(porExtension.extension, porExtension.tipo, sanear(nombreOriginal, porExtension),
                contenido, sha256(contenido));
    }

    /** Para cortar antes de leer el archivo entero, con el tamaño que informa el multipart. */
    public void validarTamano(long bytes) {
        if (bytes > properties.documentoMaxBytes()) {
            throw demasiadoGrande();
        }
    }

    private ArchivoDemasiadoGrandeException demasiadoGrande() {
        return new ArchivoDemasiadoGrandeException(
                "El certificado no puede superar los %d MB.".formatted(properties.documentoMaxMb()));
    }

    private static String extension(String nombre) {
        String base = soloNombre(nombre);
        int punto = base.lastIndexOf('.');
        return punto < 0 ? "" : base.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /** Sin carpetas: lo que venga despues de la ultima / o \. */
    private static String soloNombre(String nombre) {
        if (nombre == null) return "";
        String limpio = nombre.replace('\\', '/');
        return limpio.substring(limpio.lastIndexOf('/') + 1);
    }

    /**
     * El nombre original, solo para mostrarlo: sin carpetas ni caracteres
     * raros, recortado, y siempre con la extension real. Nunca se usa como
     * ruta: el archivo se guarda con un UUID.
     */
    static String sanear(String nombreOriginal, Formato formato) {
        String base = soloNombre(nombreOriginal);
        int punto = base.lastIndexOf('.');
        base = punto < 0 ? base : base.substring(0, punto);
        base = Normalizer.normalize(base, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N} ._-]", "_")
                .replaceAll("\\.{2,}", "_")
                .replaceAll("^[._ ]+", "")
                .trim();
        if (base.isEmpty()) base = "certificado";
        if (base.length() > 100) base = base.substring(0, 100);
        return base + "." + formato.extension;
    }

    private static String sha256(byte[] contenido) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
