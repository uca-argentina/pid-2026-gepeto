package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.reserva.ReservaAccion;

import java.time.Instant;
import java.util.UUID;

public record ReservaMovimientoResponseDto(
        UUID id,
        ReservaAccion accion,
        Instant fecha,
        UUID actorId,
        String actorNombre,
        String actorEmail,
        AppAuthority actorRol
) {
}
