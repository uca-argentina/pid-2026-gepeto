package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.CocheraTipo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TarifaDto(
        @NotNull CocheraTipo tipo,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal hora,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal fraccion,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal mediaJornada,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal jornadaCompleta,
        Long version
) {
}
