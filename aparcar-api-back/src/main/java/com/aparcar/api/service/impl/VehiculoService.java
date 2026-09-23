package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VehiculoUpdateDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IVehiculoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VehiculoService implements IVehiculoService {
    private final VehiculoRepository vehiculoRepository;
    private final VisitanteRepository visitanteRepository;
    private final ReservaRepository reservaRepository;

    // Formatos vigentes en Argentina. Auto y Carga comparten el mismo esquema
    // (asumido: en Argentina los vehiculos de carga patentan con el mismo
    // formato alfanumerico que los autos; confirmar con el dominio real si
    // hay dudas). Moto tiene un esquema distinto e incompatible con el de
    // Auto/Carga, por eso la validacion depende del tipo.
    private static final String AUTO_ANTERIOR = "^[A-Za-z]{3}[0-9]{3}$";
    private static final String AUTO_MERCOSUR = "^[A-Za-z]{2}[0-9]{3}[A-Za-z]{2}$";
    private static final String MOTO_ANTERIOR = "^[0-9]{3}[A-Za-z]{3}$";
    private static final String MOTO_MERCOSUR = "^[A-Za-z][0-9]{3}[A-Za-z]{3}$";

    @Override
    public VehiculoResponseDto crear(VehiculoRequestDto dto) {
        if (dto.getVisitanteId() == null) {
            throw new ValidationException("El visitante es obligatorio.");
        }

        Visitante visitante = visitanteRepository.findById(dto.getVisitanteId())
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));

        String patente = dto.getPatente().toUpperCase();
        validarFormatoPatente(patente, dto.getTipo());

        if (vehiculoRepository.existsByPatente(patente)) {
            throw new ValidationException("Ya existe un vehiculo con esa patente.");
        }

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente(patente);
        vehiculo.setTipo(dto.getTipo());
        vehiculo.setVisitante(visitante);

        return toResponseDto(vehiculoRepository.save(vehiculo));
    }

    @Override
    public VehiculoResponseDto crear(VehiculoRequestDto dto, String requesterEmail, boolean requesterIsAdmin) {
        if (!requesterIsAdmin) {
            Visitante propio = visitanteRepository.findByEmail(requesterEmail)
                    .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
            dto.setVisitanteId(propio.getId());
        }

        return crear(dto);
    }

    @Override
    public VehiculoResponseDto obtenerPorId(UUID id) {
        return toResponseDto(buscarPorId(id));
    }

    @Override
    public List<VehiculoResponseDto> listar() {
        return vehiculoRepository.findAll().stream().map(this::toResponseDto).toList();
    }

    @Override
    public List<VehiculoResponseDto> listarPorVisitante(UUID visitanteId) {
        return vehiculoRepository.findByVisitanteId(visitanteId).stream().map(this::toResponseDto).toList();
    }

    @Override
    public List<VehiculoResponseDto> listarPropios(String email) {
        return vehiculoRepository.findByVisitanteEmail(email).stream().map(this::toResponseDto).toList();
    }

    @Override
    public VehiculoResponseDto editar(UUID id, VehiculoUpdateDto dto, String requesterEmail, boolean requesterIsAdmin) {
        Vehiculo vehiculo = buscarPorId(id);
        verificarPropietario(vehiculo, requesterEmail, requesterIsAdmin);

        String patente = dto.getPatente().toUpperCase();
        validarFormatoPatente(patente, dto.getTipo());

        boolean cambiaPatente = !vehiculo.getPatente().equals(patente);
        if (cambiaPatente && vehiculoRepository.existsByPatente(patente)) {
            throw new ValidationException("Ya existe un vehiculo con esa patente.");
        }

        vehiculo.setPatente(patente);
        vehiculo.setTipo(dto.getTipo());

        return toResponseDto(vehiculoRepository.save(vehiculo));
    }

    @Override
    public void eliminar(UUID id, String requesterEmail, boolean requesterIsAdmin) {
        Vehiculo vehiculo = buscarPorId(id);
        verificarPropietario(vehiculo, requesterEmail, requesterIsAdmin);

        if (reservaRepository.existsByVehiculoId(id)) {
            throw new ValidationException("No se puede eliminar un vehiculo que tiene reservas asociadas.");
        }

        vehiculoRepository.delete(vehiculo);
    }

    /**
     * Valida que la patente (ya en mayusculas) tenga un formato vigente en
     * Argentina para el tipo de vehiculo indicado.
     *
     * @throws ValidationException Si el formato no corresponde al tipo.
     */
    private void validarFormatoPatente(String patente, VehiculoTipo tipo) {
        boolean valido = switch (tipo) {
            case AUTO, CARGA -> patente.matches(AUTO_ANTERIOR) || patente.matches(AUTO_MERCOSUR);
            case MOTO -> patente.matches(MOTO_ANTERIOR) || patente.matches(MOTO_MERCOSUR);
        };

        if (!valido) {
            String formatosEsperados = tipo == VehiculoTipo.MOTO
                    ? "123ABC (formato anterior) o A123BCD (Mercosur)"
                    : "ABC123 (formato anterior) o AB123CD (Mercosur)";
            throw new ValidationException(
                    "La patente no tiene un formato valido para " + tipo + ". Formatos esperados: " + formatosEsperados + ".");
        }
    }

    private void verificarPropietario(Vehiculo vehiculo, String requesterEmail, boolean requesterIsAdmin) {
        if (requesterIsAdmin) {
            return;
        }

        if (!vehiculo.getVisitante().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("No podes modificar un vehiculo que no es tuyo.");
        }
    }

    private Vehiculo buscarPorId(UUID id) {
        return vehiculoRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehiculo no encontrado."));
    }

    private VehiculoResponseDto toResponseDto(Vehiculo vehiculo) {
        return new VehiculoResponseDto(
                vehiculo.getId(),
                vehiculo.getPatente(),
                vehiculo.getTipo(),
                vehiculo.getVisitante().getId());
    }
}