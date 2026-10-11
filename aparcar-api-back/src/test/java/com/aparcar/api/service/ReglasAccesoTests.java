package com.aparcar.api.service;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.service.impl.ReglasAcceso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bordes exactos de las ventanas de acceso, con los valores por defecto
 * (15 min de anticipo, 30 de tolerancia, 15 de gracia, 60 s de permanencia,
 * 120 s de idempotencia). Reserva de referencia: de 15:00 a 17:00, con el
 * codigo emitido a las 14:00.
 */
class ReglasAccesoTests {
    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 10, 10, 15, 0);
    private static final LocalDateTime HASTA = DESDE.plusHours(2);

    private final ReglasAcceso reglas = AccesoDePrueba.reglasConRelojReal();

    private static Reserva reserva(LocalDateTime desde, LocalDateTime hasta, LocalDateTime emitido) {
        Reserva reserva = new Reserva();
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        reserva.setCodigoEmitidoAt(emitido == null ? null : emitido.atZone(AccesoDePrueba.ZONA).toInstant());
        return reserva;
    }

    private static Reserva deReferencia() {
        return reserva(DESDE, HASTA, DESDE.minusHours(1));
    }

    @Nested
    @DisplayName("ingreso anticipado")
    class Anticipado {
        @Test
        @DisplayName("la ventana abre 15 minutos antes de desde")
        void abreQuinceMinutosAntes() {
            assertEquals(DESDE.minusMinutes(15), reglas.inicioIngreso(deReferencia()));
        }

        @Test
        @DisplayName("un segundo antes de la apertura todavia es anticipado")
        void unSegundoAntesEsAnticipado() {
            assertTrue(reglas.ingresoAnticipado(deReferencia(), DESDE.minusMinutes(15).minusSeconds(1)));
        }

        @Test
        @DisplayName("justo en la apertura ya se puede ingresar")
        void justoEnLaAperturaSePuede() {
            assertFalse(reglas.ingresoAnticipado(deReferencia(), DESDE.minusMinutes(15)));
        }
    }

    @Nested
    @DisplayName("tolerancia de ingreso tardio")
    class Tolerancia {
        @Test
        @DisplayName("justo en el limite (desde + 30) todavia se puede ingresar")
        void justoEnElLimiteSePuede() {
            assertFalse(reglas.ingresoVencido(deReferencia(), DESDE.plusMinutes(30)));
        }

        @Test
        @DisplayName("un segundo despues del limite ya vencio")
        void unSegundoDespuesVencio() {
            assertTrue(reglas.ingresoVencido(deReferencia(), DESDE.plusMinutes(30).plusSeconds(1)));
        }

        @Test
        @DisplayName("si el codigo se emitio despues de desde, la tolerancia se cuenta desde la emision")
        void seCuentaDesdeLaEmisionSiEsPosterior() {
            Reserva hechaTarde = reserva(DESDE, HASTA, DESDE.plusMinutes(20));

            assertEquals(DESDE.plusMinutes(50), reglas.limiteTolerancia(hechaTarde));
            assertFalse(reglas.ingresoVencido(hechaTarde, DESDE.plusMinutes(50)));
            assertTrue(reglas.ingresoVencido(hechaTarde, DESDE.plusMinutes(50).plusSeconds(1)));
        }

        @Test
        @DisplayName("sin fecha de emision, la tolerancia se cuenta desde desde")
        void sinEmisionSeCuentaDesdeDesde() {
            assertEquals(DESDE.plusMinutes(30), reglas.limiteTolerancia(reserva(DESDE, HASTA, null)));
        }

        @Test
        @DisplayName("nunca pasa de hasta: en una franja corta la ventana cierra con la reserva")
        void nuncaPasaDeHasta() {
            Reserva corta = reserva(DESDE, DESDE.plusMinutes(15), DESDE.minusHours(1));

            assertEquals(DESDE.plusMinutes(15), reglas.finIngreso(corta));
            assertFalse(reglas.ingresoVencido(corta, DESDE.plusMinutes(15).minusSeconds(1)));
            // hasta es exclusivo, igual que en el solapamiento.
            assertTrue(reglas.ingresoVencido(corta, DESDE.plusMinutes(15)));
        }

        @Test
        @DisplayName("la ventana que se muestra cierra en la tolerancia cuando es anterior a hasta")
        void finIngresoEsLaTolerancia() {
            assertEquals(DESDE.plusMinutes(30), reglas.finIngreso(deReferencia()));
        }
    }

    @Nested
    @DisplayName("marcar VENCIDA")
    class MarcarVencida {
        @Test
        @DisplayName("pasa a VENCIDA una CONFIRMADA que vencio")
        void marcaLaConfirmadaVencida() {
            Reserva reserva = deReferencia();

            assertTrue(reglas.marcarSiVencio(reserva, DESDE.plusMinutes(31)));
            assertEquals(ReservaEstado.VENCIDA, reserva.getEstado());
        }

        @Test
        @DisplayName("no toca una CONFIRMADA dentro de la tolerancia")
        void noTocaLaVigente() {
            Reserva reserva = deReferencia();

            assertFalse(reglas.marcarSiVencio(reserva, DESDE.plusMinutes(30)));
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
        }

        @ParameterizedTest(name = "{0} no cambia")
        @EnumSource(value = ReservaEstado.class, names = {"EN_CURSO", "CANCELADA", "FINALIZADA", "VENCIDA"})
        @DisplayName("solo una CONFIRMADA puede vencer: un vehiculo adentro no vence nunca")
        void soloLaConfirmadaVence(ReservaEstado estado) {
            Reserva reserva = deReferencia();
            reserva.setEstado(estado);

            assertFalse(reglas.marcarSiVencio(reserva, HASTA.plusDays(1)));
            assertEquals(estado, reserva.getEstado());
        }
    }

    @Nested
    @DisplayName("egreso")
    class Egreso {
        @Test
        @DisplayName("un egreso a los 59 segundos del ingreso es prematuro")
        void a59SegundosEsPrematuro() {
            Reserva reserva = deReferencia();
            reserva.setIngresoAt(DESDE);

            assertTrue(reglas.egresoPrematuro(reserva, DESDE.plusSeconds(59)));
            assertEquals(1, reglas.segundosParaEgreso(reserva, DESDE.plusSeconds(59)));
        }

        @Test
        @DisplayName("justo a los 60 segundos ya se puede egresar")
        void a60SegundosSePuede() {
            Reserva reserva = deReferencia();
            reserva.setIngresoAt(DESDE);

            assertFalse(reglas.egresoPrematuro(reserva, DESDE.plusSeconds(60)));
        }

        @Test
        @DisplayName("justo al final de la gracia (hasta + 15) no hay exceso")
        void finDeLaGraciaSinExceso() {
            assertNull(reglas.minutosExceso(deReferencia(), HASTA.plusMinutes(15)));
        }

        @Test
        @DisplayName("un segundo despues de la gracia es excedido, contando desde hasta")
        void despuesDeLaGraciaEsExcedido() {
            assertEquals(15, reglas.minutosExceso(deReferencia(), HASTA.plusMinutes(15).plusSeconds(1)));
            assertEquals(40, reglas.minutosExceso(deReferencia(), HASTA.plusMinutes(40)));
        }

        @Test
        @DisplayName("un egreso antes de hasta no tiene exceso")
        void antesDeHastaSinExceso() {
            assertNull(reglas.minutosExceso(deReferencia(), HASTA.minusMinutes(30)));
        }
    }

    @Nested
    @DisplayName("idempotencia")
    class Idempotencia {
        @Test
        @DisplayName("repetir la accion a los 120 segundos devuelve lo registrado")
        void dentroDeLaVentana() {
            assertTrue(reglas.recienRegistrado(DESDE, DESDE.plusSeconds(120)));
        }

        @Test
        @DisplayName("a los 121 segundos ya no se considera el mismo pedido")
        void fueraDeLaVentana() {
            assertFalse(reglas.recienRegistrado(DESDE, DESDE.plusSeconds(121)));
        }

        @Test
        @DisplayName("sin registro previo no hay nada que devolver")
        void sinRegistro() {
            assertFalse(reglas.recienRegistrado(null, DESDE));
        }
    }

    @Test
    @DisplayName("el reloj inyectado manda: ahora, instante e inicio del dia salen de el")
    void usaElRelojInyectado() {
        LocalDateTime momento = LocalDateTime.of(2026, 10, 10, 1, 30);
        ReglasAcceso fijas = AccesoDePrueba.reglas(AccesoDePrueba.relojEn(momento));

        assertEquals(momento, fijas.ahora());
        assertEquals(momento.atZone(AccesoDePrueba.ZONA).toInstant(), fijas.instante());
        // La medianoche del predio (UTC-3), no la de UTC.
        assertEquals(Instant.parse("2026-10-10T03:00:00Z"), fijas.inicioDelDia());
    }
}
