package com.aparcar.api.dto.reserva;

import com.aparcar.api.dto.convenio.CredencialConvenioDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.entity.reserva.CocheraTipo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

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
    @Size(max = 100)
    private List<@Valid @NotNull CredencialConvenioDto> credencialesConvenio;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    // Ademas de identificar a la persona, es la contraseña inicial de la cuenta.
    @NotBlank(message = "El documento es obligatorio")
    private String documento;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    private String email;

    private String telefono;

    // El formato depende de tipoVehiculo y se valida en VehiculoService.
    @NotBlank(message = "La patente es obligatoria")
    private String patente;

    @NotNull(message = "El tipo de vehiculo es obligatorio")
    private VehiculoTipo tipoVehiculo;

    private UUID cocheraId;

    private boolean listaEspera;

    private CocheraTipo tipoCochera;

    // Si se omite, se conserva el alta para hoy de los clientes existentes.
    /**
     * Franja de la reserva que se crea junto con el alta. Si no vienen, el
     * servicio usa "desde ahora y por una hora".
     */
    private LocalDateTime desde;

    private LocalDateTime hasta;

    private BigDecimal precioEsperado;
}
