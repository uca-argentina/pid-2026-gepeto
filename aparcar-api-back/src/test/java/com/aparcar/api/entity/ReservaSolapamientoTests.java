package com.aparcar.api.entity;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.Reserva;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caja blanca del predicado de solapamiento.
 *
 * <p>Toda la regla de "dos reservas no se pisan" se apoya en una sola
 * condicion: {@code desde < otroHasta && hasta > otroDesde}. Estos tests la
 * atacan directamente, sin pasar por el servicio ni por la base, para fijar el
 * comportamiento en los bordes — que es justo donde este tipo de comparacion
 * se rompe (un {@code <=} de mas y dos reservas consecutivas dejarian de poder
 * existir; uno de menos y se permitiria pisar un minuto).
 *
 * <p>La franja de referencia es siempre 10:00–12:00 del mismo dia.
 */
@UnitTests
class ReservaSolapamientoTests {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 1, 0, 0);

    private static LocalDateTime hora(double h) {
        return BASE.plusMinutes((long) (h * 60));
    }

    private static Reserva reservaDe(double desde, double hasta) {
        Reserva reserva = new Reserva();
        reserva.setDesde(hora(desde));
        reserva.setHasta(hora(hasta));
        return reserva;
    }

    /** La reserva existente ocupa de 10 a 12. */
    private static Reserva laExistente() {
        return reservaDe(10, 12);
    }

    @ParameterizedTest(name = "de {0}:00 a {1}:00 se pisa con 10:00-12:00 -> {2}")
    @CsvSource({
            // --- Se pisan ---
            "11, 13, true,  arranca adentro y termina despues",
            "9,  11, true,  arranca antes y termina adentro",
            "9,  13, true,  contiene por completo a la existente",
            "10.5, 11.5, true, queda contenida dentro de la existente",
            "10, 12, true,  es exactamente la misma franja",
            "11.9, 12.1, true, se pisa por seis minutos al final",
            "9.9, 10.1, true,  se pisa por seis minutos al principio",

            // --- No se pisan ---
            "12, 14, false, arranca justo cuando la otra termina",
            "8,  10, false, termina justo cuando la otra arranca",
            "13, 14, false, enteramente despues",
            "7,  8,  false, enteramente antes",
    })
    @DisplayName("seSolapaCon distingue superposicion real de franjas apenas contiguas")
    void seSolapaConCubreLosBordes(double desde, double hasta, boolean esperado, String caso) {
        boolean resultado = laExistente().seSolapaCon(hora(desde), hora(hasta));

        if (esperado) {
            assertTrue(resultado, "deberia pisarse: " + caso);
        } else {
            assertFalse(resultado, "no deberia pisarse: " + caso);
        }
    }

    // Este es el caso que justifica el intervalo semiabierto: si al mediodia
    // termina una reserva y arranca otra, el lugar tiene que poder entregarse.
    // Con intervalos cerrados esto fallaria y nadie podria reservar "seguido".
    @Test
    @DisplayName("dos reservas consecutivas que se tocan en un extremo pueden coexistir")
    void reservasConsecutivasNoSePisan() {
        Reserva manana = reservaDe(8, 12);
        Reserva tarde = reservaDe(12, 16);

        assertFalse(manana.seSolapaCon(tarde.getDesde(), tarde.getHasta()));
        assertFalse(tarde.seSolapaCon(manana.getDesde(), manana.getHasta()));
    }

    @Test
    @DisplayName("el solapamiento es simetrico: da igual cual se pregunta primero")
    void elSolapamientoEsSimetrico() {
        Reserva a = reservaDe(10, 12);
        Reserva b = reservaDe(11, 13);

        assertTrue(a.seSolapaCon(b.getDesde(), b.getHasta()));
        assertTrue(b.seSolapaCon(a.getDesde(), a.getHasta()));
    }

    // De aca sale que la cochera se libere sola: una vez pasado el "hasta",
    // ningun rango posterior intersecta, sin que corra ninguna tarea.
    @Test
    @DisplayName("una franja ya terminada no se pisa con nada posterior")
    void unaFranjaTerminadaNoOcupaMas() {
        Reserva terminada = reservaDe(8, 10);

        assertFalse(terminada.seSolapaCon(hora(10), hora(11)));
        assertFalse(terminada.seSolapaCon(hora(10.001), hora(23)));
    }

    @ParameterizedTest(name = "a las {0}:00 la reserva 10:00-12:00 esta vigente -> {1}")
    @CsvSource({
            "9.99, false",
            "10,   true",
            "11,   true",
            "11.99, true",
            "12,   false",
            "12.01, false",
    })
    @DisplayName("estaVigenteEn incluye el inicio y excluye el fin")
    void estaVigenteEnCubreLosBordes(double momento, boolean esperado) {
        assertTrue(laExistente().estaVigenteEn(hora(momento)) == esperado);
    }
}
