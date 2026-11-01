package com.aparcar.api.dto.sancion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Los números del régimen de faltas, editables por el administrador. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionSancionesDto {

    @NotNull @Min(1) @Max(50)
    private Integer maxFaltas;

    @NotNull @Min(1) @Max(3650)
    private Integer diasBloqueo;

    /** Meses en los que una falta todavia cuenta para el bloqueo. */
    @NotNull @Min(1) @Max(120)
    private Integer mesesVentana;

    @NotNull @Min(0) @Max(1440)
    private Integer toleranciaIngresoMin;

    @NotNull @Min(0) @Max(1440)
    private Integer toleranciaEgresoMin;
}
