package com.aparcar.api.controller;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    @GetMapping("/sectores")
    public ResponseEntity<List<String>> listarSectores() {
        return ResponseEntity.ok(cocheraService.listarSectores());
    }

    @GetMapping
    public ResponseEntity<List<CocheraResponseDto>> listar(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) CocheraTipo tipo,
            @RequestParam(required = false) CocheraEstado estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(cocheraService.listar(sector, tipo, estado, fecha));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CocheraResponseDto> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(cocheraService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CocheraResponseDto> editar(@PathVariable UUID id, @Valid @RequestBody CocheraRequestDto dto) {
        return ResponseEntity.ok(cocheraService.editar(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id) {
        cocheraService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Cocheras libres durante toda la franja pedida. El rango es semiabierto:
     * una cochera cuya reserva termina justo en "desde" cuenta como libre.
     */
    @GetMapping("/disponibles")
    public ResponseEntity<List<CocheraResponseDto>> listarDisponibles(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) VehiculoTipo tipoVehiculo) {
        return ResponseEntity.ok(cocheraService.listarDisponibles(desde, hasta, tipoVehiculo));
    }
}