package com.aparcar.api.dto.reserva;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

@Getter
@Setter
public class ReservaRecurrenteRequestDto extends ReservaRequestDto {
    @NotEmpty(message = "Selecciona al menos un dia de la semana")
    private Set<@NotNull @Min(1) @Max(7) Integer> diasSemana;

    @NotNull(message = "La fecha final de repeticion es obligatoria")
    private LocalDate repetirHasta;
}
