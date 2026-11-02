package com.aparcar.api.dto.convenio;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ConvenioRequestDto(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 100) String formatoCredencial,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("100")
        @Digits(integer = 3, fraction = 2) BigDecimal descuentoPct
) { }
