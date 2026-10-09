package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.*;
import com.aparcar.api.repository.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTests
@Sql("/tarifas-test.sql")
class ReservaTrazabilidadTests {
    @Autowired MockMvc mvc;
    @Autowired ReservaRepository reservas;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;

    private Visitante admin;
    private Visitante otroAdmin;
    private Visitante usuario;
    private Visitante otroUsuario;
    private Vehiculo vehiculo;
    private Cochera cochera;
    private LocalDateTime desde;

    @BeforeEach
    void setUp() {
        admin = cuenta("Ana Admin", "1", AppAuthority.ADMIN);
        otroAdmin = cuenta("Luis Admin", "2", AppAuthority.ADMIN);
        usuario = cuenta("Juan Usuario", "3", AppAuthority.USER);
        otroUsuario = cuenta("Eva Usuario", "4", AppAuthority.USER);
        vehiculo = new Vehiculo();
        vehiculo.setVisitante(usuario);
        vehiculo.setPatente("ABC123");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo = vehiculos.save(vehiculo);
        cochera = new Cochera();
        cochera.setNumero("A-01");
        cochera.setSector("PB");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
        desde = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @AfterEach
    void tearDown() {
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
    }

    @Test
    void altaPropiaNoAceptaAutoresDelClienteYElHistorialSoloLoVeAdmin() throws Exception {
        String body = reservaJson().replace("\"visitanteId\":\"" + usuario.getId(),
                "\"visitanteId\":\"" + otroUsuario.getId());
        body = body.replace("}", ",\"historial\":[{\"accion\":\"ALTA\",\"actorId\":\"" + admin.getId()
                + "\"}],\"motivoCancelacion\":\"ADMINISTRACION\"}");
        mvc.perform(post("/api/v1/reservas").with(como(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitante.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.historial").doesNotExist());

        UUID id = reservas.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/reservas/" + id).with(como(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.length()").value(1))
                .andExpect(jsonPath("$.historial[0].accion").value("ALTA"))
                .andExpect(jsonPath("$.historial[0].actorId").value(usuario.getId().toString()))
                .andExpect(jsonPath("$.historial[0].actorRol").value("USER"))
                .andExpect(jsonPath("$.historial[0].fecha").isNotEmpty());
        mvc.perform(get("/api/v1/reservas").with(como(usuario)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].historial").doesNotExist());
        mvc.perform(get("/api/v1/reservas/" + id).with(como(otroUsuario)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservas").with(como(otroUsuario)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void conservaQuienCreoYQuienCanceloAunqueCambienSusCuentas() throws Exception {
        UUID id = crear(admin);
        // La base redondea la precision del Instant; se compara la ventana en segundos.
        Instant antes = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(otroAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.length()").value(2))
                .andExpect(jsonPath("$.historial[1].id").isNotEmpty())
                .andExpect(jsonPath("$.historial[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.historial[1].actorId").value(otroAdmin.getId().toString()))
                .andExpect(jsonPath("$.historial[1].accion").value("CANCELACION"));
        var movimiento = reservas.findAll().getFirst().getHistorial().getLast();
        assertFalse(movimiento.getFecha().isBefore(antes));
        assertTrue(movimiento.getFecha().isBefore(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1)));

        otroAdmin.setNombre("Nombre nuevo");
        otroAdmin.setEmail("nuevo@test.com");
        otroAdmin.setAuthorities(Set.of(AppAuthority.USER));
        visitantes.save(otroAdmin);
        visitantes.delete(admin);
        mvc.perform(get("/api/v1/reservas/" + id).with(como(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motivoCancelacion").value("ADMINISTRACION"))
                .andExpect(jsonPath("$.historial").doesNotExist());
        // Se consulta con otro principal ADMIN; no se resuelve su cuenta para leer.
        mvc.perform(get("/api/v1/reservas").with(como("consulta@test.com", AppAuthority.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visitante.id").value(usuario.getId().toString()))
                .andExpect(jsonPath("$[0].historial[0].actorNombre").value("Ana Admin"))
                .andExpect(jsonPath("$[0].historial[1].actorNombre").value("Luis Admin"))
                .andExpect(jsonPath("$[0].historial[1].actorEmail").value("2@test.com"))
                .andExpect(jsonPath("$[0].historial[1].actorRol").value("ADMIN"));
    }

    @Test
    void cancelacionPropiaLiberaLaCocheraYNoDuplicaNiPermiteBajasAjenas() throws Exception {
        UUID id = crear(admin);
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(otroUsuario)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(usuario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").value("USUARIO"))
                .andExpect(jsonPath("$.historial").doesNotExist());
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(admin)))
                .andExpect(status().isBadRequest());
        var historial = reservas.findAll().getFirst().getHistorial();
        assertEquals(2, historial.size());
        assertEquals(usuario.getId(), historial.getLast().getActorId());
        assertEquals(ReservaAccion.CANCELACION, historial.getLast().getAccion());
        mvc.perform(get("/api/v1/cocheras/disponibles").with(como(admin))
                        .param("desde", desde.toString()).param("hasta", desde.plusHours(1).toString()))
                .andExpect(jsonPath("$[0].id").value(cochera.getId().toString()));
        assertNotEquals(id, crear(usuario));
        assertEquals(2, reservas.count());
    }

    @Test
    void deshabilitarRegistraAlAdminEnCadaReservaSinReescribirLasCanceladas() throws Exception {
        UUID cancelada = crear(usuario);
        mvc.perform(post("/api/v1/reservas/" + cancelada + "/cancelar").with(como(usuario)))
                .andExpect(status().isOk());
        UUID primera = crear(admin);
        desde = desde.plusHours(2);
        UUID segunda = crear(usuario);

        mvc.perform(put("/api/v1/cocheras/" + cochera.getId()).with(como(usuario))
                        .contentType(MediaType.APPLICATION_JSON).content(cocheraJson("DESHABILITADA")))
                .andExpect(status().isForbidden());
        cambiarEstadoCochera("DESHABILITADA", otroAdmin);
        for (UUID id : List.of(primera, segunda)) {
            mvc.perform(get("/api/v1/reservas/" + id).with(como(usuario)))
                    .andExpect(jsonPath("$.estado").value("CANCELADA"))
                    .andExpect(jsonPath("$.motivoCancelacion").value("DESHABILITACION"))
                    .andExpect(jsonPath("$.historial").doesNotExist());
            mvc.perform(get("/api/v1/reservas/" + id).with(como(admin)))
                    .andExpect(jsonPath("$.historial.length()").value(2))
                    .andExpect(jsonPath("$.historial[1].accion").value("DESHABILITACION"))
                    .andExpect(jsonPath("$.historial[1].actorId").value(otroAdmin.getId().toString()));
        }
        cambiarEstadoCochera("DESHABILITADA", admin);
        cambiarEstadoCochera("HABILITADA", admin);
        mvc.perform(get("/api/v1/reservas/" + cancelada).with(como(admin)))
                .andExpect(jsonPath("$.historial.length()").value(2))
                .andExpect(jsonPath("$.motivoCancelacion").value("USUARIO"));
        mvc.perform(get("/api/v1/reservas/" + primera).with(como(admin)))
                .andExpect(jsonPath("$.historial.length()").value(2))
                .andExpect(jsonPath("$.motivoCancelacion").value("DESHABILITACION"));
        crear(usuario);
    }

    @Test
    void altaOperativaRegistraAlAdminYAlVisitanteDestinatario() throws Exception {
        mvc.perform(post("/api/v1/visitantes/alta").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(altaJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reserva.visitante.email").value("nuevo@test.com"))
                .andExpect(jsonPath("$.reserva.historial[0].accion").value("ALTA"))
                .andExpect(jsonPath("$.reserva.historial[0].actorId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.reserva.historial[0].actorRol").value("ADMIN"));
    }

    @Test
    void unErrorDeAuditoriaRevierteAltaYDeshabilitacionCompletas() throws Exception {
        mvc.perform(post("/api/v1/visitantes/alta").with(como("inexistente@test.com", AppAuthority.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(altaJson()))
                .andExpect(status().isNotFound());
        assertFalse(visitantes.existsByEmail("nuevo@test.com"));
        assertEquals(1, vehiculos.count());
        assertEquals(0, reservas.count());

        UUID id = crear(usuario);
        mvc.perform(put("/api/v1/cocheras/" + cochera.getId()).with(como("inexistente@test.com", AppAuthority.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(cocheraJson("DESHABILITADA")))
                .andExpect(status().isNotFound());
        assertEquals(CocheraEstado.HABILITADA, cocheras.findById(cochera.getId()).orElseThrow().getEstado());
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como("inexistente@test.com", AppAuthority.ADMIN)))
                .andExpect(status().isNotFound());
        var reserva = reservas.findAll().getFirst();
        assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
        assertEquals(1, reserva.getHistorial().size());
    }

    @Test
    void reservasAnterioresNoRecibenAutoresNiMotivosInventados() throws Exception {
        Reserva anterior = new Reserva();
        anterior.setVisitante(usuario);
        anterior.setVehiculo(vehiculo);
        anterior.setCochera(cochera);
        anterior.setDesde(desde);
        anterior.setHasta(desde.plusHours(1));
        anterior.setEstado(ReservaEstado.CANCELADA);
        UUID id = reservas.save(anterior).getId();
        mvc.perform(get("/api/v1/reservas/" + id).with(como(admin)))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCancelacion").isEmpty())
                .andExpect(jsonPath("$.historial.length()").value(0));
    }

    @Test
    void unaBajaConVersionAnteriorNoSobrescribeLaAccionYaRegistrada() throws Exception {
        UUID id = crear(usuario);
        Reserva desactualizada = reservas.findAll().getFirst();
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(usuario)))
                .andExpect(status().isOk());

        desactualizada.setEstado(ReservaEstado.CANCELADA);
        desactualizada.registrarAccion(ReservaAccion.DESHABILITACION, admin, true);
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,
                () -> reservas.saveAndFlush(desactualizada));

        Reserva guardada = reservas.findAll().getFirst();
        assertEquals(ReservaMotivoCancelacion.USUARIO, guardada.getMotivoCancelacion());
        assertEquals(2, guardada.getHistorial().size());
        assertEquals(usuario.getId(), guardada.getHistorial().getLast().getActorId());
    }

    @Test
    void cancelacionRechazadaDeUnaReservaFinalizadaNoAgregaMovimientos() throws Exception {
        UUID id = crear(usuario);
        Reserva finalizada = reservas.findById(id).orElseThrow();
        finalizada.setEstado(ReservaEstado.FINALIZADA);
        reservas.save(finalizada);
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(como(admin)))
                .andExpect(status().isBadRequest());
        Reserva guardada = reservas.findAll().getFirst();
        assertEquals(ReservaEstado.FINALIZADA, guardada.getEstado());
        assertEquals(1, guardada.getHistorial().size());
        assertNull(guardada.getMotivoCancelacion());
    }

    private Visitante cuenta(String nombre, String documento, AppAuthority rol) {
        return visitantes.save(new Visitante(nombre, documento, documento + "@test.com", "hash", null, Set.of(rol), true));
    }

    private RequestPostProcessor como(Visitante actor) {
        return como(actor.getEmail(), actor.getAuthorities().iterator().next());
    }

    private RequestPostProcessor como(String email, AppAuthority rol) {
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority(rol.name())))));
    }

    private UUID crear(Visitante actor) throws Exception {
        String json = mvc.perform(post("/api/v1/reservas").with(como(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(reservaJson()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private String reservaJson() {
        return """
                {"visitanteId":"%s","vehiculoId":"%s","cocheraId":"%s","desde":"%s","hasta":"%s"}
                """.formatted(usuario.getId(), vehiculo.getId(), cochera.getId(), desde, desde.plusHours(1));
    }

    private String altaJson() {
        return """
                {"nombre":"Nuevo Visitante","documento":"12345678","email":"nuevo@test.com",
                 "patente":"XYZ999","tipoVehiculo":"AUTO","cocheraId":"%s","desde":"%s","hasta":"%s"}
                """.formatted(cochera.getId(), desde, desde.plusHours(1));
    }

    private String cocheraJson(String estado) {
        return """
                {"numero":"A-01","sector":"PB","tipo":"AUTO","estado":"%s"}
                """.formatted(estado);
    }

    private void cambiarEstadoCochera(String estado, Visitante actor) throws Exception {
        mvc.perform(put("/api/v1/cocheras/" + cochera.getId()).with(como(actor))
                        .contentType(MediaType.APPLICATION_JSON).content(cocheraJson(estado)))
                .andExpect(status().isOk());
    }
}
