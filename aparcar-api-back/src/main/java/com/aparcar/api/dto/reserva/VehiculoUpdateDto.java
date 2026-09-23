package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.VehiculoTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class VehiculoUpdateDto {
    @NotBlank(message = "La patente es obligatoria")
    private String patente;

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private VehiculoTipo tipo;
}