package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.dto.reserva.TarifaDto;
import com.aparcar.api.dto.reserva.TarifasRequestDto;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Tarifa;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.TarifaRepository;
import com.aparcar.api.service.ITarifaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TarifaService implements ITarifaService {
    private final TarifaRepository tarifaRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TarifaDto> listar() {
        Map<CocheraTipo, Tarifa> tarifas = new EnumMap<>(CocheraTipo.class);
        tarifaRepository.findAll().forEach(t -> tarifas.put(t.getTipo(), t));
        return Arrays.stream(CocheraTipo.values()).map(tipo -> {
            Tarifa t = tarifas.get(tipo);
            return t == null ? new TarifaDto(tipo, null, null, null, null, null) : toDto(t);
        }).toList();
    }

    @Override
    @Transactional
    public List<TarifaDto> actualizar(TarifasRequestDto dto) {
        Set<CocheraTipo> tipos = EnumSet.noneOf(CocheraTipo.class);
        for (TarifaDto fila : dto.tarifas()) {
            if (!tipos.add(fila.tipo())) {
                throw new ValidationException("Cada tipo debe aparecer una sola vez.");
            }
        }
        if (tipos.size() != CocheraTipo.values().length) {
            throw new ValidationException("Completá las tarifas de Auto, Moto, Accesible y Carga.");
        }
        for (TarifaDto fila : dto.tarifas()) {
            Tarifa tarifa = tarifaRepository.findById(fila.tipo()).orElseGet(Tarifa::new);
            if (!Objects.equals(tarifa.getVersion(), fila.version())) {
                throw new ValidationException("Las tarifas cambiaron. Recargá los precios antes de guardar.");
            }
            tarifa.setTipo(fila.tipo());
            tarifa.setHora(fila.hora());
            tarifa.setFraccion(fila.fraccion());
            tarifa.setMediaJornada(fila.mediaJornada());
            tarifa.setJornadaCompleta(fila.jornadaCompleta());
            tarifaRepository.save(tarifa);
        }
        tarifaRepository.flush();
        return listar();
    }

    @Override
    @Transactional(readOnly = true)
    public CotizacionResponseDto cotizar(CocheraTipo tipo, LocalDateTime desde, LocalDateTime hasta) {
        if (tipo == null || desde == null || hasta == null || !hasta.isAfter(desde)) {
            throw new ValidationException("Indicá el tipo y una franja con fin posterior al inicio.");
        }
        if (!hasta.isAfter(LocalDateTime.now())) {
            throw new ValidationException("La reserva no puede terminar en el pasado.");
        }
        if (!esBloque(desde) || !esBloque(hasta)) {
            throw new ValidationException("La franja tiene que caer en bloques de 15 minutos.");
        }
        Tarifa tarifa = tarifaRepository.findById(tipo).orElseThrow(() ->
                new ValidationException("Todavía no hay tarifas configuradas para " + tipo + ". Contactá al administrador."));
        long minutos = Duration.between(desde, hasta).toMinutes();
        // Misma duración que los atajos existentes: media jornada 12 h, jornada 24 h.
        long jornadas = minutos / 1440;
        long medias = minutos % 1440 / 720;
        long horas = minutos % 720 / 60;
        long fracciones = minutos % 60 / 15;
        BigDecimal total = tarifa.getJornadaCompleta().multiply(BigDecimal.valueOf(jornadas))
                .add(tarifa.getMediaJornada().multiply(BigDecimal.valueOf(medias)))
                .add(tarifa.getHora().multiply(BigDecimal.valueOf(horas)))
                .add(tarifa.getFraccion().multiply(BigDecimal.valueOf(fracciones))).setScale(2);
        if (total.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw new ValidationException("El importe supera el máximo permitido. Reducí la duración.");
        }
        return new CotizacionResponseDto(tipo, total, "ARS", jornadas, medias, horas, fracciones);
    }

    private boolean esBloque(LocalDateTime fecha) {
        return fecha.getMinute() % 15 == 0 && fecha.getSecond() == 0 && fecha.getNano() == 0;
    }

    private TarifaDto toDto(Tarifa t) {
        return new TarifaDto(t.getTipo(), t.getHora(), t.getFraccion(),
                t.getMediaJornada(), t.getJornadaCompleta(), t.getVersion());
    }
}
