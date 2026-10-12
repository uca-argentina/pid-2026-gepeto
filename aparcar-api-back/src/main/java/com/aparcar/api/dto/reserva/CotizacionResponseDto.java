package com.aparcar.api.dto.reserva;

import com.aparcar.api.entity.reserva.CocheraTipo;
import java.math.BigDecimal;

public record CotizacionResponseDto(
        CocheraTipo tipo, BigDecimal total, String moneda,
        long jornadas, long mediasJornadas, long horas, long fracciones
) {
}
