package com.aparcar.api.dto.reserva;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReservaRequestDto {
    /**
     * A nombre de quien va la reserva. Solo lo puede mandar un ADMIN: si quien
     * reserva es un USER, el backend ignora este campo y usa su propia cuenta,
     * porque un visitante solo puede reservar a su nombre.
     */
    private UUID visitanteId;

    @NotNull(message = "El vehiculo es obligatorio")
    private UUID vehiculoId;

    @NotNull(message = "La cochera es obligatoria")
    private UUID cocheraId;

    @NotNull(message = "La fecha es obligatoria")
    @FutureOrPresent(message = "La fecha no puede ser anterior a hoy")
    private LocalDate fecha;
}
