package com.aparcar.api.dto.acceso;

/**
 * Resultado de una consulta, sin efectos sobre la reserva.
 *
 * @param codigo  El codigo normalizado, como {@code XXXXX-XXXXX}.
 * @param valido  True si ahora mismo se puede registrar {@code accion}.
 * @param accion  La que corresponde por el estado (CONFIRMADA → INGRESO, EN_CURSO → EGRESO), o null
 *                si el estado no admite ninguna.
 * @param motivo  Por que no se puede, o null si es valido.
 * @param mensaje Texto listo para mostrar.
 */
public record AccesoConsultaResponseDto(
        String codigo,
        boolean valido,
        AccesoAccion accion,
        AccesoMotivo motivo,
        String mensaje,
        AccesoReservaDto reserva
) {
}
