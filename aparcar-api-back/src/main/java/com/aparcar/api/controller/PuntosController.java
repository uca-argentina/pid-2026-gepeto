package com.aparcar.api.controller;

import com.aparcar.api.dto.puntos.BilleteraResponseDto;
import com.aparcar.api.dto.puntos.MovimientoPuntosResponseDto;
import com.aparcar.api.dto.puntos.ResumenPuntosResponseDto;
import com.aparcar.api.service.IPuntosService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Puntos de la gamificacion. Todo es de lectura: los puntos se ganan solo al
 * registrar el ingreso y se gastan al crear una reserva con descuento.
 */
@RestController
@RequestMapping("/api/v1/puntos")
@RequiredArgsConstructor
public class PuntosController {
    private final IPuntosService puntosService;

    @GetMapping("/billetera")
    public BilleteraResponseDto billetera(Authentication authentication) {
        return puntosService.billetera(authentication.getName());
    }

    @GetMapping("/movimientos")
    public List<MovimientoPuntosResponseDto> movimientos(Authentication authentication) {
        return puntosService.movimientos(authentication.getName());
    }

    /** Solo ADMIN (lo exige la configuracion de seguridad). */
    @GetMapping("/resumen")
    public ResumenPuntosResponseDto resumen() {
        return puntosService.resumen();
    }
}
