package com.aparcar.api.dto.puntos;

import java.time.LocalDate;
import java.util.List;

/**
 * La billetera de puntos de quien pide, con todo lo necesario para explicar
 * como se ganan y se gastan.
 *
 * @param rachaActual        Dias seguidos con ingreso; 0 si ya se corto (el ultimo dia no fue hoy ni ayer).
 * @param diasParaBonus      Dias de racha que faltan para el proximo bonus.
 * @param puntosPorPorcentaje Puntos que valen 1 % de descuento (100 puntos = 10 %).
 * @param maxPuntosCanjeables Puntos que llevan al tope de descuento en una reserva.
 */
public record BilleteraResponseDto(
        int saldo,
        int rachaActual,
        int mejorRacha,
        LocalDate ultimoDiaRacha,
        int diasParaBonus,
        int rachaDias,
        int rachaBonus,
        int descuentoMaxPct,
        int puntosPorPorcentaje,
        int maxPuntosCanjeables,
        int duracionMinimaMin,
        int maxAcreditacionesDia,
        List<FranjaDto> franjas
) {
    /** Una franja horaria con sus puntos; {@code inicio} incluido, {@code fin} excluido. */
    public record FranjaDto(String clave, String nombre, String inicio, String fin, int puntos) {
    }
}
