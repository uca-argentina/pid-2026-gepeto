package com.aparcar.api.dto.reserva;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Datos que un visitante puede cambiar de si mismo. No incluye documento ni
 * nombre: esos identifican a la persona y solo los toca un admin.
 */
@Data
public class VisitanteUpdateDto {
    private String telefono;

    // Es el identificador de login, asi que no puede quedar vacio.
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String email;
}
