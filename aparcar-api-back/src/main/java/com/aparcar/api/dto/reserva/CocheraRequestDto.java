package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CocheraRequestDto {
    @NotBlank(message = "El numero es obligatorio")
    private String numero;

    @NotBlank(message = "El sector es obligatorio")
    private String sector;

    @NotNull(message = "El tipo de cochera es obligatorio")
    private CocheraTipo tipo;

    @NotNull(message = "El estado de la cochera es obligatorio")
    private CocheraEstado estado;
}
