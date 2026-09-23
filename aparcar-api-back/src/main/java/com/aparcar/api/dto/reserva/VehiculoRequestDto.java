package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.VehiculoTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class VehiculoRequestDto {
    // El formato exacto depende del tipo de vehiculo (auto/carga vs. moto usan
    // esquemas distintos), asi que la validacion de formato vive en
    // VehiculoService, no acá con una unica regex.
    @NotBlank(message = "La patente es obligatoria")
    private String patente;

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private VehiculoTipo tipo;

    /**
     * De quien es el vehiculo. Solo lo puede mandar un ADMIN: si quien lo carga
     * es un USER, el backend ignora este campo y usa su propia cuenta.
     */
    private UUID visitanteId;
}