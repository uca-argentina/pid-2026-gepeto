package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.CocheraAltaPorPlantaDto;
import com.aparcar.api.dto.reserva.CocheraRequestDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.ICocheraService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CocheraService implements ICocheraService {
    private final CocheraRepository cocheraRepository;
    private final ReservaRepository reservaRepository;
    private final VisitanteRepository visitanteRepository;

    @Override
    public CocheraResponseDto crear(CocheraRequestDto dto) {
        if (cocheraRepository.existsByNumero(dto.getNumero())) {
            throw new ValidationException("Ya existe una cochera con ese numero.");
        }

        Cochera cochera = new Cochera();
        cochera.setNumero(dto.getNumero());
        cochera.setSector(dto.getSector());
        cochera.setTipo(dto.getTipo());
        cochera.setEstado(dto.getEstado());

        return toResponseDto(cocheraRepository.save(cochera));
    }

    @Override
    @Transactional
    public List<CocheraResponseDto> crearEnLote(List<CocheraRequestDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            throw new ValidationException("La lista de cocheras no puede estar vacia.");
        }

        // Todo-o-nada: se valida el lote entero ANTES de guardar nada. Si algo
        // falla mas abajo igual quedariamos cubiertos por @Transactional
        // (rollback automatico), pero validar primero evita guardar la mitad
        // del lote antes de descubrir que la ultima cochera esta repetida.
        Set<String> numerosEnLote = new HashSet<>();
        for (CocheraRequestDto dto : dtos) {
            if (!numerosEnLote.add(dto.getNumero())) {
                throw new ValidationException(
                        "El numero '%s' esta repetido dentro del lote.".formatted(dto.getNumero()));
            }
            if (cocheraRepository.existsByNumero(dto.getNumero())) {
                throw new ValidationException(
                        "Ya existe una cochera con el numero '%s'.".formatted(dto.getNumero()));
            }
        }

        List<Cochera> cocheras = dtos.stream().map(dto -> {
            Cochera cochera = new Cochera();
            cochera.setNumero(dto.getNumero());
            cochera.setSector(dto.getSector());
            cochera.setTipo(dto.getTipo());
            cochera.setEstado(dto.getEstado());
            return cochera;
        }).toList();

        return cocheraRepository.saveAll(cocheras).stream().map(this::toResponseDto).toList();
    }

    @Override
    @Transactional
    public List<CocheraResponseDto> crearPorPlanta(CocheraAltaPorPlantaDto dto) {
        String sector = dto.getSector() == null ? "" : dto.getSector().trim();
        if (sector.isEmpty()) {
            throw new ValidationException("El sector es obligatorio.");
        }

        Map<CocheraTipo, Integer> cantidades = dto.getCantidades() == null ? Map.of() : dto.getCantidades();
        // El controller ya lo valida con @PositiveOrZero, pero el servicio no
        // depende de que lo llamen por HTTP.
        for (Map.Entry<CocheraTipo, Integer> entrada : cantidades.entrySet()) {
            if (entrada.getValue() != null && entrada.getValue() < 0) {
                throw new ValidationException(
                        "La cantidad de cocheras de tipo %s no puede ser negativa.".formatted(entrada.getKey()));
            }
        }
        if (cantidades.values().stream().noneMatch(cantidad -> cantidad != null && cantidad > 0)) {
            throw new ValidationException("Indica al menos un tipo de cochera con cantidad mayor a 0.");
        }

        CocheraEstado estado = dto.getEstado() == null ? CocheraEstado.HABILITADA : dto.getEstado();
        List<String> numerosExistentes = cocheraRepository.findAllNumeros();

        // Se recorre el enum (no el mapa) para que el orden del resultado sea
        // siempre el mismo, venga como venga el JSON.
        List<Cochera> nuevas = new ArrayList<>();
        for (CocheraTipo tipo : CocheraTipo.values()) {
            Integer cantidad = cantidades.get(tipo);
            if (cantidad == null || cantidad <= 0) {
                continue;
            }
            for (String numero : NumeracionCocheras.siguientes(tipo, cantidad, numerosExistentes)) {
                Cochera cochera = new Cochera();
                cochera.setNumero(numero);
                cochera.setSector(sector);
                cochera.setTipo(tipo);
                cochera.setEstado(estado);
                nuevas.add(cochera);
            }
        }

        // saveAllAndFlush (y no saveAll) para que el INSERT ocurra aca adentro:
        // si otra alta concurrente se quedo con alguno de estos numeros, la
        // restriccion UNIQUE salta ahora y se puede traducir a un mensaje
        // claro, en vez de explotar en el commit como un 500. Toda la
        // transaccion vuelve atras: no queda ninguna cochera a medias.
        try {
            return cocheraRepository.saveAllAndFlush(nuevas).stream().map(this::toResponseDto).toList();
        } catch (DataIntegrityViolationException e) {
            throw new ValidationException(
                    "Otra alta de cocheras se guardo al mismo tiempo y algun numero coincidio. "
                            + "No se creo ninguna cochera; volve a intentarlo.");
        }
    }

    @Override
    public List<String> listarSectores() {
        return cocheraRepository.findDistinctSectores();
    }

    @Override
    public List<CocheraResponseDto> listar(String sector, CocheraTipo tipo, CocheraEstado estado,
                                           LocalDateTime desde, LocalDateTime hasta) {
        List<Cochera> cocheras = cocheraRepository.buscar(blankToNull(sector), tipo, estado);

        if (desde == null || hasta == null) {
            return cocheras.stream().map(cochera -> toResponseDto(cochera, null)).toList();
        }

        Set<UUID> ocupadas = ocupadasEnRango(desde, hasta);

        return cocheras.stream()
                .map(cochera -> toResponseDto(cochera, !ocupadas.contains(cochera.getId())))
                .toList();
    }

    @Override
    public CocheraResponseDto obtenerPorId(UUID id) {
        return toResponseDto(buscarOLanzar(id));
    }

    @Override
    public CocheraResponseDto editar(UUID id, CocheraRequestDto dto) {
        Cochera cochera = buscarOLanzar(id);

        boolean cambiaNumero = !cochera.getNumero().equals(dto.getNumero());
        if (cambiaNumero && cocheraRepository.existsByNumero(dto.getNumero())) {
            throw new ValidationException("Ya existe una cochera con ese numero.");
        }

        cochera.setNumero(dto.getNumero());
        cochera.setSector(dto.getSector());
        cochera.setTipo(dto.getTipo());
        cochera.setEstado(dto.getEstado());

        Cochera guardada = cocheraRepository.save(cochera);

        if (guardada.getEstado() == CocheraEstado.DESHABILITADA) {
            cancelarReservasConfirmadas(guardada.getId());
        }

        return toResponseDto(guardada);
    }

    @Override
    public void eliminar(UUID id) {
        Cochera cochera = buscarOLanzar(id);

        if (reservaRepository.existsByCocheraId(id)) {
            throw new ValidationException("No se puede eliminar una cochera que tiene reservas asociadas.");
        }

        cocheraRepository.delete(cochera);
    }

    @Override
    public List<CocheraResponseDto> listarDisponibles(LocalDateTime desde, LocalDateTime hasta,
                                                      VehiculoTipo tipoVehiculo,
                                                      String requesterEmail, boolean requesterIsAdmin) {
        Set<UUID> ocupadas = ocupadasEnRango(desde, hasta);
        boolean incluirAccesibles = puedeVerAccesibles(requesterEmail, requesterIsAdmin);

        return cocheraRepository.findByEstado(CocheraEstado.HABILITADA).stream()
                .filter(cochera -> !ocupadas.contains(cochera.getId()))
                .filter(cochera -> esCompatible(cochera.getTipo(), tipoVehiculo))
                .filter(cochera -> incluirAccesibles || cochera.getTipo() != CocheraTipo.ACCESIBLE)
                .map(cochera -> toResponseDto(cochera, null))
                .toList();
    }

    /**
     * Solo se le ocultan las ACCESIBLE a un visitante identificado que no
     * declaro discapacidad. Anonimo y ADMIN ven todo (ver el contrato en
     * {@link ICocheraService}).
     *
     * <p>Si el email del token no corresponde a ninguna cuenta (por ejemplo,
     * la borraron con la sesion abierta) se toma el lado conservador y se
     * ocultan: esa persona tampoco podria reservarlas.
     */
    private boolean puedeVerAccesibles(String requesterEmail, boolean requesterIsAdmin) {
        if (requesterEmail == null || requesterIsAdmin) {
            return true;
        }
        return visitanteRepository.findByEmail(requesterEmail)
                .map(Visitante::puedeUsarCocheraAccesible)
                .orElse(false);
    }

    private void cancelarReservasConfirmadas(UUID cocheraId) {
        List<Reserva> reservas = reservaRepository.findByCocheraIdAndEstado(cocheraId, ReservaEstado.CONFIRMADA);
        reservas.forEach(reserva -> reserva.setEstado(ReservaEstado.CANCELADA));
        reservaRepository.saveAll(reservas);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private Cochera buscarOLanzar(UUID id) {
        return cocheraRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cochera no encontrada."));
    }

    private boolean esCompatible(CocheraTipo cocheraTipo, VehiculoTipo tipoVehiculo) {
        if (tipoVehiculo == null || cocheraTipo == CocheraTipo.ACCESIBLE) {
            return true;
        }
        return cocheraTipo.name().equals(tipoVehiculo.name());
    }

    private CocheraResponseDto toResponseDto(Cochera cochera) {
        return toResponseDto(cochera, null);
    }

    /**
     * Las cocheras con alguna reserva CONFIRMADA que pise el rango.
     *
     * <p>Una reserva cuya franja ya termino no entra: su rango no intersecta el
     * pedido, asi que la cochera figura libre sola, sin depender de ninguna
     * tarea de limpieza.
     */
    private Set<UUID> ocupadasEnRango(LocalDateTime desde, LocalDateTime hasta) {
        return reservaRepository.findSolapadas(ReservaEstado.CONFIRMADA, desde, hasta).stream()
                .map(reserva -> reserva.getCochera().getId())
                .collect(Collectors.toSet());
    }

    private CocheraResponseDto toResponseDto(Cochera cochera, Boolean disponibleEnFecha) {
        return new CocheraResponseDto(
                cochera.getId(),
                cochera.getNumero(),
                cochera.getSector(),
                cochera.getTipo(),
                cochera.getEstado(),
                disponibleEnFecha);
    }
}