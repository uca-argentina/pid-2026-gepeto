package com.aparcar.api.service.impl;

import com.aparcar.api.dto.auth.ChangePasswordDto;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VisitanteAltaDto;
import com.aparcar.api.dto.reserva.VisitanteAltaResponseDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.dto.reserva.VisitanteUpdateDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IReservaService;
import com.aparcar.api.service.IVehiculoService;
import com.aparcar.api.service.IVisitanteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisitanteService implements IVisitanteService {
    private final VisitanteRepository visitanteRepository;
    private final IVehiculoService vehiculoService;
    private final IReservaService reservaService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public VisitanteAltaResponseDto altaConReserva(VisitanteAltaDto dto) {
        if (visitanteRepository.existsByDocumento(dto.getDocumento())) {
            throw new ValidationException("Ya existe un visitante con ese documento.");
        }

        if (visitanteRepository.existsByEmail(dto.getEmail())) {
            throw new ValidationException("Ya existe una cuenta con ese email.");
        }

        // La contraseña inicial es el documento. Es deliberado: el admin da de
        // alta al visitante en el momento y le puede decir con que entrar sin
        // tener que inventar y comunicar una contraseña aparte.
        Visitante visitante = visitanteRepository.save(new Visitante(
                dto.getNombre(),
                dto.getDocumento(),
                dto.getEmail(),
                passwordEncoder.encode(dto.getDocumento()),
                dto.getTelefono(),
                Set.of(AppAuthority.USER),
                true));

        VehiculoRequestDto vehiculoDto = new VehiculoRequestDto();
        vehiculoDto.setPatente(dto.getPatente());
        vehiculoDto.setTipo(dto.getTipoVehiculo());
        vehiculoDto.setVisitanteId(visitante.getId());
        VehiculoResponseDto vehiculo = vehiculoService.crear(vehiculoDto);

        // Reutilizamos el alta de reserva normal para no duplicar las reglas de
        // compatibilidad y disponibilidad. Al correr dentro de esta misma
        // transaccion, si la reserva falla se cae tambien la cuenta y el
        // vehiculo, que es justo lo que queremos: sin reserva no hay alta.
        ReservaRequestDto reservaDto = new ReservaRequestDto();
        reservaDto.setVisitanteId(visitante.getId());
        reservaDto.setVehiculoId(vehiculo.id());
        reservaDto.setCocheraId(dto.getCocheraId());
        // Por defecto el alta cubre desde ahora y por una hora: el admin esta
        // registrando a alguien que acaba de llegar. Si manda la franja, manda.
        LocalDateTime desde = dto.getDesde() != null ? dto.getDesde() : LocalDateTime.now();
        LocalDateTime hasta = dto.getHasta() != null ? dto.getHasta() : desde.plusHours(1);
        reservaDto.setDesde(desde);
        reservaDto.setHasta(hasta);
        ReservaResponseDto reserva = reservaService.crear(reservaDto, visitante.getEmail(), true);

        return new VisitanteAltaResponseDto(toResponseDto(visitante), vehiculo, reserva);
    }

    @Override
    public VisitanteResponseDto obtenerPorId(UUID id) {
        return toResponseDto(buscarPorId(id));
    }

    @Override
    public List<VisitanteResponseDto> listar() {
        return visitanteRepository.findAll().stream().map(this::toResponseDto).toList();
    }

    @Override
    public VisitanteResponseDto obtenerPropio(String email) {
        return toResponseDto(buscarPorEmail(email));
    }

    @Override
    public VisitanteResponseDto actualizarPropio(String email, VisitanteUpdateDto dto) {
        Visitante visitante = buscarPorEmail(email);

        // El email es con lo que se inicia sesion, asi que cambiarlo cambia el
        // login: hay que asegurarse de que no se lo pise a otra cuenta.
        boolean cambiaEmail = !visitante.getEmail().equalsIgnoreCase(dto.getEmail());
        if (cambiaEmail && visitanteRepository.existsByEmail(dto.getEmail())) {
            throw new ValidationException("Ya existe una cuenta con ese email.");
        }

        visitante.setTelefono(dto.getTelefono());
        visitante.setEmail(dto.getEmail());

        return toResponseDto(visitanteRepository.save(visitante));
    }

    @Override
    public void cambiarPasswordPropia(String email, ChangePasswordDto dto) {
        Visitante visitante = buscarPorEmail(email);

        if (!passwordEncoder.matches(dto.getPasswordActual(), visitante.getPassword())) {
            throw new ValidationException("La contraseña actual no es correcta.");
        }

        // Sin esto, "cambiar" la contraseña por la misma devolveria exito y el
        // visitante creeria que dejo de usar su documento como clave.
        if (passwordEncoder.matches(dto.getPasswordNueva(), visitante.getPassword())) {
            throw new ValidationException("La contraseña nueva tiene que ser distinta de la actual.");
        }

        visitante.setPassword(passwordEncoder.encode(dto.getPasswordNueva()));
        visitanteRepository.save(visitante);
    }

    private Visitante buscarPorId(UUID id) {
        return visitanteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }

    private Visitante buscarPorEmail(String email) {
        return visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }

    private VisitanteResponseDto toResponseDto(Visitante visitante) {
        return new VisitanteResponseDto(
                visitante.getId(),
                visitante.getNombre(),
                visitante.getDocumento(),
                visitante.getTelefono(),
                visitante.getEmail());
    }
}
