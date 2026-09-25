package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.auth.ChangePasswordDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VisitanteAltaDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.dto.reserva.VisitanteUpdateDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.ModalidadReserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.VisitanteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTests
public class VisitanteServiceTests {

    private static final UUID COCHERA_ID = UUID.randomUUID();
    private static final UUID VEHICULO_ID = UUID.randomUUID();

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private IVehiculoService vehiculoService;

    @Mock
    private IReservaService reservaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private VisitanteService visitanteService;

    private VisitanteAltaDto dto;

    @BeforeEach
    void setUp() {
        dto = new VisitanteAltaDto();
        dto.setNombre("Juan Perez");
        dto.setDocumento("30111222");
        dto.setEmail("juan@mail.com");
        dto.setTelefono("11-4444-5555");
        dto.setPatente("ABC123");
        dto.setTipoVehiculo(VehiculoTipo.AUTO);
        dto.setCocheraId(COCHERA_ID);

        lenient().when(passwordEncoder.encode(any())).thenAnswer(i -> "hash:" + i.getArgument(0));
        lenient().when(visitanteRepository.save(any())).thenAnswer(i -> {
            Visitante v = i.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
        lenient().when(vehiculoService.crear(any(VehiculoRequestDto.class))).thenAnswer(i -> {
            VehiculoRequestDto v = i.getArgument(0);
            return new VehiculoResponseDto(VEHICULO_ID, v.getPatente(), v.getTipo(), v.getVisitanteId());
        });
        lenient().when(reservaService.crear(any(), any(), anyBoolean())).thenAnswer(i -> unaReserva());
    }

    private static ReservaResponseDto unaReserva() {
        return new ReservaResponseDto(
                UUID.randomUUID(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                new VisitanteResponseDto(UUID.randomUUID(), "Juan Perez", "30111222", null, "juan@mail.com"),
                new VehiculoResponseDto(VEHICULO_ID, "ABC123", VehiculoTipo.AUTO, UUID.randomUUID()),
                new CocheraResponseDto(COCHERA_ID, "A-01", "Planta Baja", CocheraTipo.AUTO, CocheraEstado.HABILITADA, null),
                ReservaEstado.CONFIRMADA,
                ModalidadReserva.FRANJA,
                Instant.now());
    }

    @Test
    @DisplayName("altaConReserva lanza ValidationException si ya existe un visitante con el mismo documento")
    void altaLanzaValidationExceptionSiDocumentoYaExiste() {
        when(visitanteRepository.existsByDocumento(dto.getDocumento())).thenReturn(true);

        assertThrows(ValidationException.class, () -> visitanteService.altaConReserva(dto));
        verify(visitanteRepository, never()).save(any());
    }

    // El email pasó a ser el identificador de login, asi que ahora tambien
    // tiene que ser unico.
    @Test
    @DisplayName("altaConReserva lanza ValidationException si ya existe una cuenta con ese email")
    void altaLanzaValidationExceptionSiEmailYaExiste() {
        when(visitanteRepository.existsByDocumento(dto.getDocumento())).thenReturn(false);
        when(visitanteRepository.existsByEmail(dto.getEmail())).thenReturn(true);

        assertThrows(ValidationException.class, () -> visitanteService.altaConReserva(dto));
        verify(visitanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("altaConReserva usa el documento como contraseña inicial, hasheado")
    void altaUsaElDocumentoComoContraseñaInicial() {
        visitanteService.altaConReserva(dto);

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());

        verify(passwordEncoder).encode("30111222");
        assertEquals("hash:30111222", captor.getValue().getPassword());
    }

    @Test
    @DisplayName("altaConReserva crea la cuenta activa y con rol USER")
    void altaCreaLaCuentaActivaYConRolUser() {
        visitanteService.altaConReserva(dto);

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());

        assertTrue(captor.getValue().getIsActive());
        assertEquals(List.of(AppAuthority.USER), List.copyOf(captor.getValue().getAuthorities()));
    }

    @Test
    @DisplayName("altaConReserva carga el vehiculo a nombre del visitante recien creado")
    void altaCargaElVehiculoANombreDelVisitanteCreado() {
        var response = visitanteService.altaConReserva(dto);

        ArgumentCaptor<VehiculoRequestDto> captor = ArgumentCaptor.forClass(VehiculoRequestDto.class);
        verify(vehiculoService).crear(captor.capture());

        assertEquals("ABC123", captor.getValue().getPatente());
        assertEquals(VehiculoTipo.AUTO, captor.getValue().getTipo());
        assertEquals(response.visitante().id(), captor.getValue().getVisitanteId());
    }

    // Compatibilidad con clientes que no mandan fecha: se reserva para hoy.
    @Test
    @DisplayName("altaConReserva reserva la cochera indicada para hoy")
    void altaReservaLaCocheraParaHoy() {
        visitanteService.altaConReserva(dto);

        ArgumentCaptor<ReservaRequestDto> captor = ArgumentCaptor.forClass(ReservaRequestDto.class);
        verify(reservaService).crear(captor.capture(), any(), anyBoolean());

        // Sin franja explicita, el alta arranca ahora y dura una hora.
        assertEquals(60, java.time.Duration.between(
                captor.getValue().getDesde(), captor.getValue().getHasta()).toMinutes());
        assertEquals(COCHERA_ID, captor.getValue().getCocheraId());
        assertEquals(VEHICULO_ID, captor.getValue().getVehiculoId());
    }

    @Test
    @DisplayName("altaConReserva usa la franja elegida al crear la reserva")
    void altaReservaParaLaFranjaElegida() {
        LocalDateTime desde = LocalDateTime.now().plusDays(7);
        LocalDateTime hasta = desde.plusHours(3);
        dto.setDesde(desde);
        dto.setHasta(hasta);

        visitanteService.altaConReserva(dto);

        ArgumentCaptor<ReservaRequestDto> captor = ArgumentCaptor.forClass(ReservaRequestDto.class);
        verify(reservaService).crear(captor.capture(), any(), anyBoolean());
        assertEquals(desde, captor.getValue().getDesde());
        assertEquals(hasta, captor.getValue().getHasta());
    }

    // El alta es todo o nada: si la cochera ya estaba tomada, la excepcion sale
    // hacia arriba y @Transactional revierte la cuenta y el vehiculo. Sin esto
    // volveriamos a fabricar visitantes fantasma.
    @Test
    @DisplayName("altaConReserva propaga el error de la reserva en vez de dejar la cuenta creada")
    void altaPropagaElErrorDeLaReserva() {
        when(reservaService.crear(any(), any(), anyBoolean()))
                .thenThrow(new ValidationException("La cochera ya tiene una reserva confirmada."));

        assertThrows(ValidationException.class, () -> visitanteService.altaConReserva(dto));
    }

    @Test
    @DisplayName("obtenerPorId lanza NotFoundException si el visitante no existe")
    void obtenerPorIdLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(visitanteRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> visitanteService.obtenerPorId(id));
    }

    @Test
    @DisplayName("obtenerPorId devuelve el visitante cuando existe")
    void obtenerPorIdDevuelveVisitanteCuandoExiste() {
        Visitante visitante = unVisitante();
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));

        var response = visitanteService.obtenerPorId(visitante.getId());

        assertEquals(visitante.getId(), response.id());
    }

    @Test
    @DisplayName("listar devuelve todos los visitantes")
    void listarDevuelveTodosLosVisitantes() {
        when(visitanteRepository.findAll()).thenReturn(List.of(unVisitante()));

        assertEquals(1, visitanteService.listar().size());
    }

    @Test
    @DisplayName("obtenerPropio lanza NotFoundException si no existe una cuenta con ese email")
    void obtenerPropioLanzaNotFoundExceptionSiNoExisteLaCuenta() {
        when(visitanteRepository.findByEmail("visitante@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> visitanteService.obtenerPropio("visitante@test.com"));
    }

    // Ya no hay que "cargar los datos" aparte: la cuenta ES el visitante, asi
    // que buscarla por email alcanza.
    @Test
    @DisplayName("obtenerPropio devuelve los datos de la cuenta autenticada")
    void obtenerPropioDevuelveLosDatosDeLaCuenta() {
        Visitante visitante = unVisitante();
        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));

        var response = visitanteService.obtenerPropio(visitante.getEmail());

        assertEquals("Juan Perez", response.nombre());
        assertEquals("30111222", response.documento());
    }

    @Test
    @DisplayName("actualizarPropio lanza NotFoundException si no existe una cuenta con ese email")
    void actualizarPropioLanzaNotFoundExceptionSiNoExisteLaCuenta() {
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail("nuevo@mail.com");
        when(visitanteRepository.findByEmail("test@mail.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> visitanteService.actualizarPropio("test@mail.com", update));
    }

    @Test
    @DisplayName("actualizarPropio actualiza telefono y email sin tocar nombre ni documento")
    void actualizarPropioActualizaTelefonoYEmail() {
        Visitante visitante = unVisitante();
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setTelefono("11-2222-3333");
        update.setEmail("nuevo@mail.com");

        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));
        when(visitanteRepository.existsByEmail("nuevo@mail.com")).thenReturn(false);

        var result = visitanteService.actualizarPropio(visitante.getEmail(), update);

        assertEquals("Juan Perez", result.nombre());
        assertEquals("30111222", result.documento());
        assertEquals("11-2222-3333", result.telefono());
        assertEquals("nuevo@mail.com", result.email());
    }

    // Cambiar el email cambia el login, asi que no puede pisar el de otra
    // cuenta.
    @Test
    @DisplayName("actualizarPropio rechaza un email que ya usa otra cuenta")
    void actualizarPropioRechazaUnEmailYaEnUso() {
        Visitante visitante = unVisitante();
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail("ocupado@mail.com");

        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));
        when(visitanteRepository.existsByEmail("ocupado@mail.com")).thenReturn(true);

        assertThrows(ValidationException.class,
                () -> visitanteService.actualizarPropio(visitante.getEmail(), update));
    }

    // ---- Cambio de contraseña ----

    // Pedir la actual es lo que evita que alguien que agarre una sesion abierta
    // deje al dueño afuera de su cuenta.
    @Test
    @DisplayName("cambiarPasswordPropia rechaza el cambio si la contraseña actual no coincide")
    void cambiarPasswordRechazaSiLaActualNoCoincide() {
        Visitante visitante = unVisitante();
        visitante.setPassword("hash-viejo");
        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));
        when(passwordEncoder.matches("equivocada", "hash-viejo")).thenReturn(false);

        assertThrows(ValidationException.class,
                () -> visitanteService.cambiarPasswordPropia(visitante.getEmail(), cambio("equivocada", "nuevaSegura1")));
        verify(visitanteRepository, never()).save(any());
    }

    // Sin esto, "cambiar" por la misma contraseña devolveria exito y el
    // visitante creeria que dejo de usar su documento como clave.
    @Test
    @DisplayName("cambiarPasswordPropia rechaza una contraseña nueva igual a la actual")
    void cambiarPasswordRechazaSiLaNuevaEsIgualALaActual() {
        Visitante visitante = unVisitante();
        visitante.setPassword("hash-viejo");
        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));
        when(passwordEncoder.matches("30111222", "hash-viejo")).thenReturn(true);

        assertThrows(ValidationException.class,
                () -> visitanteService.cambiarPasswordPropia(visitante.getEmail(), cambio("30111222", "30111222")));
        verify(visitanteRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarPasswordPropia guarda la contraseña nueva hasheada")
    void cambiarPasswordGuardaLaNuevaHasheada() {
        Visitante visitante = unVisitante();
        visitante.setPassword("hash-viejo");
        when(visitanteRepository.findByEmail(visitante.getEmail())).thenReturn(Optional.of(visitante));
        when(passwordEncoder.matches("30111222", "hash-viejo")).thenReturn(true);
        when(passwordEncoder.matches("nuevaSegura1", "hash-viejo")).thenReturn(false);

        visitanteService.cambiarPasswordPropia(visitante.getEmail(), cambio("30111222", "nuevaSegura1"));

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        assertEquals("hash:nuevaSegura1", captor.getValue().getPassword());
    }

    @Test
    @DisplayName("cambiarPasswordPropia lanza NotFoundException si no existe una cuenta con ese email")
    void cambiarPasswordLanzaNotFoundExceptionSiNoExisteLaCuenta() {
        when(visitanteRepository.findByEmail("nadie@test.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> visitanteService.cambiarPasswordPropia("nadie@test.com", cambio("x", "nuevaSegura1")));
    }

    private static ChangePasswordDto cambio(String actual, String nueva) {
        ChangePasswordDto dto = new ChangePasswordDto();
        dto.setPasswordActual(actual);
        dto.setPasswordNueva(nueva);
        return dto;
    }

    private Visitante unVisitante() {
        Visitante visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setNombre("Juan Perez");
        visitante.setDocumento("30111222");
        visitante.setEmail("juan@mail.com");
        return visitante;
    }
}
