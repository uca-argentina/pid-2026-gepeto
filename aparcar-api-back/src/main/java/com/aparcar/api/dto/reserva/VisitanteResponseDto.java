package com.aparcar.api.dto.reserva;

import java.util.UUID;

public record VisitanteResponseDto(
        UUID id,
        String nombre,
        String documento,
        String telefono,
        String email
) {
}
