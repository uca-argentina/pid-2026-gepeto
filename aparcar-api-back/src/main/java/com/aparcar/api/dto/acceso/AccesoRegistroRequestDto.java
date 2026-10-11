package com.aparcar.api.dto.acceso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * La accion la confirma el operador, pero el servidor la vuelve a validar
 * entera: nunca se confia en una consulta previa.
 */
public record AccesoRegistroRequestDto(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 64, message = "El código es demasiado largo")
        String codigo,
        @NotNull(message = "La acción es obligatoria")
        AccesoAccion accion
) {
}
