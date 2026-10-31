package com.aparcar.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Gamificacion con puntos ({@code aparcar.puntos.*}). Los puntos por franja
 * horaria no estan aca a proposito: viven en {@code FranjaPuntos}, en el codigo.
 *
 * @param duracionMinimaMin   Minutos minimos de una reserva para que sume puntos de franja.
 * @param maxAcreditacionesDia Reservas por dia (del ingreso) que pueden sumar puntos de franja.
 * @param rachaDias           Cada cuantos dias seguidos de racha se da el bonus.
 * @param rachaBonus          Puntos del bonus de racha.
 * @param descuentoMaxPct     Tope del descuento por canje, en porcentaje del precio.
 */
@ConfigurationProperties(prefix = "aparcar.puntos")
public record PuntosProperties(
        @DefaultValue("60") int duracionMinimaMin,
        @DefaultValue("2") int maxAcreditacionesDia,
        @DefaultValue("7") int rachaDias,
        @DefaultValue("10") int rachaBonus,
        @DefaultValue("50") int descuentoMaxPct
) {
}
