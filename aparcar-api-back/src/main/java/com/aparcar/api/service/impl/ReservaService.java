package com.aparcar.api.service.impl;

import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.dto.reserva.ReservaAccesoDto;
import com.aparcar.api.dto.reserva.ReservaMovimientoResponseDto;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaRecurrenteRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaAccion;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IReservaService;
import com.aparcar.api.service.ITarifaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReservaService implements IReservaService {
    /** Las reservas se toman de a bloques de 15 minutos. */
    private static final int MINUTOS_POR_BLOQUE = 15;

    private final ReservaRepository reservaRepository;
    private final VisitanteRepository visitanteRepository;
    private final VehiculoRepository vehiculoRepository;
    private final CocheraRepository cocheraRepository;
    private final ITarifaService tarifaService;
    private final CodigoAcceso codigoAcceso;
    private final ReglasAcceso reglasAcceso;
    private final PlanificacionReservas planificacion;

    @Override
    @Transactional
    public ReservaResponseDto crear(ReservaRequestDto dto, String requesterEmail, boolean requesterIsAdmin) {
        planificacion.bloquear();
        planificacion.promover();
        return toResponseDto(crearReserva(dto, requesterEmail, requesterIsAdmin, null), requesterIsAdmin);
    }

    private Reserva crearReserva(ReservaRequestDto dto, String requesterEmail, boolean requesterIsAdmin,
                                  UUID recurrenciaId) {
        Visitante visitante = resolverVisitante(dto, requesterEmail, requesterIsAdmin);

        Vehiculo vehiculo = vehiculoRepository.findById(dto.getVehiculoId())
                .orElseThrow(() -> new NotFoundException("Vehiculo no encontrado."));

        if (!vehiculo.getVisitante().getId().equals(visitante.getId())) {
            throw new ValidationException("El vehiculo indicado no pertenece al visitante indicado.");
        }

        validarRango(dto.getDesde(), dto.getHasta());
        LocalDateTime ahora = reglasAcceso.ahora();
        validarVehiculoLibre(vehiculo, dto.getDesde(), dto.getHasta(), ahora);

        Cochera cochera;
        CocheraTipo tipo;
        if (dto.isListaEspera()) {
            if (dto.getCocheraId() != null || dto.getTipoCochera() == null) {
                throw new ValidationException("Para la lista de espera indica la categoria, sin una cochera asignada.");
            }
            tipo = dto.getTipoCochera();
            if (!PlanificacionReservas.compatible(tipo, visitante, vehiculo)) {
                throw new ValidationException("La categoria elegida no es compatible con el vehiculo o la discapacidad del visitante.");
            }
            cochera = planificacion.disponible(tipo, visitante, vehiculo, dto.getDesde(), dto.getHasta());
        } else {
            if (dto.getCocheraId() == null) {
                throw new ValidationException("La cochera es obligatoria. Acepta expresamente la lista de espera si no hay lugares.");
            }
            cochera = cocheraRepository.findById(dto.getCocheraId())
                    .orElseThrow(() -> new NotFoundException("Cochera no encontrada."));
            if (cochera.getEstado() != CocheraEstado.HABILITADA) {
                throw new ValidationException("La cochera esta deshabilitada.");
            }
            validarCompatibilidad(cochera, vehiculo);
            validarAccesibilidad(cochera, visitante);
            validarDisponibilidad(cochera, dto.getDesde(), dto.getHasta(), ahora);
            tipo = cochera.getTipo();
        }

        // La categoria esta validada; el servidor calcula el importe incluso cuando se entra en espera.
        var cotizacion = tarifaService.cotizar(tipo, dto.getDesde(), dto.getHasta());
        if (dto.getPrecioEsperado() != null && dto.getPrecioEsperado().compareTo(cotizacion.total()) != 0) {
            throw new ValidationException("El precio cambió. Revisá la nueva cotización antes de confirmar.");
        }

        Reserva reserva = new Reserva();
        reserva.setDesde(dto.getDesde());
        reserva.setHasta(dto.getHasta());
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(cochera == null ? ReservaEstado.LISTA_ESPERA : ReservaEstado.CONFIRMADA);
        reserva.setRecurrenciaId(recurrenciaId);
        reserva.setPrecioTotal(cotizacion.total());
        reserva.setTipoTarifa(cotizacion.tipo());

        Visitante actor = requesterIsAdmin ? buscarActor(requesterEmail) : visitante;
        reserva.registrarAccion(ReservaAccion.ALTA, actor, requesterIsAdmin);
        Reserva guardada = reservaRepository.save(reserva);
        // El codigo se deriva del id, que recien existe despues de guardar.
        // Misma transaccion: si algo falla, no queda una reserva sin codigo.
        if (cochera != null) codigoAcceso.asignar(guardada, reglasAcceso.instante());
        return guardada;
    }

    @Override
    @Transactional
    public List<ReservaResponseDto> crearRecurrentes(ReservaRecurrenteRequestDto dto, String email, boolean esAdmin) {
        Visitante titular = resolverAdministrador(email, esAdmin);
        if (dto.getVisitanteId() != null && !dto.getVisitanteId().equals(titular.getId())) {
            throw new AccessDeniedException("Las reservas recurrentes son exclusivamente para tus propios vehiculos.");
        }
        Vehiculo vehiculo = vehiculoRepository.findById(dto.getVehiculoId())
                .orElseThrow(() -> new NotFoundException("Vehiculo no encontrado."));
        if (!vehiculo.getVisitante().getId().equals(titular.getId())) {
            throw new AccessDeniedException("Las reservas recurrentes son exclusivamente para tus propios vehiculos.");
        }
        validarRango(dto.getDesde(), dto.getHasta());
        LocalDate inicio = dto.getDesde().toLocalDate();
        if (dto.isListaEspera() || dto.getCocheraId() == null) {
            throw new ValidationException("La recurrencia requiere una cochera y no admite lista de espera.");
        }
        if (dto.getRepetirHasta() == null || dto.getRepetirHasta().isBefore(inicio)
                || dto.getRepetirHasta().isAfter(inicio.plusYears(1))
                || inicio.isAfter(reglasAcceso.ahora().toLocalDate().plusYears(1))) {
            throw new ValidationException("La repeticion debe terminar entre la primera fecha y un año despues.");
        }
        if (dto.getDiasSemana() == null || dto.getDiasSemana().isEmpty()
                || dto.getDiasSemana().stream().anyMatch(d -> d == null || d < 1 || d > 7)
                || !dto.getDiasSemana().contains(inicio.getDayOfWeek().getValue())) {
            throw new ValidationException("Selecciona dias de lunes (1) a domingo (7), incluyendo el de la primera reserva.");
        }
        List<LocalDate> fechas = inicio.datesUntil(dto.getRepetirHasta().plusDays(1))
                .filter(dia -> dto.getDiasSemana().contains(dia.getDayOfWeek().getValue())).toList();
        if (fechas.size() > 90) throw new ValidationException("La serie admite hasta 90 reservas.");

        planificacion.bloquear();
        planificacion.promover();
        UUID recurrenciaId = UUID.randomUUID();
        Duration duracion = Duration.between(dto.getDesde(), dto.getHasta());
        List<ReservaResponseDto> reservas = new ArrayList<>();
        for (LocalDate fecha : fechas) {
            ReservaRequestDto ocurrencia = new ReservaRequestDto();
            ocurrencia.setVisitanteId(titular.getId());
            ocurrencia.setVehiculoId(dto.getVehiculoId());
            ocurrencia.setCocheraId(dto.getCocheraId());
            ocurrencia.setDesde(fecha.atTime(dto.getDesde().toLocalTime()));
            ocurrencia.setHasta(ocurrencia.getDesde().plus(duracion));
            ocurrencia.setPrecioEsperado(dto.getPrecioEsperado());
            reservas.add(toResponseDto(crearReserva(ocurrencia, email, true, recurrenciaId), true));
        }
        return reservas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDto> listarRecurrentes(String email, boolean esAdmin) {
        Visitante titular = resolverAdministrador(email, esAdmin);
        return reservaRepository.findRecurrentesByVisitanteEmail(titular.getEmail()).stream()
                .map(reserva -> toResponseDto(reserva, true)).toList();
    }

    private Visitante resolverAdministrador(String email, boolean esAdmin) {
        if (!esAdmin) throw new AccessDeniedException("Solo la administracion puede gestionar reservas recurrentes.");
        Visitante titular = visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Cuenta del administrador no encontrada."));
        if (!Boolean.TRUE.equals(titular.getIsActive()) || titular.getAuthorities() == null
                || !titular.getAuthorities().contains(AppAuthority.ADMIN)) {
            throw new AccessDeniedException("Solo la administracion puede gestionar reservas recurrentes.");
        }
        return titular;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDto> notificaciones(String email) {
        return reservaRepository
                .findByVisitanteEmailAndConfirmadaDesdeEsperaAtIsNotNullAndNotificacionLeidaAtIsNullOrderByConfirmadaDesdeEsperaAtDesc(email)
                .stream().map(reserva -> toResponseDto(reserva, false)).toList();
    }

    @Override
    @Transactional
    public ReservaResponseDto leerNotificacion(UUID id, String email) {
        planificacion.bloquear();
        Reserva reserva = buscarPorId(id);
        if (!reserva.getVisitante().getEmail().equals(email)) {
            throw new AccessDeniedException("Solo el titular puede leer esta notificacion.");
        }
        if (reserva.getConfirmadaDesdeEsperaAt() == null) {
            throw new ValidationException("La reserva no tiene una notificacion de confirmacion desde lista de espera.");
        }
        if (reserva.getNotificacionLeidaAt() == null) reserva.setNotificacionLeidaAt(reglasAcceso.instante());
        return toResponseDto(reserva, false);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservaResponseDto obtenerPorId(UUID id, String requesterEmail, boolean requesterIsAdmin) {
        Reserva reserva = buscarPorId(id);

        if (!requesterIsAdmin && !reserva.getVisitante().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("No podes ver una reserva que no es tuya.");
        }

        return toResponseDto(reserva, requesterIsAdmin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservaResponseDto> listar(String requesterEmail, boolean requesterIsAdmin) {
        List<Reserva> reservas = requesterIsAdmin
                ? reservaRepository.findAll()
                : reservaRepository.findByVisitanteEmail(requesterEmail);

        return reservas.stream().map(reserva -> toResponseDto(reserva, requesterIsAdmin)).toList();
    }

    @Override
    @Transactional
    public ReservaResponseDto cancelar(UUID id, String requesterEmail, boolean requesterIsAdmin) {
        planificacion.bloquear();
        Reserva reserva = buscarPorId(id);

        if (!requesterIsAdmin && !reserva.getVisitante().getEmail().equals(requesterEmail)) {
            throw new AccessDeniedException("No podes cancelar una reserva que no es tuya.");
        }

        if (reserva.getEstado() == ReservaEstado.CANCELADA) {
            throw new ValidationException("La reserva ya estaba cancelada.");
        }

        // Con el vehiculo adentro no hay nada que cancelar: lo que corresponde
        // es registrar el egreso, que es lo que libera la cochera.
        if (reserva.getEstado() == ReservaEstado.EN_CURSO) {
            throw new ValidationException(
                    "La reserva está en curso: el vehículo ya ingresó. Registrá el egreso en lugar de cancelarla.");
        }

        if (reserva.getEstado() == ReservaEstado.VENCIDA
                || (reserva.getEstado() == ReservaEstado.CONFIRMADA
                && reglasAcceso.ingresoVencido(reserva, reglasAcceso.ahora()))) {
            throw new ValidationException("La reserva venció sin que se registrara el ingreso, no se puede cancelar.");
        }

        if (reserva.getEstado() == ReservaEstado.FINALIZADA || !reserva.getHasta().isAfter(reglasAcceso.ahora())) {
            throw new ValidationException("La reserva ya termino, no se puede cancelar.");
        }

        Visitante actor = requesterIsAdmin ? buscarActor(requesterEmail) : reserva.getVisitante();
        reserva.setEstado(ReservaEstado.CANCELADA);
        reserva.registrarAccion(ReservaAccion.CANCELACION, actor, requesterIsAdmin);

        reservaRepository.save(reserva);
        planificacion.promover();
        return toResponseDto(reserva, requesterIsAdmin);
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

    /**
     * Una cochera ACCESIBLE solo la puede usar una persona con discapacidad.
     *
     * <p>Es una regla sobre la PERSONA, distinta de {@link #validarCompatibilidad}
     * que es sobre el VEHICULO: una cochera ACCESIBLE sigue aceptando cualquier
     * tipo de vehiculo, pero ahora exige ademas que quien reserva este
     * habilitado.
     *
     * <p>{@code visitante} es siempre el dueño de la reserva (ya resuelto por
     * {@link #resolverVisitante}). Cuando un ADMIN reserva en nombre de otro,
     * lo que cuenta es la verificacion de esa otra persona, no la del admin que
     * esta cargando la reserva.
     */
    private void validarAccesibilidad(Cochera cochera, Visitante visitante) {
        if (cochera.getTipo() != CocheraTipo.ACCESIBLE) {
            return;
        }
        if (!visitante.puedeUsarCocheraAccesible()) {
            throw new ValidationException(
                    ("La cochera %s es de uso exclusivo para personas con discapacidad, y %s no tiene "
                            + "verificado su certificado.").formatted(cochera.getNumero(), visitante.getNombre()));
        }
    }

    /**
     * La franja tiene que tener duracion positiva y no puede estar entera en el
     * pasado (reservar algo ya terminado no tiene sentido y ademas seria
     * invisible: nunca ocuparia la cochera).
     *
     * <p>Se permite un "desde" pasado para que el admin pueda registrar a
     * alguien que entro hace un rato, y para que el formulario, que arranca en
     * "ahora", no falle por los segundos que tarda el usuario en enviarlo.
     */
    private void validarRango(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null || hasta == null) throw new ValidationException("Indica inicio y fin de la reserva.");
        // La reserva se toma en bloques de 15 minutos. Se valida en el backend
        // y no solo en el tablero del frontend: si la regla vive unicamente en
        // la UI, cualquier cliente que pegue a la API la saltea.
        validarBloqueDe15(desde, "El inicio");
        validarBloqueDe15(hasta, "El fin");

        if (!hasta.isAfter(desde)) {
            throw new ValidationException("El fin de la reserva tiene que ser posterior al inicio.");
        }
        if (!hasta.isAfter(reglasAcceso.ahora())) {
            throw new ValidationException("La reserva no puede terminar en el pasado.");
        }
    }

    private void validarBloqueDe15(LocalDateTime momento, String cual) {
        if (momento.getMinute() % MINUTOS_POR_BLOQUE != 0
                || momento.getSecond() != 0
                || momento.getNano() != 0) {
            throw new ValidationException(
                    "%s de la reserva tiene que caer en un bloque de %d minutos."
                            .formatted(cual, MINUTOS_POR_BLOQUE));
        }
    }

    /**
     * Nadie puede pisarle la cochera a otro. Se compara contra las reservas
     * CONFIRMADAS y contra los vehiculos que estan adentro (EN_CURSO), que
     * ocupan hasta egresar aunque se hayan pasado de hora. Las canceladas,
     * vencidas y finalizadas no ocupan.
     */
    private void validarDisponibilidad(Cochera cochera, LocalDateTime desde, LocalDateTime hasta,
                                       LocalDateTime ahora) {
        boolean ocupada = reservaRepository.existeSolapadaEnCochera(
                cochera.getId(), ReservaEstado.CONFIRMADA, desde, hasta)
                || reservaRepository.existeEnCursoEnCochera(cochera.getId(), desde, hasta, ahora);
        if (ocupada) {
            throw new ValidationException(
                    "La cochera %s ya esta reservada en ese horario.".formatted(cochera.getNumero()));
        }
    }

    /** Un mismo vehiculo no puede estar ocupando dos cocheras a la vez. */
    private void validarVehiculoLibre(Vehiculo vehiculo, LocalDateTime desde, LocalDateTime hasta,
                                      LocalDateTime ahora) {
        boolean comprometido = reservaRepository.existeSolapadaEnVehiculo(
                vehiculo.getId(), ReservaEstado.CONFIRMADA, desde, hasta)
                || reservaRepository.existeSolapadaEnVehiculo(vehiculo.getId(), ReservaEstado.LISTA_ESPERA, desde, hasta)
                || reservaRepository.existeEnCursoConVehiculo(vehiculo.getId(), desde, hasta, ahora);
        if (comprometido) {
            throw new ValidationException(
                    "El vehiculo %s ya tiene otra reserva en ese horario.".formatted(vehiculo.getPatente()));
        }
    }

    private Reserva buscarPorId(UUID id) {
        return reservaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada."));
    }

    private Visitante buscarActor(String email) {
        return visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Cuenta del autor no encontrada."));
    }

    private ReservaResponseDto toResponseDto(Reserva reserva, boolean requesterIsAdmin) {
        VisitanteResponseDto visitanteDto = new VisitanteResponseDto(
                reserva.getVisitante().getId(),
                reserva.getVisitante().getNombre(),
                reserva.getVisitante().getDocumento(),
                reserva.getVisitante().getTelefono(),
                reserva.getVisitante().getEmail(),
                reserva.getVisitante().puedeUsarCocheraAccesible());

        VehiculoResponseDto vehiculoDto = new VehiculoResponseDto(
                reserva.getVehiculo().getId(),
                reserva.getVehiculo().getPatente(),
                reserva.getVehiculo().getTipo(),
                reserva.getVisitante().getId());

        CocheraResponseDto cocheraDto = reserva.getCochera() == null ? null : new CocheraResponseDto(
                reserva.getCochera().getId(),
                reserva.getCochera().getNumero(),
                reserva.getCochera().getSector(),
                reserva.getCochera().getTipo(),
                reserva.getCochera().getEstado(),
                null);

        return new ReservaResponseDto(
                reserva.getId(),
                reserva.getDesde(),
                reserva.getHasta(),
                visitanteDto,
                vehiculoDto,
                cocheraDto,
                reserva.getEstado(),
                reserva.getModalidad(),
                reserva.getFechaCreacion(),
                reserva.getPrecioTotal(),
                reserva.getTipoTarifa(),
                reserva.getMotivoCancelacion(),
                toAccesoDto(reserva),
                requesterIsAdmin ? reserva.getHistorial().stream().map(movimiento ->
                        new ReservaMovimientoResponseDto(movimiento.getId(), movimiento.getAccion(),
                                movimiento.getFecha(), movimiento.getActorId(), movimiento.getActorNombre(),
                                movimiento.getActorEmail(), movimiento.getActorRol())).toList() : null,
                reserva.getRecurrenciaId(), reserva.getConfirmadaDesdeEsperaAt(), reserva.getNotificacionLeidaAt());
    }

    /**
     * El codigo viaja solo mientras sirve (CONFIRMADA o EN_CURSO): una reserva
     * cancelada, vencida o terminada no tiene por que seguir mostrandolo.
     */
    private ReservaAccesoDto toAccesoDto(Reserva reserva) {
        boolean vigente = reserva.getEstado() == ReservaEstado.CONFIRMADA
                || reserva.getEstado() == ReservaEstado.EN_CURSO;
        return new ReservaAccesoDto(
                vigente ? reserva.getCodigo() : null,
                reglasAcceso.inicioIngreso(reserva),
                reglasAcceso.finIngreso(reserva),
                reserva.getIngresoAt(),
                reserva.getEgresoAt(),
                reserva.getMinutosExceso());
    }
}
