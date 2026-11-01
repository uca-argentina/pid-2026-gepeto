package com.aparcar.api.controller;

import com.aparcar.api.dto.sancion.AmonestacionRequestDto;
import com.aparcar.api.dto.sancion.AmonestacionResponseDto;
import com.aparcar.api.dto.sancion.ConfiguracionSancionesDto;
import com.aparcar.api.dto.sancion.ResumenAmonestacionesDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.sancion.Amonestacion;
import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IAmonestacionService;
import com.aparcar.api.service.impl.ConfiguracionSancionesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * El régimen de faltas.
 *
 * <p>Quién puede qué lo decide la configuración de seguridad, no este archivo:
 * aplicar una falta y consultar las de otro son del personal (ADMIN y
 * EMPLEADO), restituir una cuenta y cambiar los números son solo del ADMIN, y
 * {@code /mias} lo abre cualquiera sobre lo suyo.
 */
@RestController
@RequestMapping("/api/v1/amonestaciones")
@RequiredArgsConstructor
public class AmonestacionController {

    private final IAmonestacionService amonestaciones;
    private final ConfiguracionSancionesService configuracion;
    private final VisitanteRepository visitanteRepository;

    /** Aplica una falta a partir de una reserva. */
    @PostMapping
    public ResponseEntity<AmonestacionResponseDto> aplicar(
            @Valid @RequestBody AmonestacionRequestDto dto, Authentication authentication) {
        Amonestacion creada = amonestaciones.aplicarManual(
                dto.getReservaId(), dto.getMotivo(), authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(aDto(creada, LocalDateTime.now()));
    }

    /** Las faltas y el historial de quien pide. */
    @GetMapping("/mias")
    public ResponseEntity<ResumenAmonestacionesDto> mias(Authentication authentication) {
        Visitante visitante = visitanteRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada."));
        return ResponseEntity.ok(resumenDe(visitante));
    }

    /** Lo mismo, para una cuenta ajena. Lo usa el panel de cuentas. */
    @GetMapping("/visitante/{id}")
    public ResponseEntity<ResumenAmonestacionesDto> deVisitante(@PathVariable UUID id) {
        Visitante visitante = visitanteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada."));
        return ResponseEntity.ok(resumenDe(visitante));
    }

    /** Levanta el bloqueo sin borrar las faltas. */
    @PostMapping("/visitante/{id}/restituir")
    public ResponseEntity<ResumenAmonestacionesDto> restituir(@PathVariable UUID id) {
        amonestaciones.restituir(id);
        Visitante visitante = visitanteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada."));
        return ResponseEntity.ok(resumenDe(visitante));
    }

    @GetMapping("/configuracion")
    public ResponseEntity<ConfiguracionSancionesDto> verConfiguracion() {
        return ResponseEntity.ok(aDto(configuracion.obtener()));
    }

    @PutMapping("/configuracion")
    public ResponseEntity<ConfiguracionSancionesDto> guardarConfiguracion(
            @Valid @RequestBody ConfiguracionSancionesDto dto) {
        return ResponseEntity.ok(aDto(configuracion.guardar(
                dto.getMaxFaltas(), dto.getDiasBloqueo(), dto.getMesesVentana(),
                dto.getToleranciaIngresoMin(), dto.getToleranciaEgresoMin())));
    }

    private ResumenAmonestacionesDto resumenDe(Visitante visitante) {
        LocalDateTime ahora = LocalDateTime.now();
        ConfiguracionSanciones config = configuracion.obtener();
        LocalDateTime corte = ahora.minusMonths(config.getMesesVentana());

        List<AmonestacionResponseDto> historial = amonestaciones.historial(visitante.getId())
                .stream()
                .map(a -> aDto(a, corte))
                .toList();

        return new ResumenAmonestacionesDto(
                amonestaciones.contarVigentes(visitante.getId(), ahora),
                config.getMaxFaltas(),
                config.getMesesVentana(),
                visitante.estaBloqueado(ahora),
                visitante.estaBloqueado(ahora) ? visitante.getBloqueadoHasta() : null,
                historial);
    }

    private static AmonestacionResponseDto aDto(Amonestacion a, LocalDateTime corte) {
        return new AmonestacionResponseDto(
                a.getId(), a.getFecha(), a.getOrigen().name(), a.getMotivo(),
                a.getPatente(), a.getAutorNombre(), a.esAutomatica(),
                a.getFecha().isAfter(corte));
    }

    private static ConfiguracionSancionesDto aDto(ConfiguracionSanciones c) {
        return new ConfiguracionSancionesDto(c.getMaxFaltas(), c.getDiasBloqueo(),
                c.getMesesVentana(), c.getToleranciaIngresoMin(), c.getToleranciaEgresoMin());
    }
}
