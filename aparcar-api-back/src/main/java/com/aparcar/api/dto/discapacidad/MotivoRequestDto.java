package com.aparcar.api.dto.discapacidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo de un rechazo o de una revocacion: lo lee el visitante. */
public record MotivoRequestDto(
        @NotBlank(message = "El motivo es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo
) {
}
