package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.ReservaEstado;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReservaResponseDto(
        UUID id,
        LocalDate fecha,
        VisitanteResponseDto visitante,
        VehiculoResponseDto vehiculo,
        CocheraResponseDto cochera,
        ReservaEstado estado,
        Instant fechaCreacion
) {
}
