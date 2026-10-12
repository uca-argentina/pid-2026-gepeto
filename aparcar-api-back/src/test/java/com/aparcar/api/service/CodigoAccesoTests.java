package com.aparcar.api.service;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.service.impl.CodigoAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del codigo corto de acceso: derivacion por HMAC, normalizacion
 * de lo escaneado o tipeado, formato y asignacion con reintento por colision.
 */
@UnitTests
class CodigoAccesoTests {
    private static final byte[] SECRETO = AccesoDePrueba.SECRETO.getBytes(StandardCharsets.UTF_8);
    private static final UUID ID = UUID.fromString("3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33");

    @Mock
    private ReservaRepository reservaRepository;

    private CodigoAcceso codigoAcceso;

    @BeforeEach
    void setUp() {
        codigoAcceso = new CodigoAcceso(AccesoDePrueba.propiedades(), reservaRepository);
    }

    // ---- Derivacion ----

    @Test
    @DisplayName("el mismo id con el mismo secreto da siempre el mismo codigo")
    void esDeterministico() {
        assertEquals(CodigoAcceso.derivar(SECRETO, ID, 0), CodigoAcceso.derivar(SECRETO, ID, 0));
    }

    @Test
    @DisplayName("el codigo tiene 10 caracteres del alfabeto Crockford (sin I, L, O ni U)")
    void tieneDiezCaracteresDelAlfabeto() {
        String codigo = CodigoAcceso.derivar(SECRETO, ID, 0);

        assertEquals(10, codigo.length());
        assertTrue(codigo.chars().allMatch(c -> CodigoAcceso.ALFABETO.indexOf(c) >= 0), codigo);
        assertTrue(codigo.chars().noneMatch(c -> "ILOU".indexOf(c) >= 0), codigo);
    }

    @Test
    @DisplayName("ids distintos dan codigos distintos (1000 reservas, ninguna colision)")
    void idsDistintosDanCodigosDistintos() {
        Set<String> codigos = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> codigos.add(CodigoAcceso.derivar(SECRETO, UUID.randomUUID(), 0)));

        assertEquals(1000, codigos.size());
    }

    @Test
    @DisplayName("sin el secreto no se llega al mismo codigo: otro secreto da otro valor")
    void dependeDelSecreto() {
        byte[] otroSecreto = "otro-secreto".getBytes(StandardCharsets.UTF_8);

        assertNotEquals(CodigoAcceso.derivar(SECRETO, ID, 0), CodigoAcceso.derivar(otroSecreto, ID, 0));
    }

    @Test
    @DisplayName("el contador de intento rederiva un codigo distinto para el mismo id")
    void elIntentoCambiaElCodigo() {
        assertNotEquals(CodigoAcceso.derivar(SECRETO, ID, 0), CodigoAcceso.derivar(SECRETO, ID, 1));
    }

    // ---- Asignacion ----

    @Test
    @DisplayName("asignar guarda el codigo derivado y la fecha de emision")
    void asignarGuardaCodigoYEmision() {
        Reserva reserva = new Reserva();
        reserva.setId(ID);
        Instant emitido = Instant.parse("2026-10-10T13:00:00Z");

        codigoAcceso.asignar(reserva, emitido);

        assertEquals(CodigoAcceso.derivar(SECRETO, ID, 0), reserva.getCodigo());
        assertEquals(emitido, reserva.getCodigoEmitidoAt());
    }

    @Test
    @DisplayName("si el codigo ya lo tiene otra reserva, se rederiva con el contador")
    void asignarRederivaAnteUnaColision() {
        Reserva reserva = new Reserva();
        reserva.setId(ID);
        String primero = CodigoAcceso.derivar(SECRETO, ID, 0);
        when(reservaRepository.existsByCodigo(anyString())).thenAnswer(i -> primero.equals(i.getArgument(0)));

        codigoAcceso.asignar(reserva, Instant.now());

        assertEquals(CodigoAcceso.derivar(SECRETO, ID, 1), reserva.getCodigo());
        verify(reservaRepository, times(2)).existsByCodigo(anyString());
    }

    @Test
    @DisplayName("si nunca encuentra un codigo libre, falla en vez de quedar en un bucle")
    void asignarFallaSiTodoColisiona() {
        Reserva reserva = new Reserva();
        reserva.setId(ID);
        when(reservaRepository.existsByCodigo(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> codigoAcceso.asignar(reserva, Instant.now()));
        assertNull(reserva.getCodigo());
    }

    // ---- Normalizacion ----

    @ParameterizedTest(name = "\"{0}\" → {1}")
    @CsvSource(delimiter = '|', value = {
            "ABCDE12345|ABCDE12345",
            "ABCDE-12345|ABCDE12345",
            "abcde-12345|ABCDE12345",
            "  ab cde 123 45  |ABCDE12345",
            "APARCAR:ABCDE12345|ABCDE12345",
            "aparcar:abcde-12345|ABCDE12345",
            // Equivalencias de Crockford: I y L se leen como 1, O como 0.
            "ABCDE-I2345|ABCDE12345",
            "ABCDE-l2345|ABCDE12345",
            "ABCDE-O2345|ABCDE02345",
            "abcde-o2345|ABCDE02345",
    })
    @DisplayName("normaliza prefijo, mayusculas, espacios, guiones y equivalencias")
    void normaliza(String entrada, String esperado) {
        assertEquals(esperado, CodigoAcceso.normalizar(entrada));
    }

    @ParameterizedTest(name = "\"{0}\" es invalido")
    @ValueSource(strings = {
            "ABCDE1234",        // 9 caracteres
            "ABCDE123456",      // 11 caracteres
            "ABCDE-1234U",      // U no existe en el alfabeto
            "ABCDE_12345",      // simbolo
            "APARCAR:",         // solo el prefijo
            "OTRO:ABCDE12345",  // prefijo ajeno
            "https://ejemplo.com",
    })
    @DisplayName("rechaza lo que no es un codigo de 10 caracteres validos")
    void rechazaFormatosInvalidos(String entrada) {
        assertThrows(ValidationException.class, () -> CodigoAcceso.normalizar(entrada));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("rechaza una entrada vacia o nula")
    void rechazaVacio(String entrada) {
        assertThrows(ValidationException.class, () -> CodigoAcceso.normalizar(entrada));
    }

    @Test
    @DisplayName("normalizar un codigo derivado lo deja igual: el QR y el tipeo llegan al mismo valor")
    void unCodigoDerivadoYaEstaNormalizado() {
        String codigo = CodigoAcceso.derivar(SECRETO, ID, 0);

        assertEquals(codigo, CodigoAcceso.normalizar(CodigoAcceso.PREFIJO_QR + codigo));
        assertEquals(codigo, CodigoAcceso.normalizar(CodigoAcceso.formatear(codigo).toLowerCase()));
    }

    @Test
    @DisplayName("formatear lo muestra como XXXXX-XXXXX")
    void formatea() {
        assertEquals("ABCDE-12345", CodigoAcceso.formatear("ABCDE12345"));
        assertNull(CodigoAcceso.formatear(null));
    }
}
