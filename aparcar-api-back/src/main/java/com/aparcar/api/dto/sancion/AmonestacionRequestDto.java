package com.aparcar.api.dto.sancion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** Lo que manda el panel al aplicar una falta desde una reserva. */
@Data
public class AmonestacionRequestDto {

    @NotNull(message = "Falta indicar la reserva.")
    private UUID reservaId;

    @NotBlank(message = "Hay que indicar la causa de la amonestación.")
    @Size(max = 500, message = "La causa no puede superar los 500 caracteres.")
    private String motivo;
}
