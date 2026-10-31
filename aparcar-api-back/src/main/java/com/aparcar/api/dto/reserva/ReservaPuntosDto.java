package com.aparcar.api.dto.reserva;

import java.math.BigDecimal;

/**
 * Los puntos de una reserva, como los ve su dueño o un ADMIN.
 *
 * @param puntosUsados       Canjeados al crearla (0 si ninguno).
 * @param precioFinal        Lo que se paga con el descuento; null si no hubo descuento.
 * @param reintegrados       Si se devolvieron los puntos usados (se cancelo antes de usarla).
 * @param puntosAcreditados  Los que sumo al ingresar; null mientras no se registre el ingreso.
 * @param puntosAGanar       Antes del ingreso: los que sumaria por su franja.
 * @param motivoSinPuntos    Por que no suma (o no sumo) puntos de franja, si es el caso.
 */
public record ReservaPuntosDto(
        int puntosUsados,
        BigDecimal descuentoPct,
        BigDecimal descuentoMonto,
        BigDecimal precioFinal,
        boolean reintegrados,
        Integer puntosAcreditados,
        int puntosAGanar,
        String motivoSinPuntos
) {
}
