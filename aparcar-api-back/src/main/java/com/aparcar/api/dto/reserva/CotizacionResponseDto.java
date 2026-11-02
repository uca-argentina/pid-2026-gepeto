package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.CocheraTipo;
import java.math.BigDecimal;

/**
 * Precio de una franja. Sin puntos, {@code total} es el de siempre; con puntos,
 * {@code total} es lo que se paga y {@code subtotal} el precio de la tarifa.
 * Los campos de puntos son nuevos y opcionales: un cliente que no los mira
 * sigue funcionando igual.
 *
 * @param subtotal            Precio de la tarifa, antes del descuento.
 * @param descuentoPct        Porcentaje de descuento por los puntos aplicados.
 * @param descuento           Monto del descuento.
 * @param puntosAplicados     Puntos que se usarian.
 * @param saldoDisponible     Saldo de puntos de quien cotiza.
 * @param maxPuntosCanjeables Lo que lleva al tope de descuento, sin pasar del saldo.
 * @param puntosAGanar        Lo que sumaria al ingresar, por la franja de inicio.
 * @param motivoSinPuntos     Por que no sumaria puntos, si es el caso.
 */
public record CotizacionResponseDto(
        CocheraTipo tipo, BigDecimal total, String moneda,
        long jornadas, long mediasJornadas, long horas, long fracciones,
        BigDecimal subtotal, BigDecimal descuentoPct, BigDecimal descuento,
        Integer puntosAplicados, Integer saldoDisponible, Integer maxPuntosCanjeables,
        Integer puntosAGanar, String motivoSinPuntos,
        com.aparcar.api.dto.convenio.DescuentoConvenioDto convenio
) {
    /** La cotizacion de la tarifa, sin puntos: total y subtotal son el mismo. */
    public CotizacionResponseDto(CocheraTipo tipo, BigDecimal total, String moneda,
                                 long jornadas, long mediasJornadas, long horas, long fracciones) {
        this(tipo, total, moneda, jornadas, mediasJornadas, horas, fracciones,
                total, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), 0, null, null, null, null, null);
    }
}
