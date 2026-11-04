package com.aparcar.api.service;

import com.aparcar.api.config.PuntosProperties;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.puntos.FranjaPuntos;
import com.aparcar.api.service.impl.ReglasPuntos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@UnitTests
class ReglasPuntosTests {
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 2);

    private final ReglasPuntos reglas = new ReglasPuntos(new PuntosProperties(60, 2, 7, 10, 50));

    @Nested
    @DisplayName("estimacion de una reserva")
    class Estimacion {
        @Test
        @DisplayName("Cuenta la hora de inicio, aunque la reserva siga en otra franja")
        void cuentaElInicio() {
            var estimacion = reglas.estimar(LUNES.atTime(21, 0), LUNES.atTime(23, 0));
            assertEquals(FranjaPuntos.TARDE, estimacion.franja());
            assertEquals(10, estimacion.puntos());
            assertNull(estimacion.motivo());
        }

        @Test
        @DisplayName("Una reserva que cruza la medianoche arrancando a las 23 suma la nocturna")
        void cruzaLaMedianoche() {
            var estimacion = reglas.estimar(LUNES.atTime(23, 0), LUNES.plusDays(1).atTime(1, 0));
            assertEquals(FranjaPuntos.NOCTURNA, estimacion.franja());
            assertEquals(20, estimacion.puntos());
        }

        @Test
        @DisplayName("Justo la duracion minima suma; un bloque menos no")
        void duracionMinima() {
            assertTrue(reglas.estimar(LUNES.atTime(10, 0), LUNES.atTime(11, 0)).suma());
            var corta = reglas.estimar(LUNES.atTime(10, 0), LUNES.atTime(10, 45));
            assertFalse(corta.suma());
            assertEquals(0, corta.puntos());
            assertEquals("La reserva dura menos de 60 minutos: no suma puntos.", corta.motivo());
        }
    }

    @Nested
    @DisplayName("racha")
    class Racha {
        private BilleteraPuntos billetera() {
            return new BilleteraPuntos(UUID.randomUUID());
        }

        @Test
        @DisplayName("El primer ingreso arranca la racha en 1")
        void primerDia() {
            BilleteraPuntos billetera = billetera();
            assertTrue(reglas.contarDia(billetera, LUNES));
            assertEquals(1, billetera.getRachaActual());
            assertEquals(1, billetera.getMejorRacha());
            assertEquals(LUNES, billetera.getUltimoDiaRacha());
        }

        @Test
        @DisplayName("Dos ingresos el mismo dia no la cambian")
        void mismoDia() {
            BilleteraPuntos billetera = billetera();
            reglas.contarDia(billetera, LUNES);
            assertFalse(reglas.contarDia(billetera, LUNES));
            assertEquals(1, billetera.getRachaActual());
        }

        @Test
        @DisplayName("Dias consecutivos la suman")
        void consecutivos() {
            BilleteraPuntos billetera = billetera();
            for (int i = 0; i < 3; i++) reglas.contarDia(billetera, LUNES.plusDays(i));
            assertEquals(3, billetera.getRachaActual());
        }

        @Test
        @DisplayName("Saltear un dia la reinicia en 1 y conserva la mejor")
        void corte() {
            BilleteraPuntos billetera = billetera();
            for (int i = 0; i < 4; i++) reglas.contarDia(billetera, LUNES.plusDays(i));
            assertTrue(reglas.contarDia(billetera, LUNES.plusDays(5)));
            assertEquals(1, billetera.getRachaActual());
            assertEquals(4, billetera.getMejorRacha());
        }

        @Test
        @DisplayName("Un dia anterior al ultimo contado no la cambia")
        void diaAnterior() {
            BilleteraPuntos billetera = billetera();
            reglas.contarDia(billetera, LUNES.plusDays(1));
            assertFalse(reglas.contarDia(billetera, LUNES));
            assertEquals(LUNES.plusDays(1), billetera.getUltimoDiaRacha());
        }

        @Test
        @DisplayName("El cambio de dia es a la medianoche: 23:59 y 00:00 son dias consecutivos")
        void medianoche() {
            BilleteraPuntos billetera = billetera();
            LocalDateTime antes = LUNES.atTime(23, 59);
            reglas.contarDia(billetera, antes.toLocalDate());
            assertTrue(reglas.contarDia(billetera, antes.plusMinutes(1).toLocalDate()));
            assertEquals(2, billetera.getRachaActual());
        }

        @ParameterizedTest(name = "racha {0} → bonus {1}")
        @CsvSource({"1, false", "6, false", "7, true", "8, false", "13, false", "14, true", "21, true", "0, false"})
        @DisplayName("El bonus toca en cada multiplo de 7")
        void bonus(int racha, boolean toca) {
            assertEquals(toca, reglas.tocaBonus(racha));
        }

        @Test
        @DisplayName("21 dias seguidos dan tres bonus, en los dias 7, 14 y 21")
        void tresBonus() {
            BilleteraPuntos billetera = billetera();
            int bonus = 0;
            for (int i = 0; i < 21; i++) {
                if (reglas.contarDia(billetera, LUNES.plusDays(i)) && reglas.tocaBonus(billetera.getRachaActual())) {
                    bonus++;
                }
            }
            assertEquals(3, bonus);
            assertEquals(21, billetera.getMejorRacha());
        }

        @Test
        @DisplayName("La racha se muestra viva si el ultimo dia fue hoy o ayer, y en 0 si fue antes")
        void vigente() {
            BilleteraPuntos billetera = billetera();
            reglas.contarDia(billetera, LUNES);
            reglas.contarDia(billetera, LUNES.plusDays(1));
            assertEquals(2, reglas.rachaVigente(billetera, LUNES.plusDays(1)));
            assertEquals(2, reglas.rachaVigente(billetera, LUNES.plusDays(2)));
            assertEquals(0, reglas.rachaVigente(billetera, LUNES.plusDays(3)));
            assertEquals(0, reglas.rachaVigente(billetera(), LUNES));
        }

        @ParameterizedTest(name = "racha {0} → faltan {1}")
        @CsvSource({"0, 7", "1, 6", "6, 1", "7, 7", "13, 1"})
        @DisplayName("Dias para el proximo bonus")
        void diasParaBonus(int racha, int faltan) {
            assertEquals(faltan, reglas.diasParaBonus(racha));
        }
    }

    @Nested
    @DisplayName("descuento")
    class Descuento {
        @ParameterizedTest(name = "{1} puntos sobre {0} → {2} % = {3}, paga {4}")
        @CsvSource({
                "1000.00, 100, 10.00, 100.00, 900.00",
                "1000.00, 500, 50.00, 500.00, 500.00",
                "1000.00, 10, 1.00, 10.00, 990.00",
                "1000.00, 1, 0.10, 1.00, 999.00",
                // 333,33 * 1,5 % = 4,999995 → 5,00 (HALF_UP)
                "333.33, 15, 1.50, 5.00, 328.33",
                // 100,10 * 0,5 % = 0,5005 → 0,50
                "100.10, 5, 0.50, 0.50, 99.60",
                // 0,25 * 10 % = 0,025 → 0,03 (HALF_UP, no HALF_EVEN)
                "0.25, 100, 10.00, 0.03, 0.22",
        })
        @DisplayName("Lineal, con BigDecimal y HALF_UP a 2 decimales")
        void lineal(BigDecimal subtotal, int puntos, BigDecimal pct, BigDecimal monto, BigDecimal finalPrecio) {
            var descuento = reglas.descuento(subtotal, puntos);
            assertEquals(pct, descuento.porcentaje());
            assertEquals(monto, descuento.monto());
            assertEquals(finalPrecio, descuento.precioFinal());
            assertEquals(subtotal, descuento.monto().add(descuento.precioFinal()));
        }

        @Test
        @DisplayName("El tope de 50 % son 500 puntos")
        void tope() {
            assertEquals(500, reglas.maxPuntosCanjeables());
            assertEquals(50, reglas.descuentoMaxPct());
        }
    }
}
