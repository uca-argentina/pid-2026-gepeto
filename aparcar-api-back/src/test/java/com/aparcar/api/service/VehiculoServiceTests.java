package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.VehiculoRequestDto;
import com.aparcar.api.dto.reserva.VehiculoUpdateDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.VehiculoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTests
public class VehiculoServiceTests {

    @Mock
    private VehiculoRepository vehiculoRepository;

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private VehiculoService vehiculoService;

    private Visitante visitante;
    private VehiculoRequestDto dto;

    @BeforeEach
    void setUp() {
        visitante = new Visitante();
        visitante.setId(UUID.randomUUID());

        dto = new VehiculoRequestDto();
        dto.setPatente("abc123");
        dto.setTipo(VehiculoTipo.AUTO);
        dto.setVisitanteId(visitante.getId());
    }

    @Test
    @DisplayName("crear lanza NotFoundException si el visitante no existe")
    void crearLanzaNotFoundExceptionSiVisitanteNoExiste() {
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> vehiculoService.crear(dto));
    }

    @Test
    @DisplayName("crear lanza ValidationException si ya existe un vehiculo con esa patente")
    void crearLanzaValidationExceptionSiPatenteYaExiste() {
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        when(vehiculoRepository.existsByPatente("ABC123")).thenReturn(true);

        assertThrows(ValidationException.class, () -> vehiculoService.crear(dto));
    }

    @Test
    @DisplayName("crear normaliza la patente a mayusculas antes de guardar")
    void crearNormalizaPatenteAMayusculas() {
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        when(vehiculoRepository.existsByPatente("ABC123")).thenReturn(false);
        when(vehiculoRepository.save(any())).thenAnswer(i -> {
            Vehiculo v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = vehiculoService.crear(dto);

        assertEquals("ABC123", response.patente());

        ArgumentCaptor<Vehiculo> captor = ArgumentCaptor.forClass(Vehiculo.class);
        verify(vehiculoRepository).save(captor.capture());
        assertEquals("ABC123", captor.getValue().getPatente());
    }

    @Test
    @DisplayName("obtenerPorId lanza NotFoundException si el vehiculo no existe")
    void obtenerPorIdLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(vehiculoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> vehiculoService.obtenerPorId(id));
    }

    @Test
    @DisplayName("listarPorVisitante devuelve solo los vehiculos de ese visitante")
    void listarPorVisitanteDevuelveVehiculosDelVisitante() {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(UUID.randomUUID());
        vehiculo.setPatente("ABC123");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(visitante);
        when(vehiculoRepository.findByVisitanteId(visitante.getId())).thenReturn(List.of(vehiculo));

        var response = vehiculoService.listarPorVisitante(visitante.getId());

        assertEquals(1, response.size());
        assertEquals(visitante.getId(), response.get(0).visitanteId());
    }

    @Test
    @DisplayName("editar lanza AccessDeniedException si quien pide no es ADMIN ni el dueño")
    void editarLanzaAccessDeniedExceptionSiNoEsElDueño() {
        Visitante dueño = new Visitante();
        dueño.setEmail("dueño@test.com");
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("ABC123");
        vehiculo.setVisitante(dueño);

        VehiculoUpdateDto dto = new VehiculoUpdateDto();
        dto.setPatente("ABC123");
        dto.setTipo(VehiculoTipo.AUTO);

        when(vehiculoRepository.findById(any())).thenReturn(Optional.of(vehiculo));

        assertThrows(AccessDeniedException.class,
                () -> vehiculoService.editar(UUID.randomUUID(), dto, "otro@test.com", false));
    }

    @Test
    @DisplayName("editar permite al ADMIN modificar un vehiculo que no es suyo")
    void editarPermiteAlAdminModificarCualquierVehiculo() {
        Visitante dueño = new Visitante();
        dueño.setEmail("dueño@test.com");
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("ABC123");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(dueño);

        VehiculoUpdateDto dto = new VehiculoUpdateDto();
        // Formato Mercosur de MOTO real (1 letra + 3 numeros + 3 letras), no
        // el de auto -- antes decia "XYZ999", que no es valido para MOTO.
        dto.setPatente("A123BCD");
        dto.setTipo(VehiculoTipo.MOTO);

        when(vehiculoRepository.findById(any())).thenReturn(Optional.of(vehiculo));
        when(vehiculoRepository.existsByPatente("A123BCD")).thenReturn(false);
        when(vehiculoRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = vehiculoService.editar(UUID.randomUUID(), dto, "admin@test.com", true);

        assertEquals("A123BCD", result.patente());
    }

    @Test
    @DisplayName("eliminar lanza ValidationException si el vehiculo tiene reservas asociadas")
    void eliminarLanzaValidationExceptionSiTieneReservas() {
        Visitante dueño = new Visitante();
        dueño.setEmail("dueño@test.com");
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setVisitante(dueño);
        UUID id = UUID.randomUUID();

        when(vehiculoRepository.findById(id)).thenReturn(Optional.of(vehiculo));
        when(reservaRepository.existsByVehiculoId(id)).thenReturn(true);

        assertThrows(ValidationException.class, () -> vehiculoService.eliminar(id, "dueño@test.com", false));
    }

    // ---- Validacion de patente segun tipo ----

    @ParameterizedTest(name = "AUTO acepta patente valida: {0}")
    @DisplayName("crear acepta ambos formatos vigentes de patente para AUTO")
    @CsvSource({"ABC123", "AB123CD"})
    void crearAceptaAmbosFormatosParaAuto(String patente) {
        dto.setPatente(patente);
        dto.setTipo(VehiculoTipo.AUTO);
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        when(vehiculoRepository.existsByPatente(patente)).thenReturn(false);
        when(vehiculoRepository.save(any())).thenAnswer(i -> {
            Vehiculo v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = vehiculoService.crear(dto);

        assertEquals(patente, response.patente());
    }

    @ParameterizedTest(name = "MOTO acepta patente valida: {0}")
    @DisplayName("crear acepta ambos formatos vigentes de patente para MOTO")
    @CsvSource({"123ABC", "A123BCD"})
    void crearAceptaAmbosFormatosParaMoto(String patente) {
        dto.setPatente(patente);
        dto.setTipo(VehiculoTipo.MOTO);
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        when(vehiculoRepository.existsByPatente(patente)).thenReturn(false);
        when(vehiculoRepository.save(any())).thenAnswer(i -> {
            Vehiculo v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = vehiculoService.crear(dto);

        assertEquals(patente, response.patente());
    }

    @ParameterizedTest(name = "CARGA acepta patente valida: {0}")
    @DisplayName("crear acepta ambos formatos vigentes de patente para CARGA (mismo esquema que AUTO)")
    @CsvSource({"ABC123", "AB123CD"})
    void crearAceptaAmbosFormatosParaCarga(String patente) {
        dto.setPatente(patente);
        dto.setTipo(VehiculoTipo.CARGA);
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));
        when(vehiculoRepository.existsByPatente(patente)).thenReturn(false);
        when(vehiculoRepository.save(any())).thenAnswer(i -> {
            Vehiculo v = i.getArgument(0);
            v.setId(UUID.randomUUID());
            return v;
        });

        var response = vehiculoService.crear(dto);

        assertEquals(patente, response.patente());
    }

    @Test
    @DisplayName("crear rechaza una patente con formato de auto para un vehiculo MOTO")
    void crearRechazaFormatoDeAutoParaMoto() {
        dto.setPatente("ABC123");
        dto.setTipo(VehiculoTipo.MOTO);
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));

        assertThrows(ValidationException.class, () -> vehiculoService.crear(dto));
    }

    @Test
    @DisplayName("crear rechaza una patente con formato de moto para un vehiculo AUTO")
    void crearRechazaFormatoDeMotoParaAuto() {
        dto.setPatente("123ABC");
        dto.setTipo(VehiculoTipo.AUTO);
        when(visitanteRepository.findById(visitante.getId())).thenReturn(Optional.of(visitante));

        assertThrows(ValidationException.class, () -> vehiculoService.crear(dto));
    }
}