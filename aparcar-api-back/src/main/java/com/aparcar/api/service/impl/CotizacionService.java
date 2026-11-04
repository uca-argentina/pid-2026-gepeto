package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.CotizacionRequestDto;
import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.service.IPuntosService;
import com.aparcar.api.service.ITarifaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CotizacionService {
    private final ITarifaService tarifas;
    private final IPuntosService puntos;
    private final ConvenioService convenios;

    @Transactional(readOnly = true)
    public CotizacionResponseDto cotizar(CotizacionRequestDto dto, String email, boolean personal) {
        if (personal && (dto.puntos() != null || dto.usarTodos())) {
            throw new ValidationException("Los puntos solo se usan en una reserva propia con cochera asignada.");
        }
        var titular = convenios.resolverTitular(email, personal, dto.visitanteId(), dto.documento());
        var tarifa = tarifas.cotizar(dto.tipo(), dto.desde(), dto.hasta());
        var convenio = convenios.mejorDescuento(titular, dto.credencialesConvenio(), tarifa.total());
        var cotizacion = puntos.cotizar(tarifa, personal ? null : titular.getEmail(),
                dto.desde(), dto.hasta(), dto.puntos(), dto.usarTodos());
        if (convenio == null || cotizacion.descuentoPct().compareTo(convenio.descuentoPct()) > 0) return cotizacion;
        return new CotizacionResponseDto(cotizacion.tipo(), tarifa.total().subtract(convenio.descuentoMonto()),
                cotizacion.moneda(), cotizacion.jornadas(), cotizacion.mediasJornadas(), cotizacion.horas(),
                cotizacion.fracciones(), tarifa.total(), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                0, cotizacion.saldoDisponible(), cotizacion.maxPuntosCanjeables(), cotizacion.puntosAGanar(),
                cotizacion.motivoSinPuntos(), convenio);
    }
}
