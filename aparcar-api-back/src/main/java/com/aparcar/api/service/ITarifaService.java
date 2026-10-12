package com.aparcar.api.service;

import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.dto.reserva.TarifaDto;
import com.aparcar.api.dto.reserva.TarifasRequestDto;
import com.aparcar.api.entity.reserva.CocheraTipo;
import java.time.LocalDateTime;
import java.util.List;

public interface ITarifaService {
    List<TarifaDto> listar();
    List<TarifaDto> actualizar(TarifasRequestDto dto);
    CotizacionResponseDto cotizar(CocheraTipo tipo, LocalDateTime desde, LocalDateTime hasta);
}
