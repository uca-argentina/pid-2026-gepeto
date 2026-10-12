package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las consultas de ocupacion de los vehiculos adentro (EN_CURSO), contra la
 * base real: el fin efectivo de una reserva EN_CURSO es el mayor entre su
 * "hasta" y ahora, asi que una que se paso de hora sigue ocupando el presente.
 * Se pasa "ahora" fijo para que no dependa del reloj.
 */
@IntegrationTests
class ReservaEnCursoOcupacionTests {
    private static final LocalDateTime AHORA = LocalDateTime.of(2030, 3, 4, 12, 0);

    @Autowired ReservaRepository reservas;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;

    private Visitante visitante;
    private Vehiculo vehiculo;
    private Cochera cochera;

    @BeforeEach
    void setUp() {
        visitante = visitantes.save(new Visitante("Juan", "ocu-1", "ocu@test.com", "hash", null,
                Set.of(AppAuthority.USER), true));
        vehiculo = new Vehiculo();
        vehiculo.setVisitante(visitante);
        vehiculo.setPatente("AB123CD");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo = vehiculos.save(vehiculo);
        cochera = new Cochera();
        cochera.setNumero("OCU-01");
        cochera.setSector("PB");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
    }

    @AfterEach
    void tearDown() {
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
    }

    private Reserva reserva(ReservaEstado estado, LocalDateTime desde, LocalDateTime hasta) {
        Reserva reserva = new Reserva();
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(estado);
        return reservas.save(reserva);
    }

    @Test
    @DisplayName("un vehiculo adentro dentro de su horario ocupa su franja, como una CONFIRMADA")
    void dentroDeHorarioOcupaSuFranja() {
        reserva(ReservaEstado.EN_CURSO, AHORA.minusHours(1), AHORA.plusHours(1));

        assertTrue(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA, AHORA.plusMinutes(30), AHORA));
        assertFalse(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA.plusHours(1), AHORA.plusHours(2), AHORA));
    }

    @Test
    @DisplayName("un vehiculo que se paso de hora sigue bloqueando la cochera en el presente")
    void excedidoSigueOcupando() {
        reserva(ReservaEstado.EN_CURSO, AHORA.minusHours(3), AHORA.minusHours(1));

        // Una franja que empieza antes de ahora lo pisa: el auto sigue ahi.
        assertTrue(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA.minusMinutes(15), AHORA.plusHours(1), AHORA));
        assertTrue(reservas.existeEnCursoConVehiculo(vehiculo.getId(), AHORA.minusMinutes(15), AHORA.plusHours(1), AHORA));
        assertEquals(1, reservas.findEnCursoSolapadas(AHORA.minusMinutes(15), AHORA.plusHours(1), AHORA).size());
        // Sin el, esa misma franja estaria libre: la reserva termino hace una hora.
        assertFalse(reservas.existeSolapadaEnCochera(cochera.getId(), ReservaEstado.CONFIRMADA,
                AHORA.minusMinutes(15), AHORA.plusHours(1)));
    }

    @Test
    @DisplayName("un ingreso anticipado ocupa desde la entrada real, antes de la franja reservada")
    void ingresoAnticipadoOcupa() {
        Reserva anticipada = reserva(ReservaEstado.EN_CURSO, AHORA.plusMinutes(15), AHORA.plusHours(1));
        anticipada.setIngresoAt(AHORA);
        reservas.save(anticipada);

        assertTrue(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA, AHORA.plusMinutes(15), AHORA));
        assertTrue(reservas.existeEnCursoConVehiculo(vehiculo.getId(), AHORA, AHORA.plusMinutes(15), AHORA));
        assertEquals(1, reservas.findEnCursoSolapadas(AHORA, AHORA.plusMinutes(15), AHORA).size());
        assertFalse(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA.minusMinutes(15), AHORA, AHORA));
    }

    @Test
    @DisplayName("un vehiculo excedido ocupa tambien un rango que empieza exactamente ahora")
    void excedidoOcupaDesdeAhoraInclusive() {
        reserva(ReservaEstado.EN_CURSO, AHORA.minusHours(3), AHORA.minusHours(1));

        assertTrue(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA, AHORA.plusHours(1), AHORA));
        assertTrue(reservas.existeEnCursoConVehiculo(vehiculo.getId(), AHORA, AHORA.plusHours(1), AHORA));
        assertEquals(1, reservas.findEnCursoSolapadas(AHORA, AHORA.plusHours(1), AHORA).size());
    }

    @Test
    @DisplayName("una VENCIDA (no-show) no ocupa: los chequeos de disponibilidad la ignoran")
    void vencidaNoOcupa() {
        reserva(ReservaEstado.VENCIDA, AHORA.minusMinutes(45), AHORA.plusHours(1));

        assertFalse(reservas.existeSolapadaEnCochera(cochera.getId(), ReservaEstado.CONFIRMADA, AHORA, AHORA.plusHours(1)));
        assertFalse(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA, AHORA.plusHours(1), AHORA));
    }

    @Test
    @DisplayName("una FINALIZADA (ya egreso) no ocupa aunque su franja siga corriendo")
    void finalizadaNoOcupa() {
        reserva(ReservaEstado.FINALIZADA, AHORA.minusHours(1), AHORA.plusHours(1));

        assertFalse(reservas.existeSolapadaEnCochera(cochera.getId(), ReservaEstado.CONFIRMADA, AHORA, AHORA.plusHours(1)));
        assertFalse(reservas.existeEnCursoEnCochera(cochera.getId(), AHORA, AHORA.plusHours(1), AHORA));
    }

    @Test
    @DisplayName("el barrido encuentra como candidatas solo las CONFIRMADAS que ya empezaron")
    void candidatasDelBarrido() {
        reserva(ReservaEstado.CONFIRMADA, AHORA.minusHours(1), AHORA.plusHours(1));
        reserva(ReservaEstado.CONFIRMADA, AHORA.plusHours(1), AHORA.plusHours(2));
        reserva(ReservaEstado.EN_CURSO, AHORA.minusHours(1), AHORA.plusHours(1));

        assertEquals(1, reservas.findByEstadoAndDesdeLessThan(ReservaEstado.CONFIRMADA, AHORA).size());
    }
}
