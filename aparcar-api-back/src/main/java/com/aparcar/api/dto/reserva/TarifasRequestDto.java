package com.aparcar.api.dto.reserva;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TarifasRequestDto(
        @NotNull @Size(min = 4, max = 4) List<@NotNull @Valid TarifaDto> tarifas
) {
}
