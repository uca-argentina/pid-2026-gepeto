package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.ReservaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTests
public class ReservaServiceTests {

    private static final String ADMIN_EMAIL = "admin@test.com";
    private static final String VISITANTE_EMAIL = "visitante@test.com";

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private VehiculoRepository vehiculoRepository;

    @Mock
    private CocheraRepository cocheraRepository;

    @InjectMocks
    private ReservaService reservaService;

    private Visitante visitante;
    private Vehiculo vehiculo;
    private Cochera cochera;
    private ReservaRequestDto dto;

    @BeforeEach
    void setUp() {
        visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setEmail(VISITANTE_EMAIL);

        vehiculo = new Vehiculo();
        vehiculo.setId(UUID.randomUUID());
        vehiculo.setVisitante(visitante);
        vehiculo.setTipo(VehiculoTipo.AUTO);

        cochera = new Cochera();
        cochera.setId(UUID.randomUUID());
        cochera.setNumero("A-01");
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera.setTipo(CocheraTipo.AUTO);

        dto = new ReservaRequestDto();
        dto.setVisitanteId(visitante.getId());
        dto.setVehiculoId(vehiculo.getId());
        dto.setCocheraId(cochera.getId());
        dto.setFecha(LocalDate.now());

        // lenient: algunos tests fallan antes de llegar a estos lookups, y en
        // modo estricto Mockito marcaria esos stubs como "unnecessary"
        lenient().when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        lenient().when(vehiculoRepository.findById(vehiculo.getId())).thenReturn(Optional.of(vehiculo));
        lenient().when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
    }

    @Test
    @DisplayName("crear lanza NotFoundException si el visitante no existe")
    void crearLanzaNotFoundExceptionSiVisitanteNoExiste() {
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
    }

    @Test
    @DisplayName("crear lanza ValidationException si el vehiculo no pertenece al visitante")
    void crearLanzaValidationExceptionSiVehiculoNoPerteneceAlVisitante() {
        vehiculo.setVisitante(new Visitante());
        vehiculo.getVisitante().setId(UUID.randomUUID());

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
    }

    @Test
    @DisplayName("crear lanza ValidationException si el tipo de cochera no es compatible con el vehiculo")
    void crearLanzaValidationExceptionSiTiposNoSonCompatibles() {
        cochera.setTipo(CocheraTipo.MOTO);

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
    }

    @Test
    @DisplayName("crear permite una cochera ACCESIBLE para cualquier tipo de vehiculo")
    void crearPermiteCocheraAccesibleParaCualquierVehiculo() {
        cochera.setTipo(CocheraTipo.ACCESIBLE);
        when(reservaRepository.existsByCocheraIdAndFechaAndEstado(cochera.getId(), dto.getFecha(), ReservaEstado.CONFIRMADA))
                .thenReturn(false);
        when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(ReservaEstado.CONFIRMADA, reservaService.crear(dto, ADMIN_EMAIL, true).estado());
    }

    @Test
    @DisplayName("crear lanza ValidationException si la cochera ya tiene una reserva confirmada ese dia")
    void crearLanzaValidationExceptionSiCocheraYaEstaReservada() {
        when(reservaRepository.existsByCocheraIdAndFechaAndEstado(cochera.getId(), dto.getFecha(), ReservaEstado.CONFIRMADA))
                .thenReturn(true);

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
    }

    @Test
    @DisplayName("crear guarda la reserva como CONFIRMADA cuando todas las validaciones pasan")
    void crearGuardaReservaConfirmadaCuandoTodoEsValido() {
        when(reservaRepository.existsByCocheraIdAndFechaAndEstado(cochera.getId(), dto.getFecha(), ReservaEstado.CONFIRMADA))
                .thenReturn(false);
        when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = reservaService.crear(dto, ADMIN_EMAIL, true);

        assertEquals(ReservaEstado.CONFIRMADA, response.estado());
        assertEquals(cochera.getId(), response.cochera().id());
        assertEquals(vehiculo.getId(), response.vehiculo().id());
    }

    // Un visitante solo puede reservar a su nombre. Mandar el visitanteId de
    // otro no deberia servirle de nada: el backend usa su cuenta y punto.
    @Test
    @DisplayName("crear ignora el visitanteId del dto cuando quien reserva no es ADMIN")
    void crearIgnoraElVisitanteIdDelDtoSiNoEsAdmin() {
        dto.setVisitanteId(UUID.randomUUID()); // intenta reservar para otro
        when(visitanteRepository.findByEmail(VISITANTE_EMAIL)).thenReturn(Optional.of(visitante));
        when(reservaRepository.existsByCocheraIdAndFechaAndEstado(cochera.getId(), dto.getFecha(), ReservaEstado.CONFIRMADA))
                .thenReturn(false);
        when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = reservaService.crear(dto, VISITANTE_EMAIL, false);

        assertEquals(visitante.getId(), response.visitante().id());
    }

    @Test
    @DisplayName("crear lanza ValidationException si un ADMIN no indica a nombre de quien va la reserva")
    void crearLanzaValidationExceptionSiAdminNoIndicaVisitante() {
        dto.setVisitanteId(null);

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
    }

    @Test
    @DisplayName("listar devuelve todas las reservas cuando quien pide es ADMIN")
    void listarDevuelveTodasParaAdmin() {
        when(reservaRepository.findAll()).thenReturn(List.of(reservaDe(visitante)));

        var response = reservaService.listar(ADMIN_EMAIL, true);

        assertEquals(1, response.size());
        verify(reservaRepository, never()).findByVisitanteEmail(any());
    }

    // El filtro es del backend a proposito: antes GET /reservas devolvia todas
    // las reservas del sistema a cualquier autenticado.
    @Test
    @DisplayName("listar devuelve solo las reservas propias cuando quien pide no es ADMIN")
    void listarDevuelveSoloLasPropiasParaVisitante() {
        when(reservaRepository.findByVisitanteEmail(VISITANTE_EMAIL)).thenReturn(List.of(reservaDe(visitante)));

        var response = reservaService.listar(VISITANTE_EMAIL, false);

        assertEquals(1, response.size());
        verify(reservaRepository, never()).findAll();
    }

    @Test
    @DisplayName("obtenerPorId niega el acceso a una reserva de otro visitante")
    void obtenerPorIdNiegaElAccesoAUnaReservaAjena() {
        Reserva ajena = reservaDe(visitante);
        when(reservaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThrows(AccessDeniedException.class,
                () -> reservaService.obtenerPorId(ajena.getId(), "otro@test.com", false));
    }

    @Test
    @DisplayName("obtenerPorId deja al ADMIN ver cualquier reserva")
    void obtenerPorIdDejaAlAdminVerCualquierReserva() {
        Reserva ajena = reservaDe(visitante);
        when(reservaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertEquals(ajena.getId(), reservaService.obtenerPorId(ajena.getId(), ADMIN_EMAIL, true).id());
    }

    // ---- Cancelar ----

    // Cancelar no borra: deja la fila en CANCELADA para conservar el historial,
    // igual que ya hacia el sistema al deshabilitar una cochera.
    @Test
    @DisplayName("cancelar pasa la reserva a CANCELADA sin borrarla")
    void cancelarPasaLaReservaACancelada() {
        Reserva propia = reservaDe(visitante);
        when(reservaRepository.findById(propia.getId())).thenReturn(Optional.of(propia));
        when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = reservaService.cancelar(propia.getId(), VISITANTE_EMAIL, false);

        assertEquals(ReservaEstado.CANCELADA, response.estado());
        verify(reservaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("cancelar niega el acceso si la reserva es de otro visitante")
    void cancelarNiegaElAccesoAUnaReservaAjena() {
        Reserva ajena = reservaDe(visitante);
        when(reservaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));

        assertThrows(AccessDeniedException.class,
                () -> reservaService.cancelar(ajena.getId(), "otro@test.com", false));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelar deja al ADMIN dar de baja cualquier reserva")
    void cancelarDejaAlAdminDarDeBajaCualquierReserva() {
        Reserva ajena = reservaDe(visitante);
        when(reservaRepository.findById(ajena.getId())).thenReturn(Optional.of(ajena));
        when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        assertEquals(ReservaEstado.CANCELADA,
                reservaService.cancelar(ajena.getId(), ADMIN_EMAIL, true).estado());
    }

    @Test
    @DisplayName("cancelar lanza ValidationException si la reserva ya estaba cancelada")
    void cancelarLanzaValidationExceptionSiYaEstabaCancelada() {
        Reserva propia = reservaDe(visitante);
        propia.setEstado(ReservaEstado.CANCELADA);
        when(reservaRepository.findById(propia.getId())).thenReturn(Optional.of(propia));

        assertThrows(ValidationException.class,
                () -> reservaService.cancelar(propia.getId(), VISITANTE_EMAIL, false));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cancelar lanza NotFoundException si la reserva no existe")
    void cancelarLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(reservaRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> reservaService.cancelar(id, ADMIN_EMAIL, true));
    }

    private Reserva reservaDe(Visitante dueño) {
        Reserva reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setFecha(LocalDate.now());
        reserva.setVisitante(dueño);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reserva;
    }
}
