package com.aparcar.api.dto.convenio;

import java.math.BigDecimal;
import java.util.UUID;

public record CredencialResponseDto(UUID convenioId, String nombre, String formatoCredencial,
                                    BigDecimal descuentoPct, String credencial) { }
