package com.aparcar.api.dto.auth;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Cambio de contraseña hecho por el propio visitante desde su perfil.
 *
 * <p>Pide la contraseña actual a proposito: sin eso, cualquiera que agarre una
 * sesion abierta podria dejar al dueño afuera de su cuenta.
 */
@Data
public class ChangePasswordDto {
    @NotEmpty(message = "La contraseña actual es obligatoria")
    private String passwordActual;

    @NotEmpty(message = "La contraseña nueva es obligatoria")
    @Size(min = 8, message = "La contraseña nueva debe tener al menos 8 caracteres")
    @Size(max = 100, message = "La contraseña nueva no puede superar los 100 caracteres")
    private String passwordNueva;
}
