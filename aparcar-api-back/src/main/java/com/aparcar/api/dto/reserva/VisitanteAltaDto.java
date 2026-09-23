package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.VehiculoTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Alta operativa de un visitante desde el panel del admin: crea en un solo acto
 * la cuenta, su vehiculo y la reserva para la fecha elegida.
 *
 * <p>Los tres van juntos a proposito. Antes el frontend hacia dos llamadas
 * sueltas (crear visitante, crear vehiculo) y si la segunda fallaba quedaba un
 * visitante sin vehiculo ni reserva: justamente el fantasma que estamos
 * sacando del sistema.
 */
@Data
public class VisitanteAltaDto {
    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    // Ademas de identificar a la persona, es la contraseña inicial de la cuenta.
    @NotBlank(message = "El documento es obligatorio")
    private String documento;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String email;

    private String telefono;

    // Acepta formato argentino viejo (AAA000) y Mercosur (AA000AA)
    @NotBlank(message = "La patente es obligatoria")
    @Pattern(
            regexp = "^([A-Za-z]{3}[0-9]{3}|[A-Za-z]{2}[0-9]{3}[A-Za-z]{2})$",
            message = "La patente debe tener formato AAA000 o AA000AA"
    )
    private String patente;

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private VehiculoTipo tipoVehiculo;

    @NotNull(message = "La cochera es obligatoria")
    private UUID cocheraId;

    // Si se omite, se conserva el alta para hoy de los clientes existentes.
    @FutureOrPresent(message = "La fecha no puede ser anterior a hoy")
    private LocalDate fecha;
}
