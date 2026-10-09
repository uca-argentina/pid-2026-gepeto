package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoResponseDto;
import com.aparcar.api.dto.reserva.VisitanteAltaDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.dto.reserva.VisitanteUpdateDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.ModalidadReserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.VehiculoTipo;
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
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca de como entra y se edita la declaracion de discapacidad del
 * visitante: en el alta que hace el admin y en "editar mi perfil".
 */
@UnitTests
public class VisitanteDiscapacidadTests {

    private static final String EMAIL = "juan@mail.com";

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private IVehiculoService vehiculoService;

    @Mock
    private IReservaService reservaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IRevokedUserCache revokedUserCache;

    @InjectMocks
    private VisitanteService visitanteService;

    @BeforeEach
    void setUp() {
        lenient().when(visitanteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
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
            return new VehiculoResponseDto(UUID.randomUUID(), v.getPatente(), v.getTipo(), v.getVisitanteId());
        });
        lenient().when(reservaService.crear(any(), any(), anyBoolean())).thenAnswer(i -> unaReserva());
    }

    private static ReservaResponseDto unaReserva() {
        return new ReservaResponseDto(
                UUID.randomUUID(),
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(1),
                new VisitanteResponseDto(UUID.randomUUID(), "Juan Perez", "30111222", null, EMAIL, false),
                new VehiculoResponseDto(UUID.randomUUID(), "ABC123", VehiculoTipo.AUTO, UUID.randomUUID()),
                new CocheraResponseDto(UUID.randomUUID(), "A-01", "Planta Baja", CocheraTipo.AUTO,
                        CocheraEstado.HABILITADA, null),
                ReservaEstado.CONFIRMADA,
                ModalidadReserva.FRANJA,
                Instant.now(), new java.math.BigDecimal("1000.00"), CocheraTipo.AUTO, null, List.of());
    }

    private static VisitanteAltaDto alta(Boolean tieneDiscapacidad) {
        VisitanteAltaDto dto = new VisitanteAltaDto();
        dto.setNombre("Juan Perez");
        dto.setDocumento("30111222");
        dto.setEmail(EMAIL);
        dto.setPatente("ABC123");
        dto.setTipoVehiculo(VehiculoTipo.AUTO);
        dto.setCocheraId(UUID.randomUUID());
        dto.setTieneDiscapacidad(tieneDiscapacidad);
        return dto;
    }

    private Visitante cuentaGuardada(boolean tieneDiscapacidad) {
        Visitante visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setNombre("Juan Perez");
        visitante.setDocumento("30111222");
        visitante.setEmail(EMAIL);
        visitante.setTieneDiscapacidad(tieneDiscapacidad);
        when(visitanteRepository.findByEmail(EMAIL)).thenReturn(Optional.of(visitante));
        return visitante;
    }

    // ---- Alta del admin ----

    @Test
    @DisplayName("altaConReserva guarda la discapacidad cuando el admin la marca")
    void altaGuardaLaDiscapacidadMarcada() {
        var response = visitanteService.altaConReserva(alta(true), "admin@test.com");

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        assertTrue(captor.getValue().getTieneDiscapacidad());
        assertTrue(response.visitante().tieneDiscapacidad());
    }

    @Test
    @DisplayName("altaConReserva deja la discapacidad en false si el admin no la manda")
    void altaSinElCampoQuedaEnFalse() {
        var response = visitanteService.altaConReserva(alta(null), "admin@test.com");

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        assertFalse(captor.getValue().getTieneDiscapacidad());
        assertFalse(response.visitante().tieneDiscapacidad());
    }

    // ---- Editar mi perfil ----

    @Test
    @DisplayName("actualizarPropio permite al visitante declarar su discapacidad")
    void actualizarPropioDeclaraLaDiscapacidad() {
        Visitante visitante = cuentaGuardada(false);
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail(EMAIL);
        update.setTieneDiscapacidad(true);

        var response = visitanteService.actualizarPropio(EMAIL, update);

        assertTrue(visitante.getTieneDiscapacidad());
        assertTrue(response.tieneDiscapacidad());
    }

    @Test
    @DisplayName("actualizarPropio permite al visitante quitar la declaracion")
    void actualizarPropioQuitaLaDiscapacidad() {
        Visitante visitante = cuentaGuardada(true);
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail(EMAIL);
        update.setTieneDiscapacidad(false);

        var response = visitanteService.actualizarPropio(EMAIL, update);

        assertFalse(visitante.getTieneDiscapacidad());
        assertFalse(response.tieneDiscapacidad());
    }

    @Test
    @DisplayName("actualizarPropio sin el campo no le borra la declaracion a nadie")
    void actualizarPropioSinElCampoNoLaToca() {
        Visitante visitante = cuentaGuardada(true);
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail(EMAIL);
        update.setTelefono("11-4444-5555");

        var response = visitanteService.actualizarPropio(EMAIL, update);

        assertTrue(visitante.getTieneDiscapacidad());
        assertTrue(response.tieneDiscapacidad());
    }

    @Test
    @DisplayName("obtenerPropio devuelve la declaracion actual para precargar el formulario")
    void obtenerPropioDevuelveLaDeclaracion() {
        cuentaGuardada(true);

        assertTrue(visitanteService.obtenerPropio(EMAIL).tieneDiscapacidad());
    }
}
