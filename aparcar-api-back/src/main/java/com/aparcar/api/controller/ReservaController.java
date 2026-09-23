package com.aparcar.api.controller;

import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.service.IReservaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {
    private final IReservaService reservaService;

    private static boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
    }

    @PostMapping
    public ResponseEntity<ReservaResponseDto> crear(
            @Valid @RequestBody ReservaRequestDto dto, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crear(dto, authentication.getName(), esAdmin(authentication)));
    }

    /**
     * El ADMIN recibe todas las reservas del sistema; un visitante, solo las
     * suyas. El filtro lo hace el backend para que no dependa de que el
     * frontend se acuerde de filtrar.
     */
    @GetMapping
    public ResponseEntity<List<ReservaResponseDto>> listar(Authentication authentication) {
        return ResponseEntity.ok(
                reservaService.listar(authentication.getName(), esAdmin(authentication)));
    }

    /**
     * Da de baja la reserva. Es POST y no DELETE porque no borra nada: la pasa
     * a CANCELADA y libera la cochera, dejando el registro para el historial.
     */
    @PostMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponseDto> cancelar(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(
                reservaService.cancelar(id, authentication.getName(), esAdmin(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponseDto> obtenerPorId(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(
                reservaService.obtenerPorId(id, authentication.getName(), esAdmin(authentication)));
    }
}
