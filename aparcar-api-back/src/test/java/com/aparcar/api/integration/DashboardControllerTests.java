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
import com.aparcar.api.repository.DashboardRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTests
@Transactional
class DashboardControllerTests {
    @Autowired MockMvc mvc;
    @Autowired DashboardRepository dashboard;
    @Autowired ReservaRepository reservas;
    @Autowired CocheraRepository cocheras;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired Clock clock;

    @Test
    @WithAnonymousUser
    void anonimoNoPuedeVerMetricas() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    void visitanteNoPuedeVerMetricas() throws Exception {
        mvc.perform(get("/api/v1/dashboard").with(securityContext(getContext())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminRecibeResumenSinDatosPersonalesNiCache() throws Exception {
        mvc.perform(get("/api/v1/dashboard").with(securityContext(getContext())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.fecha").exists())
                .andExpect(jsonPath("$.actualizadoAt").exists())
                .andExpect(jsonPath("$.zonaHoraria").value(clock.getZone().getId()))
                .andExpect(jsonPath("$.ocupacion.libres").isNumber())
                .andExpect(jsonPath("$.reservasHoy.noPresentados").isNumber())
                .andExpect(jsonPath("$.actividadPorHora", hasSize(24)))
                .andExpect(jsonPath("$.visitantes").doesNotExist());
    }

    @Test
    void consultaAcotadaIncluyeOcupacionAnteriorMovimientosDeHoyYProximosDelDiaSiguiente() {
        LocalDateTime inicio = LocalDateTime.now(clock).toLocalDate().atStartOfDay();
        LocalDateTime ahora = inicio.plusHours(23);
        var visitante = visitantes.save(new Visitante("Dashboard", "dash-query", "dash-query@test.com", "hash", null,
                Set.of(AppAuthority.USER), true));
        var vehiculo = new Vehiculo();
        vehiculo.setVisitante(visitante);
        vehiculo.setPatente("DAS123");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo = vehiculos.save(vehiculo);
        var cochera = new Cochera();
        cochera.setNumero("DASH-01");
        cochera.setSector("Dashboard");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);

        Reserva hoy = reserva(visitante, vehiculo, cochera, ReservaEstado.CANCELADA, inicio, inicio.plusHours(1));
        Reserva adentro = reserva(visitante, vehiculo, cochera, ReservaEstado.EN_CURSO, inicio.minusDays(3), inicio.minusDays(2));
        Reserva egresoHoy = reserva(visitante, vehiculo, cochera, ReservaEstado.FINALIZADA, inicio.minusDays(1), inicio.plusHours(1));
        egresoHoy.setEgresoAt(inicio);
        reservas.save(egresoHoy);
        Reserva ingresoHoy = reserva(visitante, vehiculo, cochera, ReservaEstado.FINALIZADA, inicio.minusDays(1), inicio.plusHours(1));
        ingresoHoy.setIngresoAt(inicio.plusHours(1));
        reservas.save(ingresoHoy);
        Reserva proxima = reserva(visitante, vehiculo, cochera, ReservaEstado.CONFIRMADA, inicio.plusDays(1), inicio.plusDays(1).plusHours(2));
        // Fuera de todos los intervalos: no se carga el historial ni el futuro lejano.
        reserva(visitante, vehiculo, cochera, ReservaEstado.FINALIZADA, inicio.minusDays(1), inicio.minusHours(1));
        reserva(visitante, vehiculo, cochera, ReservaEstado.CANCELADA, inicio.plusDays(1), inicio.plusDays(1).plusHours(1));
        reserva(visitante, vehiculo, cochera, ReservaEstado.CONFIRMADA, ahora.plusHours(2), ahora.plusHours(3));
        reservas.flush();

        Set<UUID> esperadas = Set.of(hoy.getId(), adentro.getId(), egresoHoy.getId(), ingresoHoy.getId(), proxima.getId());
        Set<UUID> obtenidas = dashboard.reservasRelevantes(inicio, inicio.plusDays(1), ahora, ahora.plusHours(2))
                .stream().map(DashboardRepository.ReservaFila::getId).collect(java.util.stream.Collectors.toSet());
        assertEquals(esperadas, obtenidas);
        var filas = dashboard.cocheras();
        assertEquals("Dashboard", filas.stream().filter(fila -> fila.getNumero().equals("DASH-01")).findFirst().orElseThrow().getSector());
    }

    private Reserva reserva(Visitante visitante, Vehiculo vehiculo, Cochera cochera,
                            ReservaEstado estado, LocalDateTime desde, LocalDateTime hasta) {
        var reserva = new Reserva();
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(estado);
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        return reservas.save(reserva);
    }
}
