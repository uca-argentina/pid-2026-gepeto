package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IReservaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaService implements IReservaService {
    private final ReservaRepository reservaRepository;
    private final VisitanteRepository visitanteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final CocheraRepository cocheraRepository;

    @Override
    @Transactional
    public ReservaResponseDto crear(ReservaRequestDto dto, String requesterEmail, boolean requesterIsAdmin) {
        Visitante visitante = resolverVisitante(dto, requesterEmail, requesterIsAdmin);

        Vehiculo vehiculo = vehiculoRepository.findById(dto.getVehiculoId())
                .orElseThrow(() -> new NotFoundException("Vehiculo no encontrado."));

        if (!vehiculo.getVisitante().getId().equals(visitante.getId())) {
            throw new ValidationException("El vehiculo indicado no pertenece al visitante indicado.");
        }

        Cochera cochera = cocheraRepository.findById(dto.getCocheraId())
                .orElseThrow(() -> new NotFoundException("Cochera no encontrada."));

        validarCompatibilidad(cochera, vehiculo);
        validarDisponibilidad(cochera, dto.getFecha());

        Reserva reserva = new Reserva();
        reserva.setFecha(dto.getFecha());
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(ReservaEstado.CONFIRMADA);

        return toResponseDto(reservaRepository.save(reserva));
    }

    @Override
    public ReservaResponseDto obtenerPorId(UUID id, String requesterEmail, boolean requesterIsAdmin) {
        Reserva reserva = buscarPorId(id);

        if (!requesterIsAdmin && !reserva.getVisitante().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("No podes ver una reserva que no es tuya.");
        }

        return toResponseDto(reserva);
    }

    @Override
    public List<ReservaResponseDto> listar(String requesterEmail, boolean requesterIsAdmin) {
        List<Reserva> reservas = requesterIsAdmin
                ? reservaRepository.findAll()
                : reservaRepository.findByVisitanteEmail(requesterEmail);

        return reservas.stream().map(this::toResponseDto).toList();
    }

    @Override
    @Transactional
    public ReservaResponseDto cancelar(UUID id, String requesterEmail, boolean requesterIsAdmin) {
        Reserva reserva = buscarPorId(id);

        if (!requesterIsAdmin && !reserva.getVisitante().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("No podes cancelar una reserva que no es tuya.");
        }

        if (reserva.getEstado() == ReservaEstado.CANCELADA) {
            throw new ValidationException("La reserva ya estaba cancelada.");
        }

        reserva.setEstado(ReservaEstado.CANCELADA);

        return toResponseDto(reservaRepository.save(reserva));
    }

    /**
     * Un visitante solo puede reservar a su nombre, asi que para un USER el
     * visitanteId del request se ignora y se usa la cuenta autenticada. Solo el
     * ADMIN puede reservar en nombre de otro.
     */
    private Visitante resolverVisitante(ReservaRequestDto dto, String requesterEmail, boolean requesterIsAdmin) {
        if (!requesterIsAdmin) {
            return visitanteRepository.findByEmail(requesterEmail)
                    .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
        }

        if (dto.getVisitanteId() == null) {
            throw new ValidationException("El visitante es obligatorio.");
        }

        return visitanteRepository.findById(dto.getVisitanteId())
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }

    private void validarCompatibilidad(Cochera cochera, Vehiculo vehiculo) {
        if (cochera.getTipo() == CocheraTipo.ACCESIBLE) {
            return;
        }
        if (!cochera.getTipo().name().equals(vehiculo.getTipo().name())) {
            throw new ValidationException(
                    "El tipo de cochera (%s) no es compatible con el tipo de vehiculo (%s)."
                            .formatted(cochera.getTipo(), vehiculo.getTipo()));
        }
    }

    private void validarDisponibilidad(Cochera cochera, LocalDate fecha) {
        boolean ocupada = reservaRepository.existsByCocheraIdAndFechaAndEstado(
                cochera.getId(), fecha, ReservaEstado.CONFIRMADA);
        if (ocupada) {
            throw new ValidationException(
                    "La cochera %s ya tiene una reserva confirmada para el %s.".formatted(cochera.getNumero(), fecha));
        }
    }

    private Reserva buscarPorId(UUID id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada."));
    }

    private ReservaResponseDto toResponseDto(Reserva reserva) {
        VisitanteResponseDto visitanteDto = new VisitanteResponseDto(
                reserva.getVisitante().getId(),
                reserva.getVisitante().getNombre(),
                reserva.getVisitante().getDocumento(),
                reserva.getVisitante().getTelefono(),
                reserva.getVisitante().getEmail());

        VehiculoResponseDto vehiculoDto = new VehiculoResponseDto(
                reserva.getVehiculo().getId(),
                reserva.getVehiculo().getPatente(),
                reserva.getVehiculo().getTipo(),
                reserva.getVisitante().getId());

        CocheraResponseDto cocheraDto = new CocheraResponseDto(
                reserva.getCochera().getId(),
                reserva.getCochera().getNumero(),
                reserva.getCochera().getSector(),
                reserva.getCochera().getTipo(),
                reserva.getCochera().getEstado(),
                null);

        return new ReservaResponseDto(
                reserva.getId(),
                reserva.getFecha(),
                visitanteDto,
                vehiculoDto,
                cocheraDto,
                reserva.getEstado(),
                reserva.getFechaCreacion());
    }
}
