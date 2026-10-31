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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca de que ni el alta que hace el admin ni "editar mi perfil" pueden
 * cambiar el acceso a las cocheras ACCESIBLE: ese dato solo lo cambia la
 * verificacion con certificado ({@code VerificacionDiscapacidadServiceTests}).
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
                Instant.now(), new java.math.BigDecimal("1000.00"), CocheraTipo.AUTO, null, null, List.of(), null, null, null, null);
    }

    private static VisitanteAltaDto alta() {
        VisitanteAltaDto dto = new VisitanteAltaDto();
        dto.setNombre("Juan Perez");
        dto.setDocumento("30111222");
        dto.setEmail(EMAIL);
        dto.setPatente("ABC123");
        dto.setTipoVehiculo(VehiculoTipo.AUTO);
        dto.setCocheraId(UUID.randomUUID());
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

    // Antes el admin podia marcarla en el alta. Ahora el acceso solo lo da la
    // verificacion del certificado: el alta no tiene el campo.
    @Test
    @DisplayName("altaConReserva crea la cuenta sin acceso a cocheras accesibles: eso lo da la verificacion")
    void altaNaceSinAcceso() {
        var response = visitanteService.altaConReserva(alta(), "admin@test.com");

        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        assertFalse(captor.getValue().getTieneDiscapacidad());
        assertFalse(response.visitante().tieneDiscapacidad());
    }

    @Test
    @DisplayName("el alta del admin ya no acepta declarar la discapacidad")
    void altaNoTieneElCampo() {
        assertThrows(NoSuchMethodException.class,
                () -> VisitanteAltaDto.class.getMethod("setTieneDiscapacidad", Boolean.class));
    }

    // ---- Editar mi perfil ----

    @Test
    @DisplayName("actualizarPropio no le da acceso a quien no esta verificado")
    void actualizarPropioNoHabilita() {
        Visitante visitante = cuentaGuardada(false);
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail(EMAIL);

        var response = visitanteService.actualizarPropio(EMAIL, update);

        assertFalse(visitante.getTieneDiscapacidad());
        assertFalse(response.tieneDiscapacidad());
        assertThrows(NoSuchMethodException.class,
                () -> VisitanteUpdateDto.class.getMethod("setTieneDiscapacidad", Boolean.class));
    }

    @Test
    @DisplayName("actualizarPropio no le quita el acceso a quien esta verificado")
    void actualizarPropioNoDeshabilita() {
        Visitante visitante = cuentaGuardada(true);
        VisitanteUpdateDto update = new VisitanteUpdateDto();
        update.setEmail(EMAIL);
        update.setTelefono("11-4444-5555");

        var response = visitanteService.actualizarPropio(EMAIL, update);

        assertTrue(visitante.getTieneDiscapacidad());
        assertTrue(response.tieneDiscapacidad());
    }

    @Test
    @DisplayName("obtenerPropio informa el acceso actual (derivado de la verificacion)")
    void obtenerPropioDevuelveElAcceso() {
        cuentaGuardada(true);

        assertTrue(visitanteService.obtenerPropio(EMAIL).tieneDiscapacidad());
    }
}
