package com.aparcar.api.service.impl;

import com.aparcar.api.config.AccesoProperties;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Codigo corto de acceso de una reserva: el mismo valor que lleva el QR.
 *
 * <p>Son 10 caracteres base32 al estilo Crockford (sin I, L, O ni U, para que
 * no se confundan al dictarlos o tipearlos), que se muestran como
 * {@code XXXXX-XXXXX}. Se derivan con HMAC-SHA256 del id de la reserva y un
 * secreto del servidor, truncado a 50 bits: sin el secreto no se puede
 * calcular el codigo de otra reserva.
 *
 * <p>El codigo es solo una llave. No "contiene" los datos de la reserva (un
 * hash es de una via): el backend los devuelve al validarlo. Por eso el QR no
 * filtra datos personales, queda chico y no puede quedar desactualizado.
 */
@Component
public class CodigoAcceso {
    /** Lo que lleva el QR delante del codigo; el lector lo ignora al validar. */
    public static final String PREFIJO_QR = "APARCAR:";
    public static final int LONGITUD = 10;
    public static final String ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int BITS_POR_CARACTER = 5;
    /** Una colision en 50 bits es casi imposible; el tope solo evita un bucle sin fin. */
    private static final int MAX_INTENTOS = 20;

    private final byte[] secreto;
    private final ReservaRepository reservaRepository;

    public CodigoAcceso(AccesoProperties properties, ReservaRepository reservaRepository) {
        this.secreto = properties.codigoSecret().getBytes(StandardCharsets.UTF_8);
        this.reservaRepository = reservaRepository;
    }

    /**
     * Le da su codigo a una reserva que ya tiene id. Si el derivado choca con
     * el de otra reserva, se vuelve a derivar con un contador.
     */
    public void asignar(Reserva reserva, Instant emitido) {
        for (int intento = 0; intento < MAX_INTENTOS; intento++) {
            String codigo = derivar(secreto, reserva.getId(), intento);
            if (!reservaRepository.existsByCodigo(codigo)) {
                reserva.setCodigo(codigo);
                reserva.setCodigoEmitidoAt(emitido);
                return;
            }
        }
        throw new IllegalStateException("No se pudo generar un codigo de acceso unico.");
    }

    /** Deterministico: el mismo id, secreto e intento dan siempre el mismo codigo. */
    public static String derivar(byte[] secreto, UUID id, int intento) {
        String mensaje = intento == 0 ? id.toString() : id + ":" + intento;
        byte[] hmac;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto, "HmacSHA256"));
            hmac = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 no disponible", e);
        }

        // Los primeros 50 bits del HMAC, de a 5 bits por caracter.
        long bits = 0;
        for (int i = 0; i < 7; i++) {
            bits = (bits << 8) | (hmac[i] & 0xFF);
        }
        bits >>>= 56 - LONGITUD * BITS_POR_CARACTER;

        char[] codigo = new char[LONGITUD];
        for (int i = LONGITUD - 1; i >= 0; i--) {
            codigo[i] = ALFABETO.charAt((int) (bits & 0x1F));
            bits >>>= BITS_POR_CARACTER;
        }
        return new String(codigo);
    }

    /**
     * Lleva lo que se escaneo o tipeo a la forma guardada: sin el prefijo del
     * QR, en mayusculas, sin espacios ni guiones, y con las equivalencias de
     * Crockford (I y L se leen como 1, O como 0).
     *
     * @throws ValidationException Si no quedan 10 caracteres validos.
     */
    public static String normalizar(String entrada) {
        if (entrada == null) {
            throw formatoInvalido();
        }
        String texto = entrada.trim().toUpperCase(Locale.ROOT);
        if (texto.startsWith(PREFIJO_QR)) {
            texto = texto.substring(PREFIJO_QR.length());
        }

        StringBuilder codigo = new StringBuilder(LONGITUD);
        for (char c : texto.toCharArray()) {
            if (c == '-' || Character.isWhitespace(c)) continue;
            char equivalente = switch (c) {
                case 'I', 'L' -> '1';
                case 'O' -> '0';
                default -> c;
            };
            if (ALFABETO.indexOf(equivalente) < 0) {
                throw formatoInvalido();
            }
            codigo.append(equivalente);
        }

        if (codigo.length() != LONGITUD) {
            throw formatoInvalido();
        }
        return codigo.toString();
    }

    /** {@code ABCDE12345} → {@code ABCDE-12345}. */
    public static String formatear(String codigo) {
        if (codigo == null || codigo.length() != LONGITUD) return codigo;
        return codigo.substring(0, LONGITUD / 2) + "-" + codigo.substring(LONGITUD / 2);
    }

    private static ValidationException formatoInvalido() {
        return new ValidationException(
                "El código no tiene un formato válido: son 10 letras o números, por ejemplo ABCDE-12345.");
    }
}
