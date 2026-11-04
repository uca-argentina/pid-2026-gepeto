package com.aparcar.api.dto.sancion;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** Una falta, como se ve en el historial. */
@Data
@AllArgsConstructor
public class AmonestacionResponseDto {
    private UUID id;
    private LocalDateTime fecha;
    private String origen;
    private String motivo;
    private String patente;
    /** Null cuando la aplicó el sistema. */
    private String autorNombre;
    private boolean automatica;
    /** Si todavía cuenta para el bloqueo, o ya quedó fuera de la ventana. */
    private boolean vigente;
}
