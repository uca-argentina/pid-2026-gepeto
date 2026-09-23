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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra para /api/v1/reservas: ejercita las reglas de negocio
 * (compatibilidad de tipos, doble reserva) desde el contrato HTTP, contra
 * una base real (sin mocks), a diferencia de ReservaServiceTests.
 */
@IntegrationTests
public class ReservaControllerTests {

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @Autowired
    private VisitanteRepository visitanteRepository;

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

    // El visitante ES la cuenta de login, asi que crear uno implica darle
    // email, contraseña y rol.
    private Visitante crearVisitante(String documento) {
        return crearVisitante(documento, documento + "@test.com");
    }

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

    private Vehiculo crearVehiculo(String patente, VehiculoTipo tipo, Visitante visitante) {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente(patente);
        vehiculo.setTipo(tipo);
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

    private String reservaJson(UUID visitanteId, UUID vehiculoId, UUID cocheraId, LocalDate fecha) {
        return "{\"visitanteId\":\"" + visitanteId + "\",\"vehiculoId\":\"" + vehiculoId
                + "\",\"cocheraId\":\"" + cocheraId + "\",\"fecha\":\"" + fecha + "\"}";
    }

    // ---- Seguridad ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] endpoints de reservas devuelven 401 para anonimos")
    void devuelve401ParaAnonimos() throws Exception {
        mockMvc.perform(get("/api/v1/reservas")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/reservas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- Validacion basica ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si falta la fecha")
    void crearDevuelve400SiFaltaFecha() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visitanteId\":\"" + visitante.getId() + "\",\"vehiculoId\":\"" + vehiculo.getId()
                                + "\",\"cocheraId\":\"" + cochera.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si la fecha es anterior a hoy")
    void crearDevuelve400SiFechaEsPasada() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    // ---- Not found ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 404 si el visitante no existe")
    void crearDevuelve404SiVisitanteNoExiste() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(UUID.randomUUID(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 404 si la cochera no existe")
    void crearDevuelve404SiCocheraNoExiste() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), UUID.randomUUID(), LocalDate.now())))
                .andExpect(status().isNotFound());
    }

    // ---- Reglas de negocio ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si el vehiculo no pertenece al visitante indicado")
    void crearDevuelve400SiVehiculoNoPerteneceAlVisitante() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Visitante otroVisitante = crearVisitante("30111333");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, otroVisitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si el tipo de cochera no es compatible")
    void crearDevuelve400SiTiposNoSonCompatibles() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("M-01", CocheraTipo.MOTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si la cochera ya tiene una reserva confirmada ese dia")
    void crearDevuelve400SiCocheraYaEstaReservada() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        LocalDate fecha = LocalDate.now();

        Reserva existente = new Reserva();
        existente.setVisitante(visitante);
        existente.setVehiculo(vehiculo);
        existente.setCochera(cochera);
        existente.setFecha(fecha);
        existente.setEstado(ReservaEstado.CONFIRMADA);
        reservaRepository.save(existente);

        Visitante otroVisitante = crearVisitante("30111333");
        Vehiculo otroVehiculo = crearVehiculo("XYZ999", VehiculoTipo.AUTO, otroVisitante);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(otroVisitante.getId(), otroVehiculo.getId(), cochera.getId(), fecha)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 201 y queda CONFIRMADA cuando todo es valido")
    void crearDevuelve201YQuedaConfirmada() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.vehiculo.patente").value("ABC123"))
                .andExpect(jsonPath("$.cochera.numero").value("A-01"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas permite reservar una cochera ACCESIBLE con cualquier tipo de vehiculo")
    void crearPermiteCocheraAccesibleConCualquierVehiculo() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.CARGA, visitante);
        Cochera cochera = crearCochera("AC-01", CocheraTipo.ACCESIBLE);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(visitante.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    // ---- Listar / obtener por id ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/reservas/{id} devuelve 404 si no existe")
    void obtenerPorIdDevuelve404SiNoExiste() throws Exception {
        var context = getContext();

        mockMvc.perform(get("/api/v1/reservas/" + UUID.randomUUID()).with(securityContext(context)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] GET /api/v1/reservas devuelve todas las reservas cargadas")
    void listarDevuelveTodasLasReservas() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);

        Reserva reserva = new Reserva();
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setFecha(LocalDate.now());
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        reservaRepository.save(reserva);

        var context = getContext();

        mockMvc.perform(get("/api/v1/reservas").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ---- Un visitante solo reserva a su nombre ----

    // El visitanteId es un campo que solo puede usar un ADMIN. Si lo manda un
    // USER apuntando a otra persona, el backend lo ignora y usa su cuenta: no
    // alcanza con que el frontend no lo mande.
    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] POST /api/v1/reservas ignora el visitanteId ajeno cuando quien reserva es un USER")
    void crearIgnoraElVisitanteIdAjenoCuandoEsUser() throws Exception {
        Visitante dueño = crearVisitante("30111222", "dueño@test.com");
        Visitante otro = crearVisitante("30111333", "otro@test.com");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, dueño);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(otro.getId(), vehiculo.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.visitante.id").value(dueño.getId().toString()));
    }

    // Como el visitante de la reserva siempre es el autenticado, reservar con
    // el vehiculo de otro no puede funcionar: el vehiculo no le pertenece.
    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] POST /api/v1/reservas devuelve 400 si un USER intenta reservar con el vehiculo de otro")
    void crearDevuelve400SiUnUserUsaElVehiculoDeOtro() throws Exception {
        crearVisitante("30111222", "dueño@test.com");
        Visitante otro = crearVisitante("30111333", "otro@test.com");
        Vehiculo ajeno = crearVehiculo("XYZ999", VehiculoTipo.AUTO, otro);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(otro.getId(), ajeno.getId(), cochera.getId(), LocalDate.now())))
                .andExpect(status().isBadRequest());
    }

    // Antes GET /reservas devolvia todas las reservas del sistema a cualquier
    // autenticado: un visitante veia las de todos.
    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] GET /api/v1/reservas devuelve solo las reservas propias a un visitante")
    void listarDevuelveSoloLasPropiasAUnVisitante() throws Exception {
        Visitante dueño = crearVisitante("30111222", "dueño@test.com");
        Visitante otro = crearVisitante("30111333", "otro@test.com");
        crearReserva(dueño, crearVehiculo("ABC123", VehiculoTipo.AUTO, dueño), crearCochera("A-01", CocheraTipo.AUTO));
        crearReserva(otro, crearVehiculo("XYZ999", VehiculoTipo.AUTO, otro), crearCochera("A-02", CocheraTipo.AUTO));
        var context = getContext();

        mockMvc.perform(get("/api/v1/reservas").with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].vehiculo.patente").value("ABC123"));
    }

    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] GET /api/v1/reservas/{id} devuelve 403 si la reserva es de otro visitante")
    void obtenerPorIdDevuelve403SiLaReservaEsDeOtro() throws Exception {
        crearVisitante("30111222", "dueño@test.com");
        Visitante otro = crearVisitante("30111333", "otro@test.com");
        Reserva ajena = crearReserva(otro, crearVehiculo("XYZ999", VehiculoTipo.AUTO, otro),
                crearCochera("A-01", CocheraTipo.AUTO));
        var context = getContext();

        mockMvc.perform(get("/api/v1/reservas/" + ajena.getId()).with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    // ---- Cancelar ----

    // Lo que justifica cancelar en vez de borrar: la fila sigue ahi con su
    // historial, pero la cochera vuelve a estar libre para esa fecha.
    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] POST /api/v1/reservas/{id}/cancelar libera la cochera sin borrar la reserva")
    void cancelarLiberaLaCocheraSinBorrarLaReserva() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Cochera cochera = crearCochera("A-01", CocheraTipo.AUTO);
        Reserva reserva = crearReserva(visitante, vehiculo, cochera);
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas/" + reserva.getId() + "/cancelar")
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        // La reserva no se borro...
        assertEquals(1, reservaRepository.count());

        // ...y la cochera volvio a aparecer como disponible para hoy.
        mockMvc.perform(get("/api/v1/cocheras/disponibles")
                        .param("fecha", LocalDate.now().toString())
                        .param("tipoVehiculo", "AUTO")
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.numero == 'A-01')]").exists());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] cancelar dos veces la misma reserva devuelve 400")
    void cancelarDosVecesDevuelve400() throws Exception {
        Visitante visitante = crearVisitante("30111222");
        Vehiculo vehiculo = crearVehiculo("ABC123", VehiculoTipo.AUTO, visitante);
        Reserva reserva = crearReserva(visitante, vehiculo, crearCochera("A-01", CocheraTipo.AUTO));
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas/" + reserva.getId() + "/cancelar")
                        .with(securityContext(context)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/reservas/" + reserva.getId() + "/cancelar")
                        .with(securityContext(context)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] un visitante puede cancelar su propia reserva")
    void unVisitantePuedeCancelarSuPropiaReserva() throws Exception {
        Visitante dueño = crearVisitante("30111222", "dueño@test.com");
        Reserva propia = crearReserva(dueño, crearVehiculo("ABC123", VehiculoTipo.AUTO, dueño),
                crearCochera("A-01", CocheraTipo.AUTO));
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas/" + propia.getId() + "/cancelar")
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    @WithMockUser(username = "dueño@test.com", authorities = "USER")
    @DisplayName("[Caja negra] un visitante no puede cancelar la reserva de otro")
    void unVisitanteNoPuedeCancelarLaReservaDeOtro() throws Exception {
        crearVisitante("30111222", "dueño@test.com");
        Visitante otro = crearVisitante("30111333", "otro@test.com");
        Reserva ajena = crearReserva(otro, crearVehiculo("XYZ999", VehiculoTipo.AUTO, otro),
                crearCochera("A-01", CocheraTipo.AUTO));
        var context = getContext();

        mockMvc.perform(post("/api/v1/reservas/" + ajena.getId() + "/cancelar")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());

        assertEquals(ReservaEstado.CONFIRMADA,
                reservaRepository.findById(ajena.getId()).orElseThrow().getEstado());
    }

    private Reserva crearReserva(Visitante visitante, Vehiculo vehiculo, Cochera cochera) {
        Reserva reserva = new Reserva();
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setFecha(LocalDate.now());
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reservaRepository.save(reserva);
    }
}
