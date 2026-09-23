package com.aparcar.api.controller;

import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VehiculoUpdateDto;
import com.aparcar.api.service.IVehiculoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehiculos")
@RequiredArgsConstructor
public class VehiculoController {
    private final IVehiculoService vehiculoService;

    private static boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }

    @PostMapping
    public ResponseEntity<VehiculoResponseDto> crear(
            @Valid @RequestBody VehiculoRequestDto dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vehiculoService.crear(dto, authentication.getName(), esAdmin(authentication)));
    }

    /**
     * Un visitante solo ve sus propios vehiculos, sin importar que pida: el
     * catalogo completo es cosa del ADMIN, que lo necesita para reservar en
     * nombre de otro.
     */
    @GetMapping
    public ResponseEntity<List<VehiculoResponseDto>> listar(
            @RequestParam(required = false) UUID visitanteId, Authentication authentication) {
        if (!esAdmin(authentication)) {
            return ResponseEntity.ok(vehiculoService.listarPropios(authentication.getName()));
        }
        if (visitanteId != null) {
            return ResponseEntity.ok(vehiculoService.listarPorVisitante(visitanteId));
        }
        return ResponseEntity.ok(vehiculoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VehiculoResponseDto> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(vehiculoService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<VehiculoResponseDto> editar(
            @PathVariable UUID id, @Valid @RequestBody VehiculoUpdateDto dto, Authentication authentication) {
        return ResponseEntity.ok(
                vehiculoService.editar(id, dto, authentication.getName(), esAdmin(authentication)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, Authentication authentication) {
        vehiculoService.eliminar(id, authentication.getName(), esAdmin(authentication));
        return ResponseEntity.noContent().build();
    }
}