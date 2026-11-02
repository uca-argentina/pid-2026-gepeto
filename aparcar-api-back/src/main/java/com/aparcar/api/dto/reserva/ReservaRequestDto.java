package com.aparcar.api.dto.reserva;

import com.aparcar.api.dto.convenio.CredencialConvenioDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.aparcar.api.entity.reserva.CocheraTipo;
import lombok.Data;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

@Data
public class ReservaRequestDto {
    @Size(max = 100)
    private List<@Valid @NotNull CredencialConvenioDto> credencialesConvenio;

    /**
     * A nombre de quien va la reserva. Solo lo puede mandar un ADMIN: si quien
     * reserva es un USER, el backend ignora este campo y usa su propia cuenta,
     * porque un visitante solo puede reservar a su nombre.
     */
    private UUID visitanteId;

    @NotNull(message = "El vehiculo es obligatorio")
    private UUID vehiculoId;

    private UUID cocheraId;

    /** Consentimiento expreso para esperar si no queda lugar en esta categoria. */
    private boolean listaEspera;

    private CocheraTipo tipoCochera;

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

    /** Solo se compara: el servidor calcula siempre el importe definitivo. */
    private BigDecimal precioEsperado;

    /**
     * Puntos a canjear como descuento: un entero positivo. Llega como numero
     * decimal para poder rechazar un valor con decimales en vez de truncarlo
     * en silencio. El cliente nunca manda un precio ni un porcentaje.
     */
    private BigDecimal puntosAUsar;

    /** Usar todos los puntos que se puedan: el minimo entre el saldo y el tope. */
    private boolean usarTodosLosPuntos;
}
