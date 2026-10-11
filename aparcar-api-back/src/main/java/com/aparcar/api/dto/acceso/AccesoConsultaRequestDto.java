package com.aparcar.api.dto.acceso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lo escaneado o tipeado: con o sin el prefijo del QR, guiones o espacios. */
public record AccesoConsultaRequestDto(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 64, message = "El código es demasiado largo")
        String codigo
) {
}
