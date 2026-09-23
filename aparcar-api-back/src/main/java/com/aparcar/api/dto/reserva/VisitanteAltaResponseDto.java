package com.aparcar.api.dto.reserva;

/**
 * Resultado del alta operativa: la cuenta creada junto con el vehiculo y la
 * reserva del dia que se generaron en la misma transaccion.
 */
public record VisitanteAltaResponseDto(
        VisitanteResponseDto visitante,
        VehiculoResponseDto vehiculo,
        ReservaResponseDto reserva
) {
}
