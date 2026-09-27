package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
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

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra de las cocheras ACCESIBLE: solo las reserva quien declaro una
 * discapacidad. Cubre como entra la declaracion (alta del admin, registro,
 * editar mi perfil), la regla al reservar y el filtro de /disponibles, todo
 * desde el contrato HTTP y contra una base real.
 */
@IntegrationTests
public class AccesibilidadTests {

    private static final String VISITANTE = "visitante@test.com";

    @Autowired
    private VisitanteRepository visitanteRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private CocheraRepository cocheraRepository;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        reservaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        visitanteRepository.deleteAll();
        cocheraRepository.deleteAll();
    }

    private Visitante crearVisitante(String documento, String email, boolean tieneDiscapacidad) {
        Visitante visitante = new Visitante();
        visitante.setNombre("Juan Perez");
        visitante.setDocumento(documento);
        visitante.setEmail(email);
        visitante.setPassword("hash-irrelevante");
        visitante.setAuthorities(Set.of(AppAuthority.USER));
        visitante.setIsActive(true);
        visitante.setTieneDiscapacidad(tieneDiscapacidad);
        return visitanteRepository.save(visitante);
    }

    private Vehiculo crearVehiculo(String patente, Visitante visitante) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente(patente);
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(visitante);
        return vehiculoRepository.save(vehiculo);
    }

    private Cochera crearCochera(String numero, CocheraTipo tipo) {
        Cochera cochera = new Cochera();
        cochera.setNumero(numero);
        cochera.setSector("Planta Baja");
        cochera.setTipo(tipo);
        cochera.setEstado(CocheraEstado.HABILITADA);
        return cocheraRepository.save(cochera);
    }

    /** Franja futura y alineada al bloque de 15 minutos. */
    private static LocalDateTime enUnaHora() {
        return LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
    }

    private static String reservaJson(UUID visitanteId, UUID vehiculoId, UUID cocheraId) {
        LocalDateTime desde = enUnaHora();
        return "{\"visitanteId\":\"" + visitanteId + "\",\"vehiculoId\":\"" + vehiculoId
                + "\",\"cocheraId\":\"" + cocheraId + "\",\"desde\":\"" + desde
                + "\",\"hasta\":\"" + desde.plusHours(1) + "\"}";
    }

    private static String altaJson(UUID cocheraId, String tieneDiscapacidad) {
        String flag = tieneDiscapacidad == null ? "" : ",\"tieneDiscapacidad\":" + tieneDiscapacidad;
        return """
                {"nombre":"Juan Perez","documento":"30111222","email":"juan@test.com",
                 "patente":"ABC123","tipoVehiculo":"AUTO","cocheraId":"%s"%s}
                """.formatted(cocheraId, flag);
    }

    // ---- Alta del admin ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] el alta con tieneDiscapacidad=true puede reservar una cochera ACCESIBLE")
    void altaConDiscapacidadReservaAccesible() throws Exception {
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(altaJson(cochera.getId(), "true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitante.tieneDiscapacidad").value(true))
                .andExpect(jsonPath("$.reserva.cochera.numero").value("AC-01"));

        assertTrue(visitanteRepository.findByEmail("juan@test.com").orElseThrow().getTieneDiscapacidad());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] el alta sin discapacidad a una ACCESIBLE devuelve 400 y no deja nada creado")
    void altaSinDiscapacidadAAccesibleNoCreaNada() throws Exception {
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(altaJson(cochera.getId(), null)))
                .andExpect(status().isBadRequest());

        // Todo o nada: ni cuenta, ni vehiculo, ni reserva.
        assertEquals(0, visitanteRepository.count());
        assertEquals(0, vehiculoRepository.count());
        assertEquals(0, reservaRepository.count());
    }

    // ---- Registro ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] POST /register guarda la discapacidad declarada al crear la cuenta")
    void registroGuardaLaDiscapacidad() throws Exception {
        // Sin servletPath a proposito: con "/register" entraria al rate limit
        // (5 pedidos por minuto compartidos con los otros tests de registro).
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Ana Perez","documento":"30111222","email":"ana@test.com",
                                 "password":"claveSegura1","tieneDiscapacidad":true}
                                """))
                .andExpect(status().isCreated());

        assertTrue(visitanteRepository.findByEmail("ana@test.com").orElseThrow().getTieneDiscapacidad());
    }

    // ---- Editar mi perfil ----

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me permite declarar la discapacidad y GET /me la devuelve")
    void editarPerfilDeclaraLaDiscapacidad() throws Exception {
        crearVisitante("30111222", VISITANTE, false);
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VISITANTE + "\",\"tieneDiscapacidad\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneDiscapacidad").value(true));

        mockMvc.perform(get("/api/v1/visitantes/me").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneDiscapacidad").value(true));
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me sin el campo no borra la declaracion")
    void editarPerfilSinElCampoNoLaBorra() throws Exception {
        crearVisitante("30111222", VISITANTE, true);
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + VISITANTE + "\",\"telefono\":\"11-4444-5555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tieneDiscapacidad").value(true));
    }

    // ---- Reservar ----

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] un USER sin discapacidad declarada no puede reservar una ACCESIBLE")
    void userSinDiscapacidadNoReservaAccesible() throws Exception {
        Visitante visitante = crearVisitante("30111222", VISITANTE, false);
        Vehiculo vehiculo = crearVehiculo("ABC123", visitante);
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId())))
                .andExpect(status().isBadRequest());

        assertEquals(0, reservaRepository.count());
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] un USER con discapacidad declarada puede reservar una ACCESIBLE")
    void userConDiscapacidadReservaAccesible() throws Exception {
        Visitante visitante = crearVisitante("30111222", VISITANTE, true);
        Vehiculo vehiculo = crearVehiculo("ABC123", visitante);
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] un ADMIN no puede reservar una ACCESIBLE a nombre de alguien sin discapacidad declarada")
    void adminParaOtroSinDiscapacidadSeRechaza() throws Exception {
        Visitante visitante = crearVisitante("30111222", VISITANTE, false);
        Vehiculo vehiculo = crearVehiculo("ABC123", visitante);
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId())))
                .andExpect(status().isBadRequest());

        assertFalse(reservaRepository.findAll().stream().findAny().isPresent());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] un ADMIN puede reservar una ACCESIBLE a nombre de alguien con discapacidad declarada")
    void adminParaOtroConDiscapacidadSePermite() throws Exception {
        Visitante visitante = crearVisitante("30111222", VISITANTE, true);
        Vehiculo vehiculo = crearVehiculo("ABC123", visitante);
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitante.tieneDiscapacidad").value(true));
    }

    // ---- /disponibles ----

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] /disponibles no le ofrece ACCESIBLE a un USER sin discapacidad declarada")
    void disponiblesOcultaAccesiblesAUserSinDiscapacidad() throws Exception {
        crearVisitante("30111222", VISITANTE, false);
        crearCochera("A-01", CocheraTipo.AUTO);
        crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .with(securityContext(context))
                        .param("desde", enUnaHora().toString())
                        .param("hasta", enUnaHora().plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("A-01"));
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] /disponibles si le ofrece ACCESIBLE a un USER con discapacidad declarada")
    void disponiblesMuestraAccesiblesAUserConDiscapacidad() throws Exception {
        crearVisitante("30111222", VISITANTE, true);
        crearCochera("A-01", CocheraTipo.AUTO);
        crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .with(securityContext(context))
                        .param("desde", enUnaHora().toString())
                        .param("hasta", enUnaHora().plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] /disponibles le muestra ACCESIBLE a un ADMIN (reserva para otros)")
    void disponiblesMuestraAccesiblesAAdmin() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO);
        crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .with(securityContext(context))
                        .param("desde", enUnaHora().toString())
                        .param("hasta", enUnaHora().plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] /disponibles sigue siendo publico y le muestra ACCESIBLE a un anonimo")
    void disponiblesMuestraAccesiblesAAnonimo() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO);
        crearCochera("AC-01", CocheraTipo.ACCESIBLE);

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .param("desde", enUnaHora().toString())
                        .param("hasta", enUnaHora().plusHours(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}