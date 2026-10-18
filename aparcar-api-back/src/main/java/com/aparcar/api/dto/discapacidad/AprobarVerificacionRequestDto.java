package com.aparcar.api.dto.discapacidad;

import java.time.LocalDate;

/** @param venceEl Opcional: hasta que dia (inclusive) vale el certificado. */
public record AprobarVerificacionRequestDto(LocalDate venceEl) {
}
