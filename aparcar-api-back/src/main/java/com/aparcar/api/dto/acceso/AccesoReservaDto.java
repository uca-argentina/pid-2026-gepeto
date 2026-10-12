package com.aparcar.api.dto.acceso;

import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.VehiculoTipo;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lo que el operador necesita ver en la puerta. Del visitante va solo el
 * nombre: el codigo no expone email, documento ni telefono.
 *
 * @param ingresoDesde  Apertura de la ventana de ingreso.
 * @param ingresoHasta  Cierre de la ventana de ingreso (sin pasar de {@code hasta}).
 * @param minutosExceso Para una reserva FINALIZADA, el exceso registrado; para una EN_CURSO, el
 *                      que lleva hasta ahora si ya paso la gracia. Null si no hay exceso.
 */
public record AccesoReservaDto(
        UUID id,
        ReservaEstado estado,
        LocalDateTime desde,
        LocalDateTime hasta,
        String visitanteNombre,
        String patente,
        VehiculoTipo tipoVehiculo,
        String cocheraNumero,
        String cocheraSector,
        CocheraTipo cocheraTipo,
        LocalDateTime ingresoDesde,
        LocalDateTime ingresoHasta,
        LocalDateTime ingresoAt,
        LocalDateTime egresoAt,
        Integer minutosExceso
) {
}
