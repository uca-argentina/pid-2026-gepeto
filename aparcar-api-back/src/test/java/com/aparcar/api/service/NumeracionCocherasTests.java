package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.service.impl.NumeracionCocheras;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caja blanca de la numeracion automatica ({PREFIJO}-{secuencial}). Es puro
 * calculo: no hay mocks ni base.
 */
@UnitTests
public class NumeracionCocherasTests {

    @ParameterizedTest(name = "{0} usa el prefijo {1}")
    @CsvSource({
            "AUTO, A",
            "MOTO, M",
            "CARGA, C",
            "ACCESIBLE, AC"
    })
    @DisplayName("cada tipo tiene su prefijo fijo")
    void cadaTipoTieneSuPrefijo(CocheraTipo tipo, String prefijo) {
        assertEquals(prefijo, NumeracionCocheras.prefijo(tipo));
    }

    @Test
    @DisplayName("sin cocheras existentes, cada tipo arranca en 01")
    void sinExistentesArrancaEnUno() {
        assertEquals(List.of("A-01", "A-02", "A-03"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 3, List.of()));
        assertEquals(List.of("AC-01"),
                NumeracionCocheras.siguientes(CocheraTipo.ACCESIBLE, 1, List.of()));
    }

    @Test
    @DisplayName("continua desde el maximo existente, no desde el ultimo ni desde la cantidad")
    void continuaDesdeElMaximoExistente() {
        // Desordenados y con huecos: lo que manda es el maximo (07), no
        // cuantas hay (3) ni cual es la ultima de la lista (03). Los huecos
        // no se rellenan.
        List<String> existentes = List.of("A-01", "A-07", "A-03");

        assertEquals(List.of("A-08", "A-09"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 2, existentes));
    }

    @Test
    @DisplayName("el maximo de ACCESIBLE (AC-..) no se cuela en AUTO (A-..)")
    void accesibleNoCuentaParaAuto() {
        List<String> existentes = List.of("AC-05", "AC-12");

        assertEquals(0, NumeracionCocheras.maximoExistente(CocheraTipo.AUTO, existentes));
        assertEquals(List.of("A-01"), NumeracionCocheras.siguientes(CocheraTipo.AUTO, 1, existentes));
    }

    @Test
    @DisplayName("el maximo de AUTO (A-..) no se cuela en ACCESIBLE (AC-..)")
    void autoNoCuentaParaAccesible() {
        List<String> existentes = List.of("A-40", "AC-02");

        assertEquals(List.of("AC-03"),
                NumeracionCocheras.siguientes(CocheraTipo.ACCESIBLE, 1, existentes));
    }

    @Test
    @DisplayName("el maximo de ACCESIBLE (AC-..) no se cuela en CARGA (C-..)")
    void accesibleNoCuentaParaCarga() {
        // "AC-09" termina en "C-09": el patron tiene que estar anclado al
        // principio para que no lo tome como una de CARGA.
        List<String> existentes = List.of("AC-09", "C-02");

        assertEquals(List.of("C-03"),
                NumeracionCocheras.siguientes(CocheraTipo.CARGA, 1, existentes));
    }

    @Test
    @DisplayName("con todos los tipos mezclados, cada uno sigue su propia serie")
    void cadaTipoSigueSuSerie() {
        List<String> existentes = List.of("A-10", "M-03", "C-01", "AC-04");

        assertEquals("A-11", NumeracionCocheras.siguientes(CocheraTipo.AUTO, 1, existentes).getFirst());
        assertEquals("M-04", NumeracionCocheras.siguientes(CocheraTipo.MOTO, 1, existentes).getFirst());
        assertEquals("C-02", NumeracionCocheras.siguientes(CocheraTipo.CARGA, 1, existentes).getFirst());
        assertEquals("AC-05", NumeracionCocheras.siguientes(CocheraTipo.ACCESIBLE, 1, existentes).getFirst());
    }

    @ParameterizedTest(name = "\"{0}\" se ignora")
    @ValueSource(strings = {
            "1", "15", "A1", "A-", "A-1B", "A--05", "AUTO-05", "a-05", " A-05", "A-05 ",
            "PB-03", "A-0x1", "A-1.5", "A-+3"
    })
    @DisplayName("ignora numeros con formato viejo o desconocido")
    void ignoraFormatosDesconocidos(String numeroViejo) {
        assertEquals(0, NumeracionCocheras.maximoExistente(CocheraTipo.AUTO, List.of(numeroViejo)));
    }

    @Test
    @DisplayName("los formatos desconocidos conviven con los validos sin romper el calculo")
    void formatosDesconocidosConvivenConValidos() {
        List<String> existentes = List.of("Cochera 99", "A-02", "PB-50", "A-", "A-05");

        assertEquals(List.of("A-06"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 1, existentes));
    }

    @Test
    @DisplayName("un secuencial absurdamente largo se ignora en vez de romper")
    void secuencialDemasiadoLargoSeIgnora() {
        List<String> existentes = List.of("A-99999999999999999999999", "A-04");

        assertEquals(List.of("A-05"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 1, existentes));
    }

    @Test
    @DisplayName("tolera un null en la lista de existentes")
    void toleraNull() {
        List<String> existentes = new java.util.ArrayList<>();
        existentes.add(null);
        existentes.add("M-01");

        assertEquals(List.of("M-02"), NumeracionCocheras.siguientes(CocheraTipo.MOTO, 1, existentes));
    }

    @Test
    @DisplayName("rellena con cero hasta 2 digitos y despues crece sin limite")
    void anchoMinimoDosDigitosSinLimiteSuperior() {
        assertEquals(List.of("A-09", "A-10", "A-11"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 3, List.of("A-08")));
        assertEquals(List.of("A-99", "A-100", "A-101"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 3, List.of("A-98")));
        assertEquals("M-1000", NumeracionCocheras.formatear(CocheraTipo.MOTO, 1000));
    }

    @Test
    @DisplayName("un numero con ceros de mas cuenta por su valor")
    void cerosDeMasCuentanPorSuValor() {
        assertEquals(List.of("A-08"),
                NumeracionCocheras.siguientes(CocheraTipo.AUTO, 1, List.of("A-007")));
    }

    @Test
    @DisplayName("cantidad 0 no genera ningun numero")
    void cantidadCeroNoGeneraNada() {
        assertTrue(NumeracionCocheras.siguientes(CocheraTipo.AUTO, 0, List.of("A-01")).isEmpty());
    }
}