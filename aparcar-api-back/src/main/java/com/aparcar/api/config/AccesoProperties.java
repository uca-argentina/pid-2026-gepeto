package com.aparcar.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Reglas del control de acceso por codigo/QR ({@code aparcar.acceso.*}).
 *
 * @param ingresoAnticipadoMin  Minutos antes de {@code desde} desde los que ya se puede ingresar.
 * @param ingresoToleranciaMin  Minutos despues del inicio (o de la emision del codigo, si es
 *                              posterior) en los que todavia se acepta el ingreso. Nunca pasa de
 *                              {@code hasta}.
 * @param egresoGraciaMin       Minutos despues de {@code hasta} en los que el egreso no se marca
 *                              como excedido.
 * @param permanenciaMinimaSeg  Segundos despues del ingreso en los que no se acepta el egreso:
 *                              evita que un segundo escaneo accidental registre la salida.
 * @param idempotenciaSeg       Ventana en la que repetir la misma accion devuelve lo ya registrado
 *                              en vez de rechazarla (doble clic, reintento de red).
 * @param rateLimitPorMinuto    Pedidos por minuto y por operador a /api/v1/accesos/**.
 * @param codigoSecret          Secreto del HMAC que deriva los codigos. El valor por defecto es solo
 *                              para desarrollo: en produccion va en ACCESO_CODIGO_SECRET.
 */
@ConfigurationProperties(prefix = "aparcar.acceso")
public record AccesoProperties(
        @DefaultValue("15") int ingresoAnticipadoMin,
        @DefaultValue("30") int ingresoToleranciaMin,
        @DefaultValue("15") int egresoGraciaMin,
        @DefaultValue("60") int permanenciaMinimaSeg,
        @DefaultValue("120") int idempotenciaSeg,
        @DefaultValue("60") int rateLimitPorMinuto,
        @DefaultValue(ApplicationConstants.ACCESO_CODIGO_SECRET_DEFAULT) String codigoSecret
) {
}
