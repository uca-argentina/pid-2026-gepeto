package com.aparcar.api.dto.convenio;

import java.math.BigDecimal;
import java.util.UUID;

/** Copia del convenio aplicado: no depende del catalogo actual. */
public record DescuentoConvenioDto(UUID convenioId, String nombre, BigDecimal descuentoPct,
                                   BigDecimal descuentoMonto) { }
