package com.aparcar.api.controller;

import com.aparcar.api.dto.discapacidad.AprobarVerificacionRequestDto;
import com.aparcar.api.dto.discapacidad.CantidadResponseDto;
import com.aparcar.api.dto.discapacidad.MiVerificacionResponseDto;
import com.aparcar.api.dto.discapacidad.MotivoRequestDto;
import com.aparcar.api.dto.discapacidad.VerificacionResponseDto;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.service.IVerificacionDiscapacidadService;
import com.aparcar.api.service.IVerificacionDiscapacidadService.DocumentoDescarga;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Verificacion de discapacidad con certificado.
 *
 * <p>{@code /mia} y la subida son de cualquier cuenta autenticada, sobre lo
 * propio; el resto es solo ADMIN (lo exige la configuracion de seguridad). El
 * personal de la puerta (EMPLEADO) no ve certificados: es un dato de salud y
 * no lo necesita para operar.
 */
@RestController
@RequestMapping("/api/v1/verificaciones-discapacidad")
@RequiredArgsConstructor
public class VerificacionDiscapacidadController {
    private final IVerificacionDiscapacidadService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MiVerificacionResponseDto> solicitar(
            @RequestPart("archivo") MultipartFile archivo, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.solicitar(authentication.getName(), archivo));
    }

    @GetMapping("/mia")
    public ResponseEntity<MiVerificacionResponseDto> miEstado(Authentication authentication) {
        return ResponseEntity.ok(service.miEstado(authentication.getName()));
    }

    @GetMapping("/mia/documento")
    public ResponseEntity<byte[]> miDocumento(Authentication authentication) {
        return documento(service.miDocumento(authentication.getName()));
    }

    @PostMapping("/mia/cancelar")
    public ResponseEntity<MiVerificacionResponseDto> cancelar(Authentication authentication) {
        return ResponseEntity.ok(service.cancelar(authentication.getName()));
    }

    @GetMapping
    public ResponseEntity<List<VerificacionResponseDto>> listar(
            @RequestParam(defaultValue = "EN_REVISION") String estado) {
        return ResponseEntity.ok(service.listar(estadoDe(estado)));
    }

    @GetMapping("/pendientes/cantidad")
    public ResponseEntity<CantidadResponseDto> cantidadPendientes() {
        return ResponseEntity.ok(new CantidadResponseDto(service.cantidadPendientes()));
    }

    @GetMapping("/{id}/documento")
    public ResponseEntity<byte[]> documento(@PathVariable UUID id, Authentication authentication) {
        return documento(service.documento(id, authentication.getName()));
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<VerificacionResponseDto> aprobar(
            @PathVariable UUID id, @RequestBody(required = false) AprobarVerificacionRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.ok(service.aprobar(id, dto == null ? null : dto.venceEl(), authentication.getName()));
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<VerificacionResponseDto> rechazar(
            @PathVariable UUID id, @Valid @RequestBody MotivoRequestDto dto, Authentication authentication) {
        return ResponseEntity.ok(service.rechazar(id, dto.motivo(), authentication.getName()));
    }

    @PostMapping("/{id}/revocar")
    public ResponseEntity<VerificacionResponseDto> revocar(
            @PathVariable UUID id, @Valid @RequestBody MotivoRequestDto dto, Authentication authentication) {
        return ResponseEntity.ok(service.revocar(id, dto.motivo(), authentication.getName()));
    }

    private static VerificacionEstado estadoDe(String estado) {
        try {
            return VerificacionEstado.valueOf(estado.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Estado de verificación inválido: " + estado + ".");
        }
    }

    /**
     * Se sirve en linea (para verlo en pantalla) con un tipo fijo, sin que el
     * navegador adivine otro, y sin que quede en ninguna cache.
     */
    private static ResponseEntity<byte[]> documento(DocumentoDescarga documento) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.tipo()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(documento.nombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(documento.contenido());
    }
}
