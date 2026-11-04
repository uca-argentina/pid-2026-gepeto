package com.aparcar.api.service.impl;

import com.aparcar.api.config.PuntosProperties;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.puntos.FranjaPuntos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Las cuentas de la gamificacion, sin tocar la base: cuantos puntos da una
 * reserva, como avanza la racha y cuanto descuento dan los puntos. Las usan la
 * cotizacion, el canje al reservar y la acreditacion al ingresar.
 */
@Component
@RequiredArgsConstructor
public class ReglasPuntos {
    /** 100 puntos = 10 %: cada punto vale 0,1 %, asi que 10 puntos son 1 %. */
    public static final int PUNTOS_POR_PORCENTAJE = 10;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final PuntosProperties properties;

    /** Lo que sumaria una reserva al ingresar, o por que no sumaria. */
    public record Estimacion(int puntos, FranjaPuntos franja, String motivo) {
        public boolean suma() {
            return motivo == null;
        }
    }

    /** El descuento que dan unos puntos sobre un precio. */
    public record Descuento(int puntos, BigDecimal porcentaje, BigDecimal monto, BigDecimal precioFinal) {
    }

    /** Cuenta la hora de inicio de la reserva, que es la que el usuario elige. */
    public FranjaPuntos franja(LocalDateTime desde) {
        return FranjaPuntos.de(desde.toLocalTime());
    }

    /**
     * Puntos de franja de una reserva, solo con lo que se sabe al reservar. El
     * tope diario depende de cuantas otras reservas use ese dia, asi que se
     * controla recien al ingresar.
     */
    public Estimacion estimar(LocalDateTime desde, LocalDateTime hasta) {
        FranjaPuntos franja = franja(desde);
        if (Duration.between(desde, hasta).toMinutes() < properties.duracionMinimaMin()) {
            return new Estimacion(0, franja, "La reserva dura menos de %d minutos: no suma puntos."
                    .formatted(properties.duracionMinimaMin()));
        }
        return new Estimacion(franja.getPuntos(), franja, null);
    }

    public String motivoTopeDiario() {
        return "Ya sumaste puntos por %d reservas ese día: esta no suma.".formatted(properties.maxAcreditacionesDia());
    }

    public int maxAcreditacionesDia() {
        return properties.maxAcreditacionesDia();
    }

    public int duracionMinimaMin() {
        return properties.duracionMinimaMin();
    }

    // ---- Racha ----

    /**
     * Cuenta el dia del ingreso en la racha. El mismo dia no la cambia; el dia
     * siguiente al ultimo la suma; si se salteo algun dia, vuelve a empezar.
     *
     * @return true si la racha cambio (solo entonces puede corresponder bonus).
     */
    public boolean contarDia(BilleteraPuntos billetera, LocalDate dia) {
        LocalDate ultimo = billetera.getUltimoDiaRacha();
        if (ultimo != null && !dia.isAfter(ultimo)) {
            return false;
        }
        int racha = ultimo != null && dia.equals(ultimo.plusDays(1)) ? billetera.getRachaActual() + 1 : 1;
        billetera.setRachaActual(racha);
        billetera.setUltimoDiaRacha(dia);
        if (racha > billetera.getMejorRacha()) billetera.setMejorRacha(racha);
        return true;
    }

    /** True si la racha acaba de llegar a un multiplo de los dias del bonus. */
    public boolean tocaBonus(int racha) {
        return racha > 0 && racha % properties.rachaDias() == 0;
    }

    public int bonusRacha() {
        return properties.rachaBonus();
    }

    public int rachaDias() {
        return properties.rachaDias();
    }

    /** La racha sigue viva si el ultimo dia contado fue hoy o ayer; si no, hoy vale 0. */
    public int rachaVigente(BilleteraPuntos billetera, LocalDate hoy) {
        LocalDate ultimo = billetera.getUltimoDiaRacha();
        return ultimo != null && !ultimo.isBefore(hoy.minusDays(1)) ? billetera.getRachaActual() : 0;
    }

    /** Cuantos dias de racha faltan para el proximo bonus (nunca 0). */
    public int diasParaBonus(int rachaVigente) {
        int resto = rachaVigente % properties.rachaDias();
        return properties.rachaDias() - resto;
    }

    // ---- Canje ----

    public int descuentoMaxPct() {
        return properties.descuentoMaxPct();
    }

    /** Lo maximo que se puede canjear en una reserva: lo que llega al tope del descuento. */
    public int maxPuntosCanjeables() {
        return properties.descuentoMaxPct() * PUNTOS_POR_PORCENTAJE;
    }

    /** Relacion lineal, siempre con BigDecimal y redondeo HALF_UP a 2 decimales. */
    public Descuento descuento(BigDecimal subtotal, int puntos) {
        BigDecimal porcentaje = BigDecimal.valueOf(puntos)
                .divide(BigDecimal.valueOf(PUNTOS_POR_PORCENTAJE), 2, RoundingMode.HALF_UP);
        BigDecimal monto = subtotal.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
        return new Descuento(puntos, porcentaje, monto, subtotal.subtract(monto).setScale(2, RoundingMode.HALF_UP));
    }
}
