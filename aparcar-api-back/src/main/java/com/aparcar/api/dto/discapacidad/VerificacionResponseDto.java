package com.aparcar.api.dto.discapacidad;

import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una solicitud de verificacion. Sin datos medicos: del certificado solo van
 * nombre, tipo y tamaño, para poder pedirlo por el endpoint autenticado.
 *
 * @param motivo      Motivo del rechazo o de la revocacion, en español, visible para el visitante.
 * @param solicitante Solo para administradores.
 * @param resueltaPor Solo para administradores.
 * @param revocadaPor Solo para administradores.
 */
public record VerificacionResponseDto(
        UUID id,
        VerificacionEstado estado,
        Instant fechaCreacion,
        Instant resueltaAt,
        Instant revocadaAt,
        String motivo,
        LocalDate venceEl,
        boolean documentoDisponible,
        String documentoNombre,
        String documentoTipo,
        long documentoTamano,
        @JsonInclude(JsonInclude.Include.NON_NULL) SolicitanteDto solicitante,
        @JsonInclude(JsonInclude.Include.NON_NULL) String resueltaPor,
        @JsonInclude(JsonInclude.Include.NON_NULL) String revocadaPor
) {
    /** Datos minimos de quien pidio la verificacion. */
    public record SolicitanteDto(UUID id, String nombre, String email) {
    }
}
