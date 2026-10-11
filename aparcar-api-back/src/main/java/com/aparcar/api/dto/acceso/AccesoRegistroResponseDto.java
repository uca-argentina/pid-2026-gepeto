package com.aparcar.api.dto.acceso;

import java.time.LocalDateTime;

/**
 * @param yaRegistrado True si la misma accion ya se habia registrado hace instantes (doble clic,
 *                     reintento): no se registro de nuevo y se devuelve lo que ya estaba.
 * @param fecha        Cuando quedo registrada la accion.
 * @param excedido     Solo en el egreso: si salio despues de {@code hasta} + la gracia.
 */
public record AccesoRegistroResponseDto(
        AccesoAccion accion,
        boolean yaRegistrado,
        LocalDateTime fecha,
        boolean excedido,
        Integer minutosExceso,
        String mensaje,
        AccesoReservaDto reserva
) {
}
