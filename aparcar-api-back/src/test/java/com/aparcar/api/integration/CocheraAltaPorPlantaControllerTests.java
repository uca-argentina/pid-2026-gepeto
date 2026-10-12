package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integracion (caja negra: se diseñan desde el contrato HTTP) para
 * {@code POST /api/v1/cocheras/alta-por-planta}.
 */
@IntegrationTests
public class CocheraAltaPorPlantaControllerTests {

    private static final String URL = "/api/v1/cocheras/alta-por-planta";

    @Autowired
    private CocheraRepository cocheraRepository;

    @Autowired
    private VisitanteRepository visitanteRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        // Orden importante por las foreign keys: reservas -> vehiculos/visitantes -> cocheras
        reservaRepository.deleteAll();
        vehiculoRepository.deleteAll();
        visitanteRepository.deleteAll();
        cocheraRepository.deleteAll();
    }

    private void crearCochera(String numero, CocheraTipo tipo) {
        Cochera cochera = new Cochera();
        cochera.setNumero(numero);
        cochera.setSector("Planta Baja");
        cochera.setTipo(tipo);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cocheraRepository.save(cochera);
    }

    // ---- Seguridad ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] POST alta-por-planta devuelve 401 para anonimos")
    void devuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":1}}
                                """))
                .andExpect(status().isUnauthorized());

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("[Caja negra] POST alta-por-planta devuelve 403 para USER sin rol ADMIN")
    void devuelve403ParaUsuarioSinAdmin() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":1}}
                                """))
                .andExpect(status().isForbidden());

        assertEquals(0, cocheraRepository.count());
    }

    // ---- Alta ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] crea las cocheras pedidas por tipo y devuelve los numeros asignados")
    void creaYDevuelveLosNumerosAsignados() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Subsuelo","cantidades":{"AUTO":2,"MOTO":1,"ACCESIBLE":1,"CARGA":1}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].numero").value("A-01"))
                .andExpect(jsonPath("$[1].numero").value("A-02"))
                .andExpect(jsonPath("$[2].numero").value("M-01"))
                .andExpect(jsonPath("$[3].numero").value("AC-01"))
                .andExpect(jsonPath("$[4].numero").value("C-01"))
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].sector").value("Subsuelo"))
                .andExpect(jsonPath("$[3].tipo").value("ACCESIBLE"))
                .andExpect(jsonPath("$[0].estado").value("HABILITADA"));

        assertEquals(5, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] continua la numeracion de cada tipo desde lo que ya hay en la base")
    void continuaDesdeLoExistente() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO);
        crearCochera("A-07", CocheraTipo.AUTO);
        crearCochera("AC-05", CocheraTipo.ACCESIBLE);
        crearCochera("PB-99", CocheraTipo.AUTO); // formato viejo: se ignora
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":2,"ACCESIBLE":1,"CARGA":1}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].numero").value("A-08"))
                .andExpect(jsonPath("$[1].numero").value("A-09"))
                .andExpect(jsonPath("$[2].numero").value("AC-06"))
                .andExpect(jsonPath("$[3].numero").value("C-01"));

        assertEquals(8, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] dos altas seguidas no repiten numeros: la segunda sigue donde termino la primera")
    void dosAltasSeguidasNoRepitenNumeros() throws Exception {
        var context = getContext();
        String body = """
                {"sector":"Planta Baja","cantidades":{"AUTO":2}}
                """;

        mockMvc.perform(post(URL).with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post(URL).with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].numero").value("A-03"))
                .andExpect(jsonPath("$[1].numero").value("A-04"));

        assertEquals(4, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] los tipos en 0 no se crean")
    void tiposEnCeroNoSeCrean() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":0,"MOTO":3,"CARGA":0}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].numero").value("M-01"))
                .andExpect(jsonPath("$[2].numero").value("M-03"));

        assertTrue(cocheraRepository.findAll().stream().allMatch(c -> c.getTipo() == CocheraTipo.MOTO));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] respeta el estado inicial indicado")
    void respetaElEstadoInicial() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":1},"estado":"DESHABILITADA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].estado").value("DESHABILITADA"));
    }

    // ---- Validaciones: ninguna crea nada ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] devuelve 400 si todas las cantidades son 0")
    void devuelve400SiTodasLasCantidadesSonCero() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":0,"MOTO":0}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Indica al menos un tipo de cochera con cantidad mayor a 0."));

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] devuelve 400 si no se manda ninguna cantidad")
    void devuelve400SinCantidades() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja"}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] devuelve 400 y no crea nada si alguna cantidad es negativa")
    void devuelve400SiHayCantidadNegativa() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"AUTO":3,"MOTO":-1}}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] devuelve 400 si el sector viene vacio")
    void devuelve400SiSectorVacio() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"   ","cantidades":{"AUTO":1}}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] devuelve 400 (no 500) si se manda un tipo de cochera que no existe")
    void devuelve400SiElTipoNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(post(URL)
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sector":"Planta Baja","cantidades":{"BICI":2}}
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, cocheraRepository.count());
    }
}