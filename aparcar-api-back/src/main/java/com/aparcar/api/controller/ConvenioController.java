package com.aparcar.api.controller;

import com.aparcar.api.dto.ErrorResponseDto;
import com.aparcar.api.dto.convenio.*;
import com.aparcar.api.security.Roles;
import com.aparcar.api.service.impl.ConvenioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/convenios")
@RequiredArgsConstructor
public class ConvenioController {
    private final ConvenioService service;

    @GetMapping
    public List<ConvenioResponseDto> listar() { return service.listar(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConvenioResponseDto crear(@Valid @RequestBody ConvenioRequestDto dto) { return service.guardar(null, dto); }

    @PutMapping("/{id}")
    public ConvenioResponseDto actualizar(@PathVariable UUID id, @Valid @RequestBody ConvenioRequestDto dto) {
        return service.guardar(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) { service.eliminar(id); }

    @GetMapping("/credenciales")
    public List<CredencialResponseDto> credenciales(@RequestParam(required = false) UUID visitanteId, Authentication auth) {
        return service.listarCredenciales(auth.getName(), Roles.esPersonal(auth), visitanteId);
    }

    @PostMapping("/verificar")
    public CredencialResponseDto verificar(@Valid @RequestBody CredencialRequestDto dto, Authentication auth) {
        return service.verificar(dto, auth.getName(), Roles.esPersonal(auth));
    }

    @PutMapping("/credenciales")
    public CredencialResponseDto guardarCredencial(@Valid @RequestBody CredencialRequestDto dto, Authentication auth) {
        return service.guardarCredencial(dto, auth.getName(), Roles.esPersonal(auth));
    }

    @DeleteMapping("/credenciales/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarCredencial(@PathVariable UUID id, @RequestParam(required = false) UUID visitanteId, Authentication auth) {
        service.eliminarCredencial(id, auth.getName(), Roles.esPersonal(auth), visitanteId);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> conflicto() {
        return ResponseEntity.status(409).body(new ErrorResponseDto(409,
                "El convenio o la credencial cambiaron. Recarga antes de volver a intentar.", null));
    }
}
