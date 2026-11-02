package com.aparcar.api.dto.reserva;

import com.aparcar.api.dto.convenio.CredencialConvenioDto;
import com.aparcar.api.entity.reserva.CocheraTipo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CotizacionRequestDto(@NotNull CocheraTipo tipo, @NotNull LocalDateTime desde,
        @NotNull LocalDateTime hasta, BigDecimal puntos, Boolean usarTodos, UUID visitanteId,
        @Size(max = 100) String documento,
        @Size(max = 100) List<@Valid @NotNull CredencialConvenioDto> credencialesConvenio) {
    public CotizacionRequestDto {
        usarTodos = Boolean.TRUE.equals(usarTodos);
    }
}
