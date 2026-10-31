package com.aparcar.api.dto.puntos;

import com.aparcar.api.entity.puntos.TipoMovimientoPuntos;

import java.time.Instant;
import java.util.UUID;

/** Un movimiento del libro de puntos; {@code puntos} con signo. */
public record MovimientoPuntosResponseDto(
        UUID id,
        TipoMovimientoPuntos tipo,
        int puntos,
        UUID reservaId,
        String descripcion,
        Instant fecha
) {
}
