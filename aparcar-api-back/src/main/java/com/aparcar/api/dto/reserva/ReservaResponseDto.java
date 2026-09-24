package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.ReservaEstado;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservaResponseDto(
        UUID id,
        LocalDateTime desde,
        LocalDateTime hasta,
        VisitanteResponseDto visitante,
        VehiculoResponseDto vehiculo,
        CocheraResponseDto cochera,
        ReservaEstado estado,
        Instant fechaCreacion
) {
}
