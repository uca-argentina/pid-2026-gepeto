package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.VehiculoTipo;

import java.util.UUID;

public record VehiculoResponseDto(
        UUID id,
        String patente,
        VehiculoTipo tipo,
        UUID visitanteId
) {
}
