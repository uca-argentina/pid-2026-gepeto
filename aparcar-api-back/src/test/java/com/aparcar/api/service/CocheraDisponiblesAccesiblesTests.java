package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.CocheraService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del filtro de /disponibles: a un visitante sin discapacidad
 * declarada no se le ofrecen las cocheras ACCESIBLE, que igual no podria
 * reservar.
 */
@UnitTests
public class CocheraDisponiblesAccesiblesTests {

    private static final String EMAIL = "visitante@test.com";

    @Mock
    private CocheraRepository cocheraRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private VisitanteRepository visitanteRepository;

    @InjectMocks
    private CocheraService cocheraService;

    private LocalDateTime desde;
    private LocalDateTime hasta;

    private static Cochera cochera(String numero, CocheraTipo tipo) {
        Cochera c = new Cochera();
        c.setId(UUID.randomUUID());
        c.setNumero(numero);
        c.setSector("Planta Baja");
        c.setTipo(tipo);
        c.setEstado(CocheraEstado.HABILITADA);
        return c;
    }

    private static List<String> numeros(List<CocheraResponseDto> cocheras) {
        return cocheras.stream().map(CocheraResponseDto::numero).sorted().toList();
    }

    @BeforeEach
    void setUp() {
        desde = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        hasta = desde.plusHours(1);

        // Sin reservas: findSolapadas devuelve lista vacia por defecto en un mock.
        lenient().when(cocheraRepository.findByEstado(CocheraEstado.HABILITADA))
                .thenReturn(List.of(cochera("A-01", CocheraTipo.AUTO), cochera("AC-01", CocheraTipo.ACCESIBLE)));
    }

    @Test
    @DisplayName("un anonimo ve tambien las ACCESIBLE: no hay persona contra la cual comparar")
    void anonimoVeLasAccesibles() {
        var disponibles = cocheraService.listarDisponibles(desde, hasta, null, null, false);

        assertEquals(List.of("A-01", "AC-01"), numeros(disponibles));
        verify(visitanteRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("un ADMIN ve tambien las ACCESIBLE, sin consultar su propia cuenta")
    void adminVeLasAccesibles() {
        var disponibles = cocheraService.listarDisponibles(desde, hasta, null, "admin@test.com", true);

        assertEquals(List.of("A-01", "AC-01"), numeros(disponibles));
        verify(visitanteRepository, never()).findByEmail(anyString());
    }

    @Test
    @DisplayName("un visitante con discapacidad declarada ve las ACCESIBLE")
    void visitanteConDiscapacidadVeLasAccesibles() {
        Visitante visitante = new Visitante();
        visitante.setTieneDiscapacidad(true);
        when(visitanteRepository.findByEmail(EMAIL)).thenReturn(Optional.of(visitante));

        var disponibles = cocheraService.listarDisponibles(desde, hasta, null, EMAIL, false);

        assertEquals(List.of("A-01", "AC-01"), numeros(disponibles));
    }

    @Test
    @DisplayName("a un visitante sin discapacidad declarada no se le ofrecen las ACCESIBLE")
    void visitanteSinDiscapacidadNoVeLasAccesibles() {
        Visitante visitante = new Visitante();
        visitante.setTieneDiscapacidad(false);
        when(visitanteRepository.findByEmail(EMAIL)).thenReturn(Optional.of(visitante));

        var disponibles = cocheraService.listarDisponibles(desde, hasta, null, EMAIL, false);

        assertEquals(List.of("A-01"), numeros(disponibles));
    }

    @Test
    @DisplayName("si la cuenta del token no existe, se ocultan las ACCESIBLE (lado conservador)")
    void cuentaInexistenteNoVeLasAccesibles() {
        when(visitanteRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        var disponibles = cocheraService.listarDisponibles(desde, hasta, null, EMAIL, false);

        assertEquals(List.of("A-01"), numeros(disponibles));
    }

    @Test
    @DisplayName("la version sin datos de quien pide sigue devolviendo las ACCESIBLE")
    void versionSinRequesterNoFiltra() {
        var disponibles = cocheraService.listarDisponibles(desde, hasta, null);

        assertEquals(List.of("A-01", "AC-01"), numeros(disponibles));
    }
}