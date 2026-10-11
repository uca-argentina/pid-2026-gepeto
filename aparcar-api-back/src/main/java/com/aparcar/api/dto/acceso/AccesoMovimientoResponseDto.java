package com.aparcar.api.dto.acceso;

import com.aparcar.api.entity.reserva.VehiculoTipo;

import java.time.Instant;
import java.util.UUID;

/** Un ingreso o un egreso registrado, para el listado del dia. */
public record AccesoMovimientoResponseDto(
        UUID id,
        AccesoAccion accion,
        Instant fecha,
        UUID reservaId,
        String visitanteNombre,
        String patente,
        VehiculoTipo tipoVehiculo,
        String cocheraNumero,
        String cocheraSector,
        String operador,
        /** Solo en egresos excedidos. */
        Integer minutosExceso
) {
}
