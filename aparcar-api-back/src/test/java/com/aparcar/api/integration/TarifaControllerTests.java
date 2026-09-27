package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.*;
import com.aparcar.api.entity.reserva.*;
import com.aparcar.api.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTests
@Transactional
@Sql("/tarifas-test.sql")
class TarifaControllerTests {
    private org.springframework.security.core.context.SecurityContext auth;
    @org.junit.jupiter.api.BeforeEach
    void captureAuth() { auth = getContext(); }

    @Autowired MockMvc mvc;
    @Autowired TarifaRepository tarifas;
    @Autowired ReservaRepository reservas;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired VisitanteRepository visitantes;

    private String matriz(String hora, String tipos) {
        return "{\"tarifas\":[" + java.util.Arrays.stream(tipos.split(","))
                .map(tipo -> "{\"tipo\":\"" + tipo + "\",\"hora\":" + hora
                        + ",\"fraccion\":300,\"mediaJornada\":9000,\"jornadaCompleta\":16000,\"version\":0}")
                .collect(java.util.stream.Collectors.joining(",")) + "]}";
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALIDO|2099-01-01T09:00", "AUTO|no-es-fecha", "AUTO|2099-01-01T08:00", "AUTO|2099-01-01T09:01"})
    @WithMockUser(authorities = "USER")
    void cotizacionRechazaParametrosInvalidosCon400(String caso) throws Exception {
        String[] valores = caso.split("\\|");
        mvc.perform(get("/api/v1/tarifas/cotizacion").with(securityContext(auth))
                        .param("tipo", valores[0]).param("desde", "2099-01-01T08:00").param("hasta", valores[1]))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazaAnonimos() throws Exception {
        mvc.perform(get("/api/v1/tarifas")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/tarifas/cotizacion")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/tarifas")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    void userConsultaPeroNoModifica() throws Exception {
        mvc.perform(get("/api/v1/tarifas").with(securityContext(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/v1/tarifas/cotizacion").with(securityContext(auth))
                        .param("tipo", "MOTO").param("desde", "2099-01-01T08:00").param("hasta", "2099-01-01T09:15"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(650));
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth))
                        .contentType(MediaType.APPLICATION_JSON).content(matriz("2000", "AUTO,MOTO,ACCESIBLE,CARGA")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void guardaLosDieciseisPreciosYRechazaEdicionDesactualizada() throws Exception {
        String body = matriz("1234.56", "AUTO,MOTO,ACCESIBLE,CARGA");
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].hora").value(1234.56));
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(new BigDecimal("1234.56"), tarifas.findById(CocheraTipo.CARGA).orElseThrow().getHora());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "100000000", "null", "\"NaN\""})
    @WithMockUser(authorities = "ADMIN")
    void rechazaImportesInvalidosSinCambiosParciales(String hora) throws Exception {
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content(matriz(hora, "AUTO,MOTO,ACCESIBLE,CARGA")))
                .andExpect(status().isBadRequest());
        assertEquals(new BigDecimal("1000.00"), tarifas.findById(CocheraTipo.AUTO).orElseThrow().getHora());
    }

    @ParameterizedTest
    @ValueSource(strings = {"AUTO,MOTO,CARGA", "AUTO,AUTO,MOTO,CARGA", "AUTO,MOTO,CARGA,INVALIDO"})
    @WithMockUser(authorities = "ADMIN")
    void exigeLasCuatroCategoriasSinDuplicados(String tipos) throws Exception {
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content(matriz("1000", tipos))).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void configuraDesdeCeroYNoInventaPrecios() throws Exception {
        tarifas.deleteAll();
        mvc.perform(get("/api/v1/tarifas").with(securityContext(auth)))
                .andExpect(jsonPath("$[0].hora").isEmpty());
        mvc.perform(get("/api/v1/tarifas/cotizacion").with(securityContext(auth))
                        .param("tipo", "AUTO").param("desde", "2099-01-01T08:00").param("hasta", "2099-01-01T09:00"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/tarifas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                        .content(matriz("0", "AUTO,MOTO,ACCESIBLE,CARGA").replace("\"version\":0", "\"version\":null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].hora").value(0));
    }

    private UUID[] preparar(CocheraTipo tipo) {
        Visitante visitante = new Visitante("Cliente", UUID.randomUUID().toString(), "cliente@test.com", "hash", null, Set.of(AppAuthority.USER), true);
        visitante.setTieneDiscapacidad(true);
        visitantes.save(visitante);
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setVisitante(visitante); vehiculo.setPatente("ABC123"); vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculos.save(vehiculo);
        Cochera cochera = new Cochera();
        cochera.setNumero("T-01"); cochera.setSector("Test tarifas"); cochera.setTipo(tipo); cochera.setEstado(CocheraEstado.HABILITADA);
        cocheras.save(cochera);
        return new UUID[]{visitante.getId(), vehiculo.getId(), cochera.getId()};
    }

    private String reserva(UUID[] ids, int hora, int precio) {
        return """
                {"visitanteId":"%s","vehiculoId":"%s","cocheraId":"%s",
                 "desde":"2099-01-01T%02d:00","hasta":"2099-01-01T%02d:00","precioEsperado":%d}
                """.formatted(ids[0], ids[1], ids[2], hora, hora + 1, precio);
    }

    @Test
    @WithMockUser(username = "cliente@test.com", authorities = "USER")
    void conservaImporteHistoricoYAfectaSoloNuevasReservas() throws Exception {
        UUID[] ids = preparar(CocheraTipo.AUTO);
        mvc.perform(post("/api/v1/reservas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(reserva(ids, 8, 1000))).andExpect(status().isCreated()).andExpect(jsonPath("$.precioTotal").value(1000));
        Tarifa tarifa = tarifas.findById(CocheraTipo.AUTO).orElseThrow();
        tarifa.setHora(new BigDecimal("2000.00")); tarifas.saveAndFlush(tarifa);
        mvc.perform(post("/api/v1/reservas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(reserva(ids, 10, 1000))).andExpect(status().isBadRequest());
        assertEquals(1, reservas.count());
        mvc.perform(post("/api/v1/reservas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(reserva(ids, 10, 2000))).andExpect(status().isCreated()).andExpect(jsonPath("$.precioTotal").value(2000));
        var anterior = reservas.findAll().stream().filter(r -> r.getDesde().getHour() == 8).findFirst().orElseThrow();
        mvc.perform(get("/api/v1/reservas/" + anterior.getId()).with(securityContext(auth)))
                .andExpect(jsonPath("$.precioTotal").value(1000));
        mvc.perform(post("/api/v1/reservas/" + anterior.getId() + "/cancelar").with(securityContext(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.precioTotal").value(1000));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminAplicaAccesibleSegunCocheraYNuncaPrecioDelCliente() throws Exception {
        UUID[] ids = preparar(CocheraTipo.ACCESIBLE);
        mvc.perform(post("/api/v1/reservas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(reserva(ids, 8, 1))).andExpect(status().isBadRequest());
        assertEquals(0, reservas.count());
        mvc.perform(post("/api/v1/reservas").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                .content(reserva(ids, 8, 800))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioTotal").value(800)).andExpect(jsonPath("$.tipoTarifa").value("ACCESIBLE"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void altaOperativaCotizaYRevierteCuentaSiCambioPrecio() throws Exception {
        UUID[] ids = preparar(CocheraTipo.AUTO);
        String alta = """
                {"nombre":"Nuevo","documento":"12345678","email":"nuevo@test.com","patente":"DEF456",
                 "tipoVehiculo":"AUTO","cocheraId":"%s","desde":"2099-01-02T08:00","hasta":"2099-01-02T09:00","precioEsperado":%d}
                """;
        try {
            mvc.perform(post("/api/v1/visitantes/alta").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                    .content(alta.formatted(ids[2], 500))).andExpect(status().isBadRequest());
            assertFalse(visitantes.existsByEmail("nuevo@test.com"));
            assertEquals(0, reservas.count());
            mvc.perform(post("/api/v1/visitantes/alta").with(securityContext(auth)).contentType(MediaType.APPLICATION_JSON)
                    .content(alta.formatted(ids[2], 1000))).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reserva.precioTotal").value(1000));
        } finally {
            reservas.deleteAll(); vehiculos.deleteAll(); visitantes.deleteAll(); cocheras.deleteAll();
        }
    }
}
