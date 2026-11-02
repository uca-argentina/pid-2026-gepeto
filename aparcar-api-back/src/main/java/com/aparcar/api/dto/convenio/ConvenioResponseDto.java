package com.aparcar.api.dto.convenio;

import java.math.BigDecimal;
import java.util.UUID;

public record ConvenioResponseDto(UUID id, String nombre, String formatoCredencial, BigDecimal descuentoPct) { }
