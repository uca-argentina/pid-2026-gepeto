package com.aparcar.api.dto.reserva;

import java.time.LocalDateTime;

/**
 * El acceso de una reserva, tal como lo ve su dueño o un ADMIN.
 *
 * @param codigo       Codigo corto sin guion (el QR lleva {@code APARCAR:} + este valor). Solo viene
 *                     mientras sirve: en reservas CONFIRMADA o EN_CURSO.
 * @param ingresoDesde Apertura de la ventana de ingreso.
 * @param ingresoHasta Cierre de la ventana de ingreso.
 */
public record ReservaAccesoDto(
        String codigo,
        LocalDateTime ingresoDesde,
        LocalDateTime ingresoHasta,
        LocalDateTime ingresoAt,
        LocalDateTime egresoAt,
        Integer minutosExceso
) {
}
