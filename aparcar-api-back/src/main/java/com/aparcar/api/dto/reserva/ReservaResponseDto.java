package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.ModalidadReserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.ReservaMotivoCancelacion;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public record ReservaResponseDto(
        UUID id,
        LocalDateTime desde,
        LocalDateTime hasta,
        VisitanteResponseDto visitante,
        VehiculoResponseDto vehiculo,
        CocheraResponseDto cochera,
        ReservaEstado estado,
        /** Deducida de la duracion; no se guarda en la base. */
        ModalidadReserva modalidad,
        Instant fechaCreacion,
        BigDecimal precioTotal,
        CocheraTipo tipoTarifa,
        ReservaMotivoCancelacion motivoCancelacion,
        /** Solo se serializa para administradores. */
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ReservaMovimientoResponseDto> historial
) {
}
