package com.aparcar.api.controller;

import com.aparcar.api.dto.auth.ChangePasswordDto;
import com.aparcar.api.dto.reserva.VisitanteAltaDto;
import com.aparcar.api.dto.reserva.VisitanteAltaResponseDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.dto.reserva.VisitanteUpdateDto;
import com.aparcar.api.service.IVisitanteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/visitantes")
@RequiredArgsConstructor
public class VisitanteController {
    private final IVisitanteService visitanteService;

    /**
     * Alta operativa: cuenta + vehiculo + reserva del dia, en una transaccion.
     * Solo ADMIN (ver la configuracion de seguridad).
     */
    @PostMapping("/alta")
    public ResponseEntity<VisitanteAltaResponseDto> alta(@Valid @RequestBody VisitanteAltaDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(visitanteService.altaConReserva(dto));
    }

    @GetMapping
    public ResponseEntity<List<VisitanteResponseDto>> listar() {
        return ResponseEntity.ok(visitanteService.listar());
    }

    // Va antes de /{id} para que "me" no se intente parsear como UUID.
    @GetMapping("/me")
    public ResponseEntity<VisitanteResponseDto> obtenerPropio(Authentication authentication) {
        return ResponseEntity.ok(visitanteService.obtenerPropio(authentication.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<VisitanteResponseDto> actualizarPropio(
            @Valid @RequestBody VisitanteUpdateDto dto, Authentication authentication) {
        return ResponseEntity.ok(visitanteService.actualizarPropio(authentication.getName(), dto));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> cambiarPasswordPropia(
            @Valid @RequestBody ChangePasswordDto dto, Authentication authentication) {
        visitanteService.cambiarPasswordPropia(authentication.getName(), dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<VisitanteResponseDto> obtenerPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(visitanteService.obtenerPorId(id));
    }
}
