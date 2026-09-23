package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.time.LocalDate;
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
 * Caja negra para /api/v1/visitantes.
 *
 * <p>Un visitante es ahora la misma entidad que la cuenta de login, asi que el
 * alta crea las dos cosas de una: no existe mas el paso de "cargar mi perfil"
 * por separado, que era de donde salian los visitantes fantasma.
 */
@IntegrationTests
public class VisitanteControllerTests {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        reservaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        visitanteRepository.deleteAll();
        cocheraRepository.deleteAll();
    }

    private Visitante crearVisitante(String nombre, String documento, String email) {
        return crearVisitante(nombre, documento, email, null);
    }

    private Visitante crearVisitante(String nombre, String documento, String email, String passwordEnClaro) {
        Visitante visitante = new Visitante();
        visitante.setNombre(nombre);
        visitante.setDocumento(documento);
        visitante.setEmail(email);
        visitante.setPassword(
                passwordEnClaro == null ? "hash-irrelevante" : passwordEncoder.encode(passwordEnClaro));
        visitante.setAuthorities(Set.of(AppAuthority.USER));
        visitante.setIsActive(true);
        return visitanteRepository.save(visitante);
    }

    private Cochera crearCochera(String numero, CocheraTipo tipo) {
        Cochera cochera = new Cochera();
        cochera.setNumero(numero);
        cochera.setSector("Planta Baja");
        cochera.setTipo(tipo);
        cochera.setEstado(CocheraEstado.HABILITADA);
        return cocheraRepository.save(cochera);
    }

    private String cuerpoAlta(String documento, String email, String patente, UUID cocheraId) {
        return """
                {"nombre":"Juan Perez","documento":"%s","email":"%s",
                 "patente":"%s","tipoVehiculo":"AUTO","cocheraId":"%s"}
                """.formatted(documento, email, patente, cocheraId);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Regresion] el alta reserva para la fecha elegida y rechaza fechas pasadas sin crear datos")
    void altaRespetaYValidaLaFechaElegida() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();
        LocalDate futura = LocalDate.now().plusDays(7);
        String cuerpo = cuerpoAlta("30111222", "juan@test.com", "ABC123", cochera.getId());

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("}", ",\"fecha\":\"" + LocalDate.now().minusDays(1) + "\"}")))
                .andExpect(status().isBadRequest());
        assertEquals(0, visitanteRepository.count());
        assertEquals(0, vehiculoRepository.count());
        assertEquals(0, reservaRepository.count());

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo.replace("}", ",\"fecha\":\"" + futura + "\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reserva.fecha").value(futura.toString()));
        assertEquals(futura, reservaRepository.findAll().getFirst().getFecha());
    }

    // ---- Seguridad ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] endpoints de visitantes devuelven 401 para anonimos")
    void devuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(get("/api/v1/visitantes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/visitantes/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // El alta y el catalogo son cosa del admin: un visitante no tiene por que
    // ver quien mas esta cargado, ni dar de alta a nadie.
    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] un USER no puede listar visitantes ni dar de alta")
    void unUserNoPuedeListarNiDarDeAlta() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/visitantes").with(securityContext(context)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ---- Alta (flujo admin) ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si falta el nombre")
    void altaDevuelve400SiFaltaNombre() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documento":"30111222","email":"juan@test.com",
                                 "patente":"ABC123","tipoVehiculo":"AUTO","cocheraId":"%s"}
                                """.formatted(cochera.getId())))
                .andExpect(status().isBadRequest());
    }

    // El email dejo de ser opcional: es el identificador de login.
    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si falta el email")
    void altaDevuelve400SiFaltaEmail() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Juan Perez","documento":"30111222",
                                 "patente":"ABC123","tipoVehiculo":"AUTO","cocheraId":"%s"}
                                """.formatted(cochera.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/visitantes/alta crea cuenta, vehiculo y reserva de hoy")
    void altaCreaCuentaVehiculoYReserva() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("30111222", "juan@test.com", "ABC123", cochera.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitante.nombre").value("Juan Perez"))
                .andExpect(jsonPath("$.vehiculo.patente").value("ABC123"))
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reserva.cochera.numero").value("A-01"));

        assertEquals(1, visitanteRepository.count());
        assertEquals(1, vehiculoRepository.count());
        assertEquals(1, reservaRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] el alta deja la cuenta activa, con rol USER y el documento como contraseña")
    void altaDejaLaCuentaListaParaIniciarSesion() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("30111222", "juan@test.com", "ABC123", cochera.getId())))
                .andExpect(status().isCreated());

        Visitante creado = visitanteRepository.findByEmail("juan@test.com").orElseThrow();
        assertTrue(creado.getIsActive());
        assertEquals(Set.of(AppAuthority.USER), creado.getAuthorities());
        // Guardada hasheada, nunca en claro.
        assertTrue(creado.getPassword() != null && !creado.getPassword().equals("30111222"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si el documento ya existe")
    void altaDevuelve400SiDocumentoYaExiste() throws Exception {
        crearVisitante("Juan Perez", "30111222", "ocupado@test.com");
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("30111222", "otro@test.com", "ABC123", cochera.getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/visitantes/alta devuelve 400 si el email ya esta registrado")
    void altaDevuelve400SiEmailYaExiste() throws Exception {
        crearVisitante("Juan Perez", "30111222", "ocupado@test.com");
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("40222333", "ocupado@test.com", "ABC123", cochera.getId())))
                .andExpect(status().isBadRequest());
    }

    // Esta es la regresion que justifica todo el cambio: el alta es una sola
    // transaccion, asi que si la reserva falla no puede quedar la cuenta
    // creada dando vueltas sin vehiculo ni reserva.
    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] si la cochera ya esta ocupada, el alta no deja ninguna cuenta a medio crear")
    void altaNoDejaCuentaHuerfanaSiFallaLaReserva() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        // Primera alta: toma la unica cochera compatible de hoy.
        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("30111222", "juan@test.com", "ABC123", cochera.getId())))
                .andExpect(status().isCreated());

        // Segunda alta sobre la misma cochera: tiene que fallar entera.
        mockMvc.perform(post("/api/v1/visitantes/alta")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta("40222333", "ana@test.com", "XYZ789", cochera.getId())))
                .andExpect(status().isBadRequest());

        assertEquals(1, visitanteRepository.count());
        assertEquals(1, vehiculoRepository.count());
        assertEquals(1, reservaRepository.count());
    }

    // ---- Obtener por id ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/visitantes/{id} devuelve 404 si no existe")
    void obtenerPorIdDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/visitantes/" + UUID.randomUUID()).with(securityContext(context)))
                .andExpect(status().isNotFound());
    }

    // ---- /me: los datos propios ----

    // Ya no existe el 404 de "todavia no cargaste tus datos": si hay cuenta,
    // hay nombre y documento.
    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] GET /api/v1/visitantes/me devuelve los datos de la cuenta autenticada")
    void obtenerPropioDevuelveLosDatosDeLaCuenta() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE);
        var context = getContext();

        mockMvc.perform(get("/api/v1/visitantes/me").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Visitante Propio"))
                .andExpect(jsonPath("$.documento").value("40222333"));
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me actualiza telefono y email")
    void actualizarPropioActualizaTelefonoYEmail() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE);
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"11-2222-3333\",\"email\":\"nuevo@mail.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("11-2222-3333"))
                .andExpect(jsonPath("$.email").value("nuevo@mail.com"))
                .andExpect(jsonPath("$.nombre").value("Visitante Propio"));
    }

    // Cambiar el email cambia el login, asi que no puede pisar el de otra cuenta.
    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me devuelve 400 si el email ya lo usa otra cuenta")
    void actualizarPropioDevuelve400SiElEmailYaEstaEnUso() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE);
        crearVisitante("Otra Persona", "50333444", "ocupado@test.com");
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ocupado@test.com\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- /me/password: cambiar la contraseña propia ----

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me/password cambia la contraseña y deja entrar con la nueva")
    void cambiarPasswordPropiaFuncionaDePuntaAPunta() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE, "claveVieja1");
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me/password")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"claveVieja1\",\"passwordNueva\":\"claveNueva1\"}"))
                .andExpect(status().isNoContent());

        Visitante actualizado = visitanteRepository.findByEmail(VISITANTE).orElseThrow();
        assertTrue(passwordEncoder.matches("claveNueva1", actualizado.getPassword()));
        assertFalse(passwordEncoder.matches("claveVieja1", actualizado.getPassword()));
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me/password devuelve 400 si la contraseña actual no coincide")
    void cambiarPasswordDevuelve400SiLaActualNoCoincide() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE, "claveVieja1");
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me/password")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"equivocada\",\"passwordNueva\":\"claveNueva1\"}"))
                .andExpect(status().isBadRequest());

        Visitante sinCambios = visitanteRepository.findByEmail(VISITANTE).orElseThrow();
        assertTrue(passwordEncoder.matches("claveVieja1", sinCambios.getPassword()));
    }

    @Test
    @WithMockUser(username = VISITANTE, authorities = "USER")
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me/password devuelve 400 si la contraseña nueva es muy corta")
    void cambiarPasswordDevuelve400SiLaNuevaEsMuyCorta() throws Exception {
        crearVisitante("Visitante Propio", "40222333", VISITANTE, "claveVieja1");
        var context = getContext();

        mockMvc.perform(put("/api/v1/visitantes/me/password")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"claveVieja1\",\"passwordNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me/password devuelve 401 para anonimos")
    void cambiarPasswordDevuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(put("/api/v1/visitantes/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] PUT /api/v1/visitantes/me devuelve 401 para anonimos")
    void actualizarPropioDevuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(put("/api/v1/visitantes/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
