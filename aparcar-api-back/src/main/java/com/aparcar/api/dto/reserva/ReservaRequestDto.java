package com.aparcar.api.dto.reserva;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
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

    /**
     * Inicio de la franja. No lleva @FutureOrPresent a proposito: el formulario
     * lo arranca en "ahora", y entre que se abre y se envia pasan segundos que
     * lo dejarian en el pasado por unos instantes. Ademas el admin necesita
     * poder registrar a alguien que ya entro hace un rato. Lo que si se exige
     * (en el servicio) es que la franja no este enteramente vencida.
     */
    @NotNull(message = "El inicio de la reserva es obligatorio")
    private LocalDateTime desde;

    @NotNull(message = "El fin de la reserva es obligatorio")
    private LocalDateTime hasta;
}
