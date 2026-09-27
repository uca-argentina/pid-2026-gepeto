package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.CocheraAltaPorPlantaDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.CocheraService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca de {@link CocheraService#crearPorPlanta}: validaciones, armado
 * de las cocheras y traduccion del choque por numero duplicado. El calculo
 * fino de los numeros se prueba aparte en {@link NumeracionCocherasTests}.
 */
@UnitTests
public class CocheraAltaPorPlantaTests {

    @Mock
    private CocheraRepository cocheraRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private VisitanteRepository visitanteRepository;

    @InjectMocks
    private CocheraService cocheraService;

    private static CocheraAltaPorPlantaDto dto(String sector, Map<CocheraTipo, Integer> cantidades) {
        CocheraAltaPorPlantaDto dto = new CocheraAltaPorPlantaDto();
        dto.setSector(sector);
        dto.setCantidades(cantidades);
        return dto;
    }

    /** Simula el guardado: le pone id a cada cochera y la devuelve igual. */
    private void guardadoDevuelveLoMismo() {
        when(cocheraRepository.saveAllAndFlush(anyIterable())).thenAnswer(invocacion -> {
            Iterable<Cochera> cocheras = invocacion.getArgument(0);
            cocheras.forEach(c -> c.setId(UUID.randomUUID()));
            return cocheras;
        });
    }

    @Test
    @DisplayName("crea la cantidad pedida de cada tipo, numerada desde 01 si no hay cocheras")
    void creaLaCantidadPedidaDeCadaTipo() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("Planta Baja",
                Map.of(CocheraTipo.AUTO, 2, CocheraTipo.MOTO, 1, CocheraTipo.ACCESIBLE, 1, CocheraTipo.CARGA, 1)));

        assertEquals(List.of("A-01", "A-02", "M-01", "AC-01", "C-01"),
                creadas.stream().map(CocheraResponseDto::numero).toList());
        assertTrue(creadas.stream().allMatch(c -> c.sector().equals("Planta Baja")));
    }

    @Test
    @DisplayName("devuelve las cocheras en orden fijo de tipo, sin importar el orden del mapa")
    void ordenFijoDeTipos() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();
        Map<CocheraTipo, Integer> cantidades = new HashMap<>();
        cantidades.put(CocheraTipo.CARGA, 1);
        cantidades.put(CocheraTipo.AUTO, 1);

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("Subsuelo", cantidades));

        assertEquals(List.of(CocheraTipo.AUTO, CocheraTipo.CARGA),
                creadas.stream().map(CocheraResponseDto::tipo).toList());
    }

    @Test
    @DisplayName("cada tipo continua desde el maximo existente de su propio prefijo")
    void continuaDesdeLosExistentes() {
        when(cocheraRepository.findAllNumeros())
                .thenReturn(List.of("A-01", "A-04", "AC-07", "M-02", "Cochera vieja 12"));
        guardadoDevuelveLoMismo();

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("Planta Baja",
                Map.of(CocheraTipo.AUTO, 2, CocheraTipo.ACCESIBLE, 1, CocheraTipo.CARGA, 1)));

        assertEquals(List.of("A-05", "A-06", "AC-08", "C-01"),
                creadas.stream().map(CocheraResponseDto::numero).toList());
    }

    @Test
    @DisplayName("los tipos con cantidad 0 o null no se crean")
    void tiposEnCeroONullNoSeCrean() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();
        Map<CocheraTipo, Integer> cantidades = new EnumMap<>(CocheraTipo.class);
        cantidades.put(CocheraTipo.AUTO, 0);
        cantidades.put(CocheraTipo.MOTO, 2);
        cantidades.put(CocheraTipo.CARGA, null);

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("Planta Baja", cantidades));

        assertEquals(List.of("M-01", "M-02"), creadas.stream().map(CocheraResponseDto::numero).toList());
    }

    @Test
    @DisplayName("sin estado, las crea HABILITADA")
    void estadoPorDefectoHabilitada() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("Planta Baja", Map.of(CocheraTipo.AUTO, 2)));

        assertTrue(creadas.stream().allMatch(c -> c.estado() == CocheraEstado.HABILITADA));
    }

    @Test
    @DisplayName("respeta el estado inicial si se lo indica")
    void respetaElEstadoIndicado() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();
        CocheraAltaPorPlantaDto dto = dto("Planta Baja", Map.of(CocheraTipo.AUTO, 1));
        dto.setEstado(CocheraEstado.DESHABILITADA);

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto);

        assertEquals(CocheraEstado.DESHABILITADA, creadas.getFirst().estado());
    }

    @Test
    @DisplayName("guarda el sector sin espacios de mas")
    void recortaElSector() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        guardadoDevuelveLoMismo();

        List<CocheraResponseDto> creadas = cocheraService.crearPorPlanta(dto("  Planta Baja  ", Map.of(CocheraTipo.AUTO, 1)));

        assertEquals("Planta Baja", creadas.getFirst().sector());
    }

    @Test
    @DisplayName("rechaza un sector vacio o en blanco sin guardar nada")
    void rechazaSectorVacio() {
        assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("   ", Map.of(CocheraTipo.AUTO, 1))));
        assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto(null, Map.of(CocheraTipo.AUTO, 1))));
        verify(cocheraRepository, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    @DisplayName("rechaza si ningun tipo tiene cantidad mayor a 0")
    void rechazaSiNingunTipoTieneCantidad() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("Planta Baja", Map.of(CocheraTipo.AUTO, 0, CocheraTipo.MOTO, 0))));

        assertEquals("Indica al menos un tipo de cochera con cantidad mayor a 0.", e.getMessage());
        verify(cocheraRepository, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    @DisplayName("rechaza un mapa de cantidades vacio o ausente")
    void rechazaCantidadesVaciasOAusentes() {
        assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("Planta Baja", Map.of())));
        assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("Planta Baja", null)));
        verify(cocheraRepository, never()).saveAllAndFlush(anyIterable());
    }

    @Test
    @DisplayName("rechaza una cantidad negativa aunque otro tipo tenga cantidad valida")
    void rechazaCantidadNegativa() {
        ValidationException e = assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("Planta Baja", Map.of(CocheraTipo.AUTO, 3, CocheraTipo.MOTO, -1))));

        assertTrue(e.getMessage().contains("MOTO"));
        verify(cocheraRepository, never()).saveAllAndFlush(anyIterable());
    }

    // Dos altas simultaneas calculan el mismo maximo y generan el mismo
    // numero: la restriccion UNIQUE hace fallar a la segunda. Tiene que llegar
    // como un 400 entendible, no como un 500.
    @Test
    @DisplayName("si otra alta concurrente se quedo con el numero, avisa para reintentar")
    void choqueConcurrenteSeTraduceAValidationException() {
        when(cocheraRepository.findAllNumeros()).thenReturn(List.of());
        when(cocheraRepository.saveAllAndFlush(anyIterable()))
                .thenThrow(new DataIntegrityViolationException("duplicate key cocheras_numero"));

        ValidationException e = assertThrows(ValidationException.class,
                () -> cocheraService.crearPorPlanta(dto("Planta Baja", Map.of(CocheraTipo.AUTO, 2))));

        assertTrue(e.getMessage().contains("volve a intentarlo"));
    }
}