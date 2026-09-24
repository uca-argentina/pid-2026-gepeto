package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración (y caja negra: los casos se diseñan desde el
 * contrato HTTP, sin conocimiento de la implementación interna) para
 * {@code /api/v1/cocheras}.
 */
@IntegrationTests
public class CocheraControllerTests {

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

    private Cochera crearCochera(String numero, CocheraTipo tipo, CocheraEstado estado) {
        Cochera cochera = new Cochera();
        cochera.setNumero(numero);
        cochera.setSector("Planta Baja");
        cochera.setTipo(tipo);
        cochera.setEstado(estado);
        return cocheraRepository.save(cochera);
    }

    private Cochera crearCocheraConSector(String numero, String sector) {
        Cochera cochera = new Cochera();
        cochera.setNumero(numero);
        cochera.setSector(sector);
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        return cocheraRepository.save(cochera);
    }

    // ---- Seguridad: anónimo ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] endpoints de gestión devuelven 401 para usuarios anónimos")
    void gestionDevuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(post("/api/v1/cocheras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cocheras"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cocheras/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/v1/cocheras/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/v1/cocheras/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] /disponibles es publico incluso para anonimos")
    void disponiblesEsPublicoParaAnonimos() throws Exception {
        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .param("fecha", LocalDate.now().toString()))
                .andExpect(status().isOk());
    }

    // ---- Seguridad: USER sin ADMIN ----

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("[Caja negra] endpoints de gestión devuelven 403 para USER sin rol ADMIN")
    void gestionDevuelve403ParaUsuarioSinAdmin() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/cocheras")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    // ---- Crear ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras devuelve 400 si falta el numero")
    void crearDevuelve400SiFaltaNumero() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sector\":\"Planta Baja\",\"tipo\":\"AUTO\",\"estado\":\"HABILITADA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras devuelve 201 y crea la cochera con datos validos")
    void crearDevuelve201ConDatosValidos() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"A-01\",\"sector\":\"Planta Baja\",\"tipo\":\"AUTO\",\"estado\":\"HABILITADA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("A-01"))
                .andExpect(jsonPath("$.tipo").value("AUTO"))
                .andExpect(jsonPath("$.estado").value("HABILITADA"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras devuelve 400 si el numero ya existe")
    void crearDevuelve400SiNumeroYaExiste() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"A-01\",\"sector\":\"Subsuelo\",\"tipo\":\"MOTO\",\"estado\":\"HABILITADA\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- Listar / obtener por id ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras devuelve todas las cocheras")
    void listarDevuelveTodasLasCocheras() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        crearCochera("M-01", CocheraTipo.MOTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras/{id} devuelve 404 si no existe")
    void obtenerPorIdDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/" + UUID.randomUUID()).with(securityContext(context)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras/{id} devuelve 200 con la cochera cuando existe")
    void obtenerPorIdDevuelve200CuandoExiste() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/" + cochera.getId()).with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value("A-01"));
    }

    // ---- Editar ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] PUT /api/v1/cocheras/{id} devuelve 404 si no existe")
    void editarDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(put("/api/v1/cocheras/" + UUID.randomUUID())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"A-01\",\"sector\":\"Planta Baja\",\"tipo\":\"AUTO\",\"estado\":\"HABILITADA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] PUT /api/v1/cocheras/{id} devuelve 200 y actualiza los datos")
    void editarDevuelve200YActualiza() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(put("/api/v1/cocheras/" + cochera.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"A-01\",\"sector\":\"Subsuelo\",\"tipo\":\"AUTO\",\"estado\":\"DESHABILITADA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sector").value("Subsuelo"))
                .andExpect(jsonPath("$.estado").value("DESHABILITADA"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] PUT /api/v1/cocheras/{id} devuelve 400 si el nuevo numero ya esta en uso")
    void editarDevuelve400SiNumeroYaEstaEnUso() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        Cochera aEditar = crearCochera("A-02", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(put("/api/v1/cocheras/" + aEditar.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"numero\":\"A-01\",\"sector\":\"Planta Baja\",\"tipo\":\"AUTO\",\"estado\":\"HABILITADA\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- Eliminar ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 404 si no existe")
    void eliminarDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(delete("/api/v1/cocheras/" + UUID.randomUUID()).with(securityContext(context)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 204 cuando no tiene reservas")
    void eliminarDevuelve204CuandoNoTieneReservas() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(delete("/api/v1/cocheras/" + cochera.getId()).with(securityContext(context)))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] DELETE /api/v1/cocheras/{id} devuelve 400 si tiene reservas asociadas")
    void eliminarDevuelve400SiTieneReservasAsociadas() throws Exception {
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);

        Visitante visitante = new Visitante();
        visitante.setNombre("Juan Perez");
        visitante.setDocumento("30111222");
        visitante.setEmail("juan@test.com");
        visitante.setPassword("hash-irrelevante");
        visitante.setAuthorities(Set.of(AppAuthority.USER));
        visitante.setIsActive(true);
        visitanteRepository.save(visitante);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("AB123CD");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(visitante);
        vehiculoRepository.save(vehiculo);

        Reserva reserva = new Reserva();
        reserva.setFecha(LocalDate.now());
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        reservaRepository.save(reserva);

        var context = getContext();

        mockMvc.perform(delete("/api/v1/cocheras/" + cochera.getId()).with(securityContext(context)))
                .andExpect(status().isBadRequest());
    }

    // ---- Disponibles (extremo a extremo, sin mocks) ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] /disponibles excluye cocheras deshabilitadas")
    void disponiblesExcluyeCocherasDeshabilitadas() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.DESHABILITADA);

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .param("fecha", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] /disponibles filtra por tipoVehiculo compatible")
    void disponiblesFiltraPorTipoVehiculoCompatible() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        crearCochera("M-01", CocheraTipo.MOTO, CocheraEstado.HABILITADA);

        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .param("fecha", LocalDate.now().toString())
                        .param("tipoVehiculo", "MOTO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("M-01"));
    }

    // ---- Filtros del listado ----

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", textBlock = """
            NULL, NULL, NULL, A-01;A-02;M-01
            planta, NULL, NULL, A-01;A-02
            NULL, AUTO, NULL, A-01;A-02
            NULL, NULL, DESHABILITADA, A-02
            planta, AUTO, NULL, A-01;A-02
            planta, NULL, HABILITADA, A-01
            NULL, AUTO, HABILITADA, A-01
            planta, AUTO, HABILITADA, A-01
            '', NULL, NULL, A-01;A-02;M-01
            '   ', MOTO, HABILITADA, M-01
            """)
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Regresion] lista todas las cocheras y combina filtros sin exigir sector")
    void listarConFiltrosIndependientes(String sector, String tipo, String estado, String numeros) throws Exception {
        crearCochera("A-02", CocheraTipo.AUTO, CocheraEstado.DESHABILITADA);
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        Cochera moto = crearCochera("M-01", CocheraTipo.MOTO, CocheraEstado.HABILITADA);
        moto.setSector("Subsuelo");
        cocheraRepository.save(moto);

        var request = get("/api/v1/cocheras").with(securityContext(getContext()));
        if (sector != null) request.param("sector", sector);
        if (tipo != null) request.param("tipo", tipo);
        if (estado != null) request.param("estado", estado);

        String[] esperados = numeros.split(";");
        var resultado = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(esperados.length));
        for (int i = 0; i < esperados.length; i++) {
            resultado.andExpect(jsonPath("$[" + i + "].numero").value(esperados[i]));
        }
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras?tipo filtra por tipo exacto")
    void listarFiltraPorTipo() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        crearCochera("M-01", CocheraTipo.MOTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras").param("tipo", "MOTO").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("M-01"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras?sector filtra de forma parcial e insensible a mayusculas")
    void listarFiltraPorSectorParcial() throws Exception {
        crearCocheraConSector("A-01", "Planta Baja");
        crearCocheraConSector("A-02", "Subsuelo");
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras").param("sector", "planta").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value("A-01"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras sin fecha devuelve disponibleEnFecha en null")
    void listarSinFechaDevuelveDisponibleEnFechaNull() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].disponibleEnFecha").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras?fecha marca disponibleEnFecha segun reservas confirmadas")
    void listarConFechaMarcaDisponibilidad() throws Exception {
        Cochera libre = crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        Cochera ocupada = crearCochera("A-02", CocheraTipo.AUTO, CocheraEstado.HABILITADA);

        Visitante visitante = new Visitante();
        visitante.setNombre("Juan Perez");
        visitante.setDocumento("30111222");
        visitante.setEmail("juan@test.com");
        visitante.setPassword("hash-irrelevante");
        visitante.setAuthorities(Set.of(AppAuthority.USER));
        visitante.setIsActive(true);
        visitanteRepository.save(visitante);

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("ABC123");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(visitante);
        vehiculoRepository.save(vehiculo);

        Reserva reserva = new Reserva();
        reserva.setFecha(LocalDate.now());
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(ocupada);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        reservaRepository.save(reserva);

        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras")
                        .param("desde", DESDE)
                        .param("hasta", HASTA)
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 'A-01')].disponibleEnFecha").value(true))
                .andExpect(jsonPath("$[?(@.numero == 'A-02')].disponibleEnFecha").value(false));
    }

    // ---- POST /bulk ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] POST /api/v1/cocheras/bulk devuelve 401 para anonimos")
    void crearEnLoteDevuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(post("/api/v1/cocheras/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("[Caja negra] POST /api/v1/cocheras/bulk devuelve 403 para USER sin rol ADMIN")
    void crearEnLoteDevuelve403ParaUsuarioSinAdmin() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras/bulk")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras/bulk crea todas las cocheras del lote")
    void crearEnLoteCreaTodasLasCocherasDelLote() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras/bulk")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                    {"numero":"A-01","sector":"Planta Baja","tipo":"AUTO","estado":"HABILITADA"},
                                    {"numero":"A-02","sector":"Subsuelo","tipo":"MOTO","estado":"HABILITADA"}
                                ]
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numero").value("A-01"))
                .andExpect(jsonPath("$[1].numero").value("A-02"));

        assertEquals(2, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras/bulk no crea ninguna si una cochera del lote es invalida (todo-o-nada)")
    void crearEnLoteNoCreaNingunaSiUnaEsInvalida() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras/bulk")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                    {"numero":"A-01","sector":"Planta Baja","tipo":"AUTO","estado":"HABILITADA"},
                                    {"numero":"","sector":"Subsuelo","tipo":"MOTO","estado":"HABILITADA"}
                                ]
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(0, cocheraRepository.count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/cocheras/bulk devuelve 400 y no crea nada si un numero ya existe en la base")
    void crearEnLoteDevuelve400SiNumeroYaExisteEnLaBase() throws Exception {
        crearCochera("A-01", CocheraTipo.AUTO, CocheraEstado.HABILITADA);
        var context = getContext();

        mockMvc.perform(post("/api/v1/cocheras/bulk")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                    {"numero":"A-02","sector":"Subsuelo","tipo":"MOTO","estado":"HABILITADA"},
                                    {"numero":"A-01","sector":"Planta Baja","tipo":"AUTO","estado":"HABILITADA"}
                                ]
                                """))
                .andExpect(status().isBadRequest());

        assertEquals(1, cocheraRepository.count());
    }

    // ---- GET /sectores ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] GET /api/v1/cocheras/sectores devuelve 401 para anonimos")
    void listarSectoresDevuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(get("/api/v1/cocheras/sectores")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("[Caja negra] GET /api/v1/cocheras/sectores devuelve 403 para USER sin rol ADMIN")
    void listarSectoresDevuelve403ParaUsuarioSinAdmin() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/sectores").with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/cocheras/sectores devuelve los sectores distintos, sin repetidos")
    void listarSectoresDevuelveSectoresDistintos() throws Exception {
        crearCocheraConSector("A-01", "Planta Baja");
        crearCocheraConSector("A-02", "Planta Baja");
        crearCocheraConSector("S-01", "Subsuelo");
        var context = getContext();

        mockMvc.perform(get("/api/v1/cocheras/sectores").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("Planta Baja"))
                .andExpect(jsonPath("$[1]").value("Subsuelo"));
    }
}
