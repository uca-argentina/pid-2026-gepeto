package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra para /api/v1/vehiculos.
 */
@IntegrationTests
public class VehiculoControllerTests {

    private static final String DUEÑO = "dueño@test.com";
    private static final String OTRO = "otro@test.com";

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private VisitanteRepository visitanteRepository;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        vehiculoRepository.deleteAll();
        visitanteRepository.deleteAll();
    }

    // Una sola entidad: el visitante ES la cuenta con la que se inicia sesion,
    // asi que crear uno es crear la cuenta.
    private Visitante crearVisitante(String documento, String email) {
        Visitante visitante = new Visitante();
        visitante.setNombre("Juan Perez");
        visitante.setDocumento(documento);
        visitante.setEmail(email);
        visitante.setPassword("hash-irrelevante");
        visitante.setAuthorities(Set.of(AppAuthority.USER));
        visitante.setIsActive(true);
        return visitanteRepository.save(visitante);
    }

    private Visitante crearVisitante(String documento) {
        return crearVisitante(documento, documento + "@test.com");
    }

    private Vehiculo crearVehiculo(String patente, VehiculoTipo tipo, Visitante visitante) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente(patente);
        vehiculo.setTipo(tipo);
        vehiculo.setVisitante(visitante);
        return vehiculoRepository.save(vehiculo);
    }

    // ---- Seguridad ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] endpoints de vehiculos devuelven 401 para anonimos")
    void devuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(get("/api/v1/vehiculos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/vehiculos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Crear ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato invalido")
    void crearDevuelve400SiPatenteTieneFormatoInvalido() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"12345\",\"tipo\":\"AUTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 404 si el visitante no existe")
    void crearDevuelve404SiVisitanteNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"ABC123\",\"tipo\":\"AUTO\",\"visitanteId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 201 y normaliza la patente a mayusculas")
    void crearDevuelve201YNormalizaPatente() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"abc123\",\"tipo\":\"AUTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patente").value("ABC123"))
                .andExpect(jsonPath("$.visitanteId").value(visitante.getId().toString()));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente ya existe")
    void crearDevuelve400SiPatenteYaExiste() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"ABC123\",\"tipo\":\"MOTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // Un visitante solo puede cargar vehiculos a su nombre: mandar el
    // visitanteId de otro no le sirve, el backend usa su cuenta igual.
    @Test
    @WithMockUser(username = DUEÑO, authorities = "USER")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos ignora el visitanteId cuando quien carga es un USER")
    void crearIgnoraElVisitanteIdCuandoEsUser() throws Exception {
        Visitante dueño = crearVisitante("30111222", DUEÑO);
        Visitante otro = crearVisitante("30111333", OTRO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"ABC123\",\"tipo\":\"AUTO\",\"visitanteId\":\"" + otro.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitanteId").value(dueño.getId().toString()));
    }

    // ---- Listar ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/vehiculos sin filtro devuelve todos los vehiculos al ADMIN")
    void listarSinFiltroDevuelveTodosAlAdmin() throws Exception {
        Visitante v1 = crearVisitante("30111222");
        Visitante v2 = crearVisitante("30111333");
        crearVehiculo("AAA111", VehiculoTipo.AUTO, v1);
        crearVehiculo("BBB222", VehiculoTipo.MOTO, v2);
        var context = getContext();

        mockMvc.perform(get("/api/v1/vehiculos").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/vehiculos?visitanteId filtra solo los de ese visitante")
    void listarConFiltroDevuelveSoloLosDeEseVisitante() throws Exception {
        Visitante v1 = crearVisitante("30111222");
        Visitante v2 = crearVisitante("30111333");
        crearVehiculo("AAA111", VehiculoTipo.AUTO, v1);
        crearVehiculo("BBB222", VehiculoTipo.MOTO, v2);
        var context = getContext();

        mockMvc.perform(get("/api/v1/vehiculos").param("visitanteId", v1.getId().toString()).with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patente").value("AAA111"));
    }

    // El catalogo completo es cosa del ADMIN, que lo necesita para reservar en
    // nombre de otro. Un visitante solo ve lo suyo, aunque pida lo de otro.
    @Test
    @WithMockUser(username = DUEÑO, authorities = "USER")
    @DisplayName("[Caja negra] GET /api/v1/vehiculos devuelve solo los propios a un visitante")
    void listarDevuelveSoloLosPropiosAUnVisitante() throws Exception {
        Visitante dueño = crearVisitante("30111222", DUEÑO);
        Visitante otro = crearVisitante("30111333", OTRO);
        crearVehiculo("AAA111", VehiculoTipo.AUTO, dueño);
        crearVehiculo("BBB222", VehiculoTipo.MOTO, otro);
        var context = getContext();

        mockMvc.perform(get("/api/v1/vehiculos")
                        .param("visitanteId", otro.getId().toString())
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].patente").value("AAA111"));
    }

    // ---- Obtener por id ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/vehiculos/{id} devuelve 404 si no existe")
    void obtenerPorIdDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/vehiculos/" + UUID.randomUUID()).with(securityContext(context)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/vehiculos/{id} devuelve 200 con el vehiculo cuando existe")
    void obtenerPorIdDevuelve200CuandoExiste() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        var context = getContext();

        mockMvc.perform(get("/api/v1/vehiculos/" + vehiculo.getId()).with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("ABC123"));
    }

    @Test
    @WithMockUser(username = DUEÑO, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/vehiculos/{id} devuelve 403 si no es el dueño")
    void editarDevuelve403SiNoEsElDueño() throws Exception {
        // El vehiculo pertenece a "otro@test.com"; quien hace el request es
        // "dueño@test.com" -> no coinciden.
        Visitante propietarioReal = crearVisitante("30111222", OTRO);
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, propietarioReal);
        var context = getContext();

        mockMvc.perform(put("/api/v1/vehiculos/" + vehiculo.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"XYZ999\",\"tipo\":\"MOTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DUEÑO, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/vehiculos/{id} permite al dueño editar su vehiculo")
    void editarPermiteAlDueñoEditarSuVehiculo() throws Exception {
        // El email del Visitante tiene que coincidir con el username del @WithMockUser.
        Visitante dueño = crearVisitante("30111222", DUEÑO);
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, dueño);
        var context = getContext();

        // "A123BCD" es un formato Mercosur real de MOTO (antes decia "XYZ999",
        // que es formato de auto y ahora la validacion por tipo lo rechazaria).
        mockMvc.perform(put("/api/v1/vehiculos/" + vehiculo.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"A123BCD\",\"tipo\":\"MOTO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patente").value("A123BCD"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] DELETE /api/v1/vehiculos/{id} devuelve 204 cuando no tiene reservas")
    void eliminarDevuelve204CuandoNoTieneReservas() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        var context = getContext();

        mockMvc.perform(delete("/api/v1/vehiculos/" + vehiculo.getId()).with(securityContext(context)))
                .andExpect(status().isNoContent());
    }

    // ---- Validacion de patente segun tipo ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos acepta formato anterior de MOTO (123ABC)")
    void crearAceptaFormatoAnteriorDeMoto() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"123abc\",\"tipo\":\"MOTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patente").value("123ABC"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos acepta formato Mercosur de MOTO (A123BCD)")
    void crearAceptaFormatoMercosurDeMoto() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"a123bcd\",\"tipo\":\"MOTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patente").value("A123BCD"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato de auto para una MOTO")
    void crearDevuelve400SiPatenteEsDeAutoParaMoto() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"ABC123\",\"tipo\":\"MOTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/vehiculos devuelve 400 si la patente tiene formato de moto para un AUTO")
    void crearDevuelve400SiPatenteEsDeMotoParaAuto() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        var context = getContext();

        mockMvc.perform(post("/api/v1/vehiculos")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patente\":\"123ABC\",\"tipo\":\"AUTO\",\"visitanteId\":\"" + visitante.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
