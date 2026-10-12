package com.aparcar.api.dto.reserva;

import java.util.UUID;

public record VisitanteResponseDto(
        UUID id,
        String nombre,
        String documento,
        String telefono,
        String email,
        // Si puede usar cocheras ACCESIBLE. Nunca null: las cuentas nacen en false.
        Boolean tieneDiscapacidad,
        String nombreEstacionamiento
) {
    public VisitanteResponseDto(UUID id, String nombre, String documento, String telefono,
                                String email, Boolean tieneDiscapacidad) {
        this(id, nombre, documento, telefono, email, tieneDiscapacidad, null);
    }
}
