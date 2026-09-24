package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.CocheraRequestDto;
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
import com.aparcar.api.service.impl.CocheraService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTests
public class CocheraServiceTests {

    @Mock
    private CocheraRepository cocheraRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @InjectMocks
    private CocheraService cocheraService;

    private Cochera cocheraAuto(String numero) {
        Cochera c = new Cochera();
        c.setId(UUID.randomUUID());
        c.setNumero(numero);
        c.setSector("Planta Baja");
        c.setTipo(CocheraTipo.AUTO);
        c.setEstado(CocheraEstado.HABILITADA);
        return c;
    }

    @Test
    @DisplayName("crear lanza ValidationException si ya existe una cochera con ese numero")
    void crearLanzaValidationExceptionSiNumeroYaExiste() {
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-01");
        dto.setSector("Planta Baja");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.existsByNumero("A-01")).thenReturn(true);

        assertThrows(ValidationException.class, () -> cocheraService.crear(dto));
    }

    @Test
    @DisplayName("crear guarda la cochera cuando el numero no esta repetido")
    void crearGuardaCocheraCuandoNumeroNoEstaRepetido() {
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-01");
        dto.setSector("Planta Baja");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.existsByNumero("A-01")).thenReturn(false);
        when(cocheraRepository.save(any())).thenAnswer(i -> {
            Cochera c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var response = cocheraService.crear(dto);

        assertEquals("A-01", response.numero());
    }

    @Test
    @DisplayName("obtenerPorId lanza NotFoundException si no existe")
    void obtenerPorIdLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(cocheraRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> cocheraService.obtenerPorId(id));
    }

    @Test
    @DisplayName("obtenerPorId devuelve la cochera cuando existe")
    void obtenerPorIdDevuelveLaCocheraCuandoExiste() {
        Cochera cochera = cocheraAuto("A-01");
        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));

        var response = cocheraService.obtenerPorId(cochera.getId());

        assertEquals("A-01", response.numero());
    }

    @Test
    @DisplayName("editar lanza NotFoundException si no existe")
    void editarLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-01");
        dto.setSector("Planta Baja");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> cocheraService.editar(id, dto));
    }

    @Test
    @DisplayName("editar lanza ValidationException si el nuevo numero ya esta en uso por otra cochera")
    void editarLanzaValidationExceptionSiNumeroYaEstaEnUso() {
        Cochera cochera = cocheraAuto("A-01");
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-02");
        dto.setSector("Planta Baja");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        when(cocheraRepository.existsByNumero("A-02")).thenReturn(true);

        assertThrows(ValidationException.class, () -> cocheraService.editar(cochera.getId(), dto));
    }

    @Test
    @DisplayName("editar permite guardar sin chequear duplicados si el numero no cambia")
    void editarPermiteGuardarSiNumeroNoCambia() {
        Cochera cochera = cocheraAuto("A-01");
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-01");
        dto.setSector("Subsuelo");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.DESHABILITADA);

        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        when(cocheraRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var response = cocheraService.editar(cochera.getId(), dto);

        assertEquals("Subsuelo", response.sector());
        assertEquals(CocheraEstado.DESHABILITADA, response.estado());
    }

    @Test
    @DisplayName("eliminar lanza NotFoundException si no existe")
    void eliminarLanzaNotFoundExceptionSiNoExiste() {
        UUID id = UUID.randomUUID();
        when(cocheraRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> cocheraService.eliminar(id));
    }

    @Test
    @DisplayName("eliminar lanza ValidationException si la cochera tiene reservas asociadas")
    void eliminarLanzaValidationExceptionSiTieneReservas() {
        Cochera cochera = cocheraAuto("A-01");
        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        when(reservaRepository.existsByCocheraId(cochera.getId())).thenReturn(true);

        assertThrows(ValidationException.class, () -> cocheraService.eliminar(cochera.getId()));
    }

    @Test
    @DisplayName("eliminar borra la cochera cuando no tiene reservas asociadas")
    void eliminarBorraLaCocheraCuandoNoTieneReservas() {
        Cochera cochera = cocheraAuto("A-01");
        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        when(reservaRepository.existsByCocheraId(cochera.getId())).thenReturn(false);

        cocheraService.eliminar(cochera.getId());
    }

    @Test
    @DisplayName("listarDisponibles excluye cocheras con una reserva confirmada en esa fecha")
    void listarDisponiblesExcluyeCocherasReservadas() {
        Cochera libre = cocheraAuto("A-01");
        Cochera ocupada = cocheraAuto("A-02");
        LocalDate fecha = LocalDate.now();

        Reserva reserva = new Reserva();
        reserva.setCochera(ocupada);
        reserva.setEstado(ReservaEstado.CONFIRMADA);

        when(cocheraRepository.findByEstado(CocheraEstado.HABILITADA)).thenReturn(List.of(libre, ocupada));
        when(reservaRepository.findByFechaAndEstado(fecha, ReservaEstado.CONFIRMADA)).thenReturn(List.of(reserva));

        var disponibles = cocheraService.listarDisponibles(fecha, null);

        assertEquals(1, disponibles.size());
        assertEquals("A-01", disponibles.get(0).numero());
    }

    @Test
    @DisplayName("listarDisponibles filtra por tipo exacto de vehiculo cuando se indica")
    void listarDisponiblesFiltraPorTipoExacto() {
        Cochera cocheraAuto = cocheraAuto("A-01");
        Cochera cocheraMoto = cocheraAuto("M-01");
        cocheraMoto.setTipo(CocheraTipo.MOTO);
        LocalDate fecha = LocalDate.now();

        when(cocheraRepository.findByEstado(CocheraEstado.HABILITADA)).thenReturn(List.of(cocheraAuto, cocheraMoto));
        when(reservaRepository.findByFechaAndEstado(fecha, ReservaEstado.CONFIRMADA)).thenReturn(List.of());

        var disponibles = cocheraService.listarDisponibles(fecha, VehiculoTipo.MOTO);

        assertEquals(1, disponibles.size());
        assertEquals("M-01", disponibles.get(0).numero());
    }

    @Test
    @DisplayName("listarDisponibles incluye cocheras ACCESIBLE sin importar el tipo de vehiculo")
    void listarDisponiblesIncluyeAccesibleParaCualquierTipo() {
        Cochera accesible = cocheraAuto("AC-01");
        accesible.setTipo(CocheraTipo.ACCESIBLE);
        LocalDate fecha = LocalDate.now();

        when(cocheraRepository.findByEstado(CocheraEstado.HABILITADA)).thenReturn(List.of(accesible));
        when(reservaRepository.findByFechaAndEstado(fecha, ReservaEstado.CONFIRMADA)).thenReturn(List.of());

        var disponibles = cocheraService.listarDisponibles(fecha, VehiculoTipo.CARGA);

        assertEquals(1, disponibles.size());
        assertTrue(disponibles.stream().anyMatch(c -> c.numero().equals("AC-01")));
    }

    @Test
    @DisplayName("editar cancela las reservas CONFIRMADA de la cochera al pasarla a DESHABILITADA")
    void editarCancelaReservasConfirmadasAlDeshabilitar() {
        Cochera cochera = cocheraAuto("A-01");
        CocheraRequestDto dto = new CocheraRequestDto();
        dto.setNumero("A-01");
        dto.setSector("Planta Baja");
        dto.setTipo(CocheraTipo.AUTO);
        dto.setEstado(CocheraEstado.DESHABILITADA);

        Reserva reservaConfirmada = new Reserva();
        reservaConfirmada.setEstado(ReservaEstado.CONFIRMADA);

        when(cocheraRepository.findById(cochera.getId())).thenReturn(Optional.of(cochera));
        when(cocheraRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservaRepository.findByCocheraIdAndEstado(cochera.getId(), ReservaEstado.CONFIRMADA))
                .thenReturn(List.of(reservaConfirmada));

        cocheraService.editar(cochera.getId(), dto);

        assertEquals(ReservaEstado.CANCELADA, reservaConfirmada.getEstado());
    }

    @Test
    @DisplayName("listar sin filtros devuelve todas las cocheras con disponibleEnFecha en null")
    void listarSinFiltrosDevuelveTodasConDisponibleEnFechaNull() {
        Cochera a01 = cocheraAuto("A-01");
        when(cocheraRepository.buscar(null, null, null)).thenReturn(List.of(a01));

        var resultado = cocheraService.listar(null, null, null, null, null);

        assertEquals(1, resultado.size());
        assertEquals(null, resultado.get(0).disponibleEnFecha());
    }

    @Test
    @DisplayName("listar trata un sector en blanco como si no se hubiera pasado filtro")
    void listarTrataSectorEnBlancoComoNull() {
        when(cocheraRepository.buscar(null, CocheraTipo.AUTO, null)).thenReturn(List.of());

        cocheraService.listar("   ", CocheraTipo.AUTO, null, null, null);

        verify(cocheraRepository).buscar(null, CocheraTipo.AUTO, null);
    }

    @Test
    @DisplayName("listar con fecha marca disponibleEnFecha=false para una cochera con reserva confirmada ese dia")
    void listarConFechaMarcaOcupadaCorrectamente() {
        Cochera libre = cocheraAuto("A-01");
        Cochera ocupada = cocheraAuto("A-02");
        LocalDateTime desde = LocalDateTime.now();
        LocalDateTime hasta = desde.plusHours(2);

        Reserva reserva = new Reserva();
        reserva.setCochera(ocupada);
        reserva.setEstado(ReservaEstado.CONFIRMADA);

        when(cocheraRepository.buscar(null, null, null)).thenReturn(List.of(libre, ocupada));
        when(reservaRepository.findSolapadas(ReservaEstado.CONFIRMADA, desde, hasta)).thenReturn(List.of(reserva));

        var resultado = cocheraService.listar(null, null, null, desde, hasta);

        var dtoLibre = resultado.stream().filter(c -> c.numero().equals("A-01")).findFirst().orElseThrow();
        var dtoOcupada = resultado.stream().filter(c -> c.numero().equals("A-02")).findFirst().orElseThrow();
        assertEquals(true, dtoLibre.disponibleEnFecha());
        assertEquals(false, dtoOcupada.disponibleEnFecha());
    }

    // ---- crearEnLote ----

    @Test
    @DisplayName("crearEnLote lanza ValidationException si la lista esta vacia")
    void crearEnLoteLanzaValidationExceptionSiListaVacia() {
        assertThrows(ValidationException.class, () -> cocheraService.crearEnLote(List.of()));
    }

    @Test
    @DisplayName("crearEnLote lanza ValidationException si hay un numero repetido dentro del propio lote")
    void crearEnLoteLanzaValidationExceptionSiNumeroRepetidoEnElLote() {
        CocheraRequestDto dto1 = new CocheraRequestDto();
        dto1.setNumero("A-01");
        dto1.setSector("Planta Baja");
        dto1.setTipo(CocheraTipo.AUTO);
        dto1.setEstado(CocheraEstado.HABILITADA);

        CocheraRequestDto dto2 = new CocheraRequestDto();
        dto2.setNumero("A-01");
        dto2.setSector("Subsuelo");
        dto2.setTipo(CocheraTipo.MOTO);
        dto2.setEstado(CocheraEstado.HABILITADA);

        assertThrows(ValidationException.class, () -> cocheraService.crearEnLote(List.of(dto1, dto2)));
        verify(cocheraRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("crearEnLote lanza ValidationException si un numero ya existe en la base, sin guardar nada")
    void crearEnLoteLanzaValidationExceptionSiNumeroYaExisteEnLaBase() {
        CocheraRequestDto dto1 = new CocheraRequestDto();
        dto1.setNumero("A-01");
        dto1.setSector("Planta Baja");
        dto1.setTipo(CocheraTipo.AUTO);
        dto1.setEstado(CocheraEstado.HABILITADA);

        CocheraRequestDto dto2 = new CocheraRequestDto();
        dto2.setNumero("A-02");
        dto2.setSector("Subsuelo");
        dto2.setTipo(CocheraTipo.MOTO);
        dto2.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.existsByNumero("A-01")).thenReturn(false);
        when(cocheraRepository.existsByNumero("A-02")).thenReturn(true);

        assertThrows(ValidationException.class, () -> cocheraService.crearEnLote(List.of(dto1, dto2)));
        verify(cocheraRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("crearEnLote guarda todas las cocheras del lote cuando son validas")
    void crearEnLoteGuardaTodasLasCocherasCuandoSonValidas() {
        CocheraRequestDto dto1 = new CocheraRequestDto();
        dto1.setNumero("A-01");
        dto1.setSector("Planta Baja");
        dto1.setTipo(CocheraTipo.AUTO);
        dto1.setEstado(CocheraEstado.HABILITADA);

        CocheraRequestDto dto2 = new CocheraRequestDto();
        dto2.setNumero("A-02");
        dto2.setSector("Subsuelo");
        dto2.setTipo(CocheraTipo.MOTO);
        dto2.setEstado(CocheraEstado.HABILITADA);

        when(cocheraRepository.existsByNumero(any())).thenReturn(false);
        when(cocheraRepository.saveAll(any())).thenAnswer(i -> {
            List<Cochera> lista = i.getArgument(0);
            lista.forEach(c -> c.setId(UUID.randomUUID()));
            return lista;
        });

        var resultado = cocheraService.crearEnLote(List.of(dto1, dto2));

        assertEquals(2, resultado.size());
        assertEquals("A-01", resultado.get(0).numero());
        assertEquals("A-02", resultado.get(1).numero());
    }

    // ---- listarSectores ----

    @Test
    @DisplayName("listarSectores delega en el repository")
    void listarSectoresDelegaEnElRepository() {
        when(cocheraRepository.findDistinctSectores()).thenReturn(List.of("Planta Baja", "Subsuelo"));

        var sectores = cocheraService.listarSectores();

        assertEquals(List.of("Planta Baja", "Subsuelo"), sectores);
    }
}