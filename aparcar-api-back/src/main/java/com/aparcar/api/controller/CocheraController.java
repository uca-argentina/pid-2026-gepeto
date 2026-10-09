package com.aparcar.api.controller;

import com.aparcar.api.dto.reserva.CocheraAltaPorPlantaDto;
import com.aparcar.api.dto.reserva.CocheraRequestDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.service.ICocheraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cocheras")
@RequiredArgsConstructor
public class CocheraController {
    private final ICocheraService cocheraService;

    @PostMapping
    public ResponseEntity<CocheraResponseDto> crear(@Valid @RequestBody CocheraRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cocheraService.crear(dto));
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<CocheraResponseDto>> crearEnLote(@Valid @RequestBody List<CocheraRequestDto> dtos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cocheraService.crearEnLote(dtos));
    }

    /**
     * Alta por planta: un sector y cuantas cocheras de cada tipo. Los numeros
     * los genera el backend y vuelven en la respuesta, para que el admin vea
     * exactamente cuales quedaron asignados.
     */
    @PostMapping("/alta-por-planta")
    public ResponseEntity<List<CocheraResponseDto>> crearPorPlanta(@Valid @RequestBody CocheraAltaPorPlantaDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cocheraService.crearPorPlanta(dto));
    }

    @GetMapping("/sectores")
    public ResponseEntity<List<String>> listarSectores() {
        return ResponseEntity.ok(cocheraService.listarSectores());
    }

    @GetMapping
    public ResponseEntity<List<CocheraResponseDto>> listar(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) CocheraTipo tipo,
            @RequestParam(required = false) CocheraEstado estado,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(cocheraService.listar(sector, tipo, estado, desde, hasta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CocheraResponseDto> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cocheraService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CocheraResponseDto> editar(@PathVariable UUID id, @Valid @RequestBody CocheraRequestDto dto,
                                                    Authentication authentication) {
        return ResponseEntity.ok(cocheraService.editar(id, dto, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        cocheraService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cocheras libres durante toda la franja pedida. El rango es semiabierto:
     * una cochera cuya reserva termina justo en "desde" cuenta como libre.
     *
     * <p>El endpoint es publico, asi que {@code authentication} viene null
     * para un anonimo. Si quien pide es un visitante logueado sin discapacidad
     * declarada, no se le ofrecen las cocheras ACCESIBLE (que igual no podria
     * reservar).
     */
    @GetMapping("/disponibles")
    public ResponseEntity<List<CocheraResponseDto>> listarDisponibles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) VehiculoTipo tipoVehiculo,
            Authentication authentication) {
        String requesterEmail = authentication == null ? null : authentication.getName();
        return ResponseEntity.ok(cocheraService.listarDisponibles(
                desde, hasta, tipoVehiculo, requesterEmail, esAdmin(authentication)));
    }

    private static boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }
}
