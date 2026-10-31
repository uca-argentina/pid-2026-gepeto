package com.aparcar.api.controller;

import com.aparcar.api.dto.ErrorResponseDto;
import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.dto.reserva.TarifaDto;
import com.aparcar.api.dto.reserva.TarifasRequestDto;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.service.IPuntosService;
import com.aparcar.api.service.ITarifaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/tarifas")
@RequiredArgsConstructor
public class TarifaController {
    private final ITarifaService tarifaService;
    private final IPuntosService puntosService;

    @GetMapping
    public List<TarifaDto> listar() {
        return tarifaService.listar();
    }

    @PutMapping
    public List<TarifaDto> actualizar(@Valid @RequestBody TarifasRequestDto dto) {
        return tarifaService.actualizar(dto);
    }

    @GetMapping("/cotizacion")
    public CotizacionResponseDto cotizar(
            @RequestParam CocheraTipo tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta,
            @RequestParam(required = false) String puntos,
            @RequestParam(defaultValue = "false") boolean usarTodos,
            Authentication authentication) {
        var cotizacion = tarifaService.cotizar(tipo, desde, hasta);
        // Sin parametros de puntos responde lo mismo que antes, mas lo que
        // sumaria y el saldo de quien cotiza.
        return puntosService.cotizar(cotizacion, authentication == null ? null : authentication.getName(),
                desde, hasta, leerPuntos(puntos), usarTodos);
    }

    /** Se lee a mano para rechazar "1.5" o "abc" con un mensaje propio, no el generico de parametros. */
    private static BigDecimal leerPuntos(String puntos) {
        if (puntos == null || puntos.isBlank()) return null;
        try {
            return new BigDecimal(puntos.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException("Los puntos tienen que ser un número entero.");
        }
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponseDto> conflicto() {
        return ResponseEntity.status(409).body(new ErrorResponseDto(409,
                "Las tarifas cambiaron. Recargá los precios antes de guardar.", null));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDto> parametrosInvalidos() {
        return ResponseEntity.badRequest().body(new ErrorResponseDto(400,
                "Indicá un tipo válido y las fechas desde y hasta para cotizar.", null));
    }
}
