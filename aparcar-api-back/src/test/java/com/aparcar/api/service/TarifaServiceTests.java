package com.aparcar.api.service;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.reserva.*;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.TarifaRepository;
import com.aparcar.api.service.impl.TarifaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@UnitTests
class TarifaServiceTests {
    @Mock TarifaRepository repository;
    @InjectMocks TarifaService service;
    private final LocalDateTime desde = LocalDateTime.of(2099, 1, 1, 23, 45);

    private Tarifa tarifa(CocheraTipo tipo) {
        Tarifa t = new Tarifa();
        t.setTipo(tipo);
        t.setHora(new BigDecimal("1000.10"));
        t.setFraccion(new BigDecimal("300.05"));
        t.setMediaJornada(new BigDecimal("9000.20"));
        t.setJornadaCompleta(new BigDecimal("16000.30"));
        return t;
    }

    @ParameterizedTest(name = "{0} minutos suman {1}")
    @CsvSource({"15,300.05", "30,600.10", "45,900.15", "60,1000.10", "75,1300.15",
            "705,11901.25", "720,9000.20", "735,9300.25", "780,10000.30",
            "1425,20901.45", "1440,16000.30", "1455,16300.35", "2880,32000.60",
            "3675,42300.95"})
    void sumaBloquesConDecimalesSinRedondeos(long minutos, String total) {
        when(repository.findById(CocheraTipo.AUTO)).thenReturn(Optional.of(tarifa(CocheraTipo.AUTO)));
        var cotizacion = service.cotizar(CocheraTipo.AUTO, desde, desde.plusMinutes(minutos));
        assertEquals(new BigDecimal(total), cotizacion.total());
        assertEquals(minutos, cotizacion.jornadas() * 1440 + cotizacion.mediasJornadas() * 720
                + cotizacion.horas() * 60 + cotizacion.fracciones() * 15);
    }

    @ParameterizedTest
    @EnumSource(CocheraTipo.class)
    void usaLaTarifaDeCadaCategoria(CocheraTipo tipo) {
        Tarifa t = tarifa(tipo);
        t.setHora(BigDecimal.valueOf(tipo.ordinal() + 1));
        when(repository.findById(tipo)).thenReturn(Optional.of(t));
        var cotizacion = service.cotizar(tipo, desde, desde.plusHours(1));
        assertEquals(t.getHora().setScale(2), cotizacion.total());
        assertEquals(tipo, cotizacion.tipo());
        assertEquals("ARS", cotizacion.moneda());
    }

    @Test
    void rechazaTarifasSinConfigurar() {
        when(repository.findById(CocheraTipo.AUTO)).thenReturn(Optional.empty());
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde, desde.plusHours(1)));
    }

    @Test
    void permitePreciosCeroExplicitos() {
        Tarifa t = tarifa(CocheraTipo.ACCESIBLE);
        t.setHora(BigDecimal.ZERO);
        when(repository.findById(t.getTipo())).thenReturn(Optional.of(t));
        assertEquals(new BigDecimal("0.00"), service.cotizar(t.getTipo(), desde, desde.plusHours(1)).total());
    }

    @Test
    void rechazaRangosInvalidosYPasados() {
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde, desde));
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde, desde.minusMinutes(15)));
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde, desde.plusMinutes(16)));
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde.plusSeconds(1), desde.plusHours(1)));
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, null, desde));
        assertThrows(ValidationException.class, () -> service.cotizar(CocheraTipo.AUTO, desde.minusYears(200), desde.minusYears(199)));
    }
}
