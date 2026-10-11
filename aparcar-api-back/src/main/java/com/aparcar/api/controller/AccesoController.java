package com.aparcar.api.controller;

import com.aparcar.api.dto.acceso.AccesoConsultaRequestDto;
import com.aparcar.api.dto.acceso.AccesoConsultaResponseDto;
import com.aparcar.api.dto.acceso.AccesoMovimientoResponseDto;
import com.aparcar.api.dto.acceso.AccesoRegistroRequestDto;
import com.aparcar.api.dto.acceso.AccesoRegistroResponseDto;
import com.aparcar.api.service.IAccesoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Control de acceso en la puerta. Solo ADMIN (lo exige la configuracion de
 * seguridad) y con rate limit propio.
 *
 * <p>Son POST aunque la consulta no cambie la reserva: el codigo es una
 * credencial y no tiene que quedar en URLs ni en logs de acceso.
 */
@RestController
@RequestMapping("/api/v1/accesos")
@RequiredArgsConstructor
public class AccesoController {
    private final IAccesoService accesoService;

    @PostMapping("/consulta")
    public ResponseEntity<AccesoConsultaResponseDto> consultar(
            @Valid @RequestBody AccesoConsultaRequestDto dto, Authentication authentication) {
        return ResponseEntity.ok(accesoService.consultar(dto.codigo(), authentication.getName()));
    }

    @PostMapping("/registro")
    public ResponseEntity<AccesoRegistroResponseDto> registrar(
            @Valid @RequestBody AccesoRegistroRequestDto dto, Authentication authentication) {
        return ResponseEntity.ok(accesoService.registrar(dto.codigo(), dto.accion(), authentication.getName()));
    }

    @GetMapping("/movimientos")
    public ResponseEntity<List<AccesoMovimientoResponseDto>> movimientos() {
        return ResponseEntity.ok(accesoService.movimientosDelDia());
    }
}
