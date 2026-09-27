package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Caja blanca de la regla "una cochera ACCESIBLE solo la puede reservar una
 * persona con discapacidad declarada".
 *
 * <p>Es una regla sobre la PERSONA, que se suma (no reemplaza) a la de
 * compatibilidad de VEHICULO que ya cubre ReservaServiceTests.
 */
@UnitTests
public class ReservaAccesibilidadTests {

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
    private Cochera cochera;
    private ReservaRequestDto dto;

    @BeforeEach
    void setUp() {
        visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setNombre("Juan Perez");
        visitante.setEmail(VISITANTE_EMAIL);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(UUID.randomUUID());
        vehiculo.setPatente("ABC123");
        vehiculo.setVisitante(visitante);
        vehiculo.setTipo(VehiculoTipo.AUTO);

        cochera = new Cochera();
        cochera.setId(UUID.randomUUID());
        cochera.setNumero("AC-01");
        cochera.setSector("Planta Baja");
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera.setTipo(CocheraTipo.ACCESIBLE);

        // Franja futura y alineada al bloque de 15 minutos, para que ninguna
        // otra validacion se meta en el medio.
        LocalDateTime desde = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);

        dto = new ReservaRequestDto();
        dto.setVisitanteId(visitante.getId());
        dto.setVehiculoId(vehiculo.getId());
        dto.setCocheraId(cochera.getId());
        dto.setDesde(desde);
        dto.setHasta(desde.plusHours(1));

        lenient().when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        lenient().when(visitanteRepository.findByEmail(VISITANTE_EMAIL)).thenReturn(Optional.of(visitante));
        lenient().when(vehiculoRepository.findById(vehiculo.getId())).thenReturn(Optional.of(vehiculo));
        lenient().when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        lenient().when(reservaRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("un visitante sin discapacidad declarada no puede reservar una cochera ACCESIBLE")
    void visitanteSinDiscapacidadNoPuedeReservarAccesible() {
        visitante.setTieneDiscapacidad(false);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> reservaService.crear(dto, VISITANTE_EMAIL, false));

        assertTrue(ex.getMessage().contains("AC-01"));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("un visitante con discapacidad declarada puede reservar una cochera ACCESIBLE")
    void visitanteConDiscapacidadPuedeReservarAccesible() {
        visitante.setTieneDiscapacidad(true);

        var response = reservaService.crear(dto, VISITANTE_EMAIL, false);

        assertEquals(ReservaEstado.CONFIRMADA, response.estado());
        assertTrue(response.visitante().tieneDiscapacidad());
    }

    @Test
    @DisplayName("si un ADMIN reserva para otro, cuenta la declaracion del dueño: sin ella se rechaza")
    void adminReservandoParaOtroSinDiscapacidadSeRechaza() {
        visitante.setTieneDiscapacidad(false);

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, ADMIN_EMAIL, true));
        verify(reservaRepository, never()).save(any());
    }

    @Test
    @DisplayName("si un ADMIN reserva para otro con discapacidad declarada, se permite sin mirar la cuenta del admin")
    void adminReservandoParaOtroConDiscapacidadSePermite() {
        visitante.setTieneDiscapacidad(true);

        var response = reservaService.crear(dto, ADMIN_EMAIL, true);

        assertEquals(ReservaEstado.CONFIRMADA, response.estado());
        assertEquals(visitante.getId(), response.visitante().id());
        // La cuenta del admin ni siquiera se consulta: el dueño sale del dto.
        verify(visitanteRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("la regla no afecta a las cocheras que no son ACCESIBLE")
    void cocheraComunNoExigeDiscapacidad() {
        visitante.setTieneDiscapacidad(false);
        cochera.setTipo(CocheraTipo.AUTO);

        var response = reservaService.crear(dto, VISITANTE_EMAIL, false);

        assertEquals(ReservaEstado.CONFIRMADA, response.estado());
    }

    @Test
    @DisplayName("un flag en null se trata como 'sin discapacidad declarada'")
    void flagNullSeTrataComoFalse() {
        visitante.setTieneDiscapacidad(null);

        assertThrows(ValidationException.class, () -> reservaService.crear(dto, VISITANTE_EMAIL, false));
    }
}