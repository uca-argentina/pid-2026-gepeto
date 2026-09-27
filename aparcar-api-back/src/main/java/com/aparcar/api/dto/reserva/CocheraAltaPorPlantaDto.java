package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.util.Map;

/**
 * Alta de varias cocheras en una misma planta/sector, indicando solo cuantas
 * de cada tipo. Los numeros los genera el backend (ver
 * {@link com.aparcar.api.service.impl.NumeracionCocheras}).
 *
 * <p>Ejemplo:
 * <pre>
 * {
 *   "sector": "Planta Baja",
 *   "cantidades": { "AUTO": 10, "MOTO": 4, "ACCESIBLE": 2 },
 *   "estado": "HABILITADA"
 * }
 * </pre>
 */
@Data
public class CocheraAltaPorPlantaDto {
    @NotBlank(message = "El sector es obligatorio")
    private String sector;

    /**
     * Cantidad a crear por tipo. Un tipo ausente, en null o en 0 no se crea;
     * tiene que haber al menos uno mayor a 0 (eso lo valida el servicio).
     */
    @NotNull(message = "Indica cuantas cocheras crear de cada tipo")
    private Map<CocheraTipo, @PositiveOrZero(message = "Las cantidades no pueden ser negativas") Integer> cantidades;

    /** Estado inicial de todas las cocheras creadas. Si no viene: HABILITADA. */
    private CocheraEstado estado;
}