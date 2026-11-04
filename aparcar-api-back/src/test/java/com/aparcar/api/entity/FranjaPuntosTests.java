package com.aparcar.api.entity;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.puntos.FranjaPuntos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

@UnitTests
class FranjaPuntosTests {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "00:00, NOCTURNA, 20",
            "05:59, NOCTURNA, 20",
            "06:00, MANANA, 10",
            "09:59, MANANA, 10",
            "10:00, MEDIODIA, 15",
            "13:59, MEDIODIA, 15",
            "14:00, TARDE, 10",
            "21:59, TARDE, 10",
            "22:00, NOCTURNA, 20",
            "23:59, NOCTURNA, 20",
    })
    @DisplayName("Los bordes: el inicio es inclusive y el fin exclusive")
    void bordes(LocalTime hora, FranjaPuntos esperada, int puntos) {
        assertEquals(esperada, FranjaPuntos.de(hora));
        assertEquals(puntos, FranjaPuntos.de(hora).getPuntos());
    }

    @Test
    @DisplayName("Cada minuto del dia cae en exactamente una franja")
    void cubrenElDiaSinSolaparse() {
        for (int minuto = 0; minuto < 24 * 60; minuto++) {
            LocalTime hora = LocalTime.of(minuto / 60, minuto % 60);
            long contienen = Arrays.stream(FranjaPuntos.values()).filter(f -> f.contiene(hora)).count();
            assertEquals(1, contienen, "El minuto " + hora + " esta en " + contienen + " franjas");
        }
    }

    @Test
    @DisplayName("La noche es la que mas puntos da")
    void maximo() {
        assertEquals(FranjaPuntos.NOCTURNA.getPuntos(), FranjaPuntos.maximo());
    }
}
