package com.aparcar.api.integration;

import com.aparcar.api.config.DiscapacidadDePrueba;
import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VerificacionDiscapacidadRepository;
import com.aparcar.api.repository.VerificacionDocumentoAccesoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IVerificacionDiscapacidadService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra de /api/v1/verificaciones-discapacidad contra una base real y un
 * directorio temporal para los certificados.
 *
 * <p>Los barridos programados corren una vez por hora, asi que no interfieren:
 * los que se prueban aca se llaman a mano, en el mismo flujo del test.
 */
@IntegrationTests
@Sql({"/tarifas-test.sql", "/admin-test.sql"})
class VerificacionDiscapacidadControllerTests {
    private static final String BASE = "/api/v1/verificaciones-discapacidad";
    /** El principal por defecto de @WithMockUser; admin-test.sql le da una cuenta ADMIN. */
    private static final String ADMIN = "user";

    @TempDir
    static Path documentos;

    @DynamicPropertySource
    static void directorioTemporal(DynamicPropertyRegistry registry) {
        registry.add("aparcar.discapacidad.documentos-dir", () -> documentos.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired ReservaRepository reservas;
    @Autowired VerificacionDiscapacidadRepository verificaciones;
    @Autowired VerificacionDocumentoAccesoRepository accesos;
    @Autowired IVerificacionDiscapacidadService service;

    private Visitante juan;
    private Visitante eva;

    @BeforeEach
    void setUp() {
        juan = cuenta("Juan Perez", "juan@test.com", AppAuthority.USER);
        eva = cuenta("Eva Lopez", "eva@test.com", AppAuthority.USER);
    }

    @AfterEach
    void tearDown() throws Exception {
        // El directorio es de la clase: cada test arranca sin certificados.
        try (Stream<Path> archivos = Files.list(documentos)) {
            for (Path archivo : archivos.toList()) Files.delete(archivo);
        }
        accesos.deleteAll();
        verificaciones.deleteAll();
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
    }

    private Visitante cuenta(String nombre, String email, AppAuthority rol) {
        return visitantes.save(new Visitante(nombre, "doc-" + email, email, "hash", null, Set.of(rol), true));
    }

    private static RequestPostProcessor como(String email, String rol) {
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority(rol)))));
    }

    private static RequestPostProcessor admin() {
        return como(ADMIN, "ADMIN");
    }

    private static MockMultipartFile archivo(String nombre, String tipo, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, tipo, contenido);
    }

    private static MockMultipartFile pdf() {
        return archivo("CUD.pdf", "application/pdf", DiscapacidadDePrueba.pdf());
    }

    /** Sube el certificado de la cuenta y devuelve el id de la solicitud. */
    private UUID subir(Visitante visitante) throws Exception {
        String json = mvc.perform(multipart(BASE).file(pdf()).with(como(visitante.getEmail(), "USER")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.solicitud.id"));
    }

    private long archivosGuardados() throws Exception {
        try (Stream<Path> archivos = Files.list(documentos)) {
            return archivos.count();
        }
    }

    /** Intenta reservar una cochera ACCESIBLE a su nombre y devuelve el status. */
    private int reservarAccesible(Visitante visitante) throws Exception {
        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setPatente("AB" + (100 + vehiculos.count()) + "CD");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo.setVisitante(visitante);
        vehiculo = vehiculos.save(vehiculo);
        Cochera cochera = new Cochera();
        cochera.setNumero("AC-" + (10 + cocheras.count()));
        cochera.setSector("PB");
        cochera.setTipo(CocheraTipo.ACCESIBLE);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
        LocalDateTime desde = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return mvc.perform(post("/api/v1/reservas").with(como(visitante.getEmail(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\":\"" + vehiculo.getId() + "\",\"cocheraId\":\"" + cochera.getId()
                                + "\",\"desde\":\"" + desde + "\",\"hasta\":\"" + desde.plusHours(1) + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    // ---- Flujo completo ----

    @Test
    @DisplayName("[Caja negra] subir → queda en revision → el admin la ve, abre el certificado y aprueba → puede reservar ACCESIBLE")
    void flujoDeAprobacion() throws Exception {
        assertEquals(400, reservarAccesible(juan));

        String alta = mvc.perform(multipart(BASE).file(pdf()).with(como(juan.getEmail(), "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("EN_REVISION"))
                .andExpect(jsonPath("$.puedeEnviar").value(false))
                .andExpect(jsonPath("$.puedeUsarCocherasAccesibles").value(false))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(alta, "$.solicitud.id");
        assertEquals(1, archivosGuardados());

        mvc.perform(get(BASE + "/pendientes/cantidad").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(1));
        mvc.perform(get(BASE).param("estado", "EN_REVISION").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].solicitante.nombre").value("Juan Perez"))
                .andExpect(jsonPath("$[0].solicitante.email").value("juan@test.com"));

        mvc.perform(get(BASE + "/" + id + "/documento").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(DiscapacidadDePrueba.pdf()))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
        // Quien lo vio queda registrado.
        assertEquals(1, accesos.count());
        assertEquals("ADMIN", accesos.findAll().getFirst().getActorRol());

        String vence = LocalDate.now().plusYears(1).toString();
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"venceEl\":\"" + vence + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("VERIFICADA"))
                .andExpect(jsonPath("$.venceEl").value(vence))
                .andExpect(jsonPath("$.resueltaPor").value(containsString("Administrador de prueba")));

        mvc.perform(get(BASE + "/mia").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.estado").value("VERIFICADA"))
                .andExpect(jsonPath("$.puedeUsarCocherasAccesibles").value(true))
                .andExpect(jsonPath("$.solicitud.venceEl").value(vence))
                // El visitante no ve quien resolvio.
                .andExpect(jsonPath("$.solicitud.resueltaPor").doesNotExist());
        mvc.perform(get("/api/v1/visitantes/me").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.tieneDiscapacidad").value(true));
        assertEquals(201, reservarAccesible(juan));
    }

    @Test
    @DisplayName("[Caja negra] rechazar exige motivo; el visitante lo ve, no puede reservar ACCESIBLE y puede reenviar")
    void flujoDeRechazo() throws Exception {
        UUID id = subir(juan);

        mvc.perform(post(BASE + "/" + id + "/rechazar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + id + "/rechazar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/" + id + "/rechazar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"No se lee el certificado.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));

        mvc.perform(get(BASE + "/mia").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.solicitud.motivo").value("No se lee el certificado."))
                .andExpect(jsonPath("$.puedeEnviar").value(true));
        assertEquals(400, reservarAccesible(juan));

        // El rechazado se borra en el siguiente barrido; la decision queda.
        assertEquals(1, service.retirarDocumentos());
        assertEquals(0, archivosGuardados());
        mvc.perform(get(BASE + "/" + id + "/documento").with(admin())).andExpect(status().isNotFound());

        subir(juan);
    }

    @Test
    @DisplayName("[Caja negra] revocar una vigente quita el acceso a las ACCESIBLE")
    void flujoDeRevocacion() throws Exception {
        UUID id = subir(juan);
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isOk());
        assertEquals(201, reservarAccesible(juan));

        mvc.perform(post(BASE + "/" + id + "/revocar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Certificado adulterado.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADA"));

        mvc.perform(get(BASE + "/mia").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.estado").value("REVOCADA"))
                .andExpect(jsonPath("$.puedeUsarCocherasAccesibles").value(false));
        assertEquals(400, reservarAccesible(juan));
        // Las reservas ya hechas no se cancelan solas.
        assertEquals(1, reservas.count());
    }

    // ---- Reglas ----

    @Test
    @DisplayName("[Caja negra] una sola solicitud en revision: la segunda subida es 409")
    void unaSolaEnRevision() throws Exception {
        subir(juan);

        mvc.perform(multipart(BASE).file(pdf()).with(como(juan.getEmail(), "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("en revisión")));
        assertEquals(1, archivosGuardados());
    }

    @Test
    @DisplayName("[Caja negra] resolver dos veces la misma solicitud: la segunda es 409 y no la procesa de nuevo")
    void dobleResolucion() throws Exception {
        UUID id = subir(juan);
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isOk());

        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isConflict());
        mvc.perform(post(BASE + "/" + id + "/rechazar").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"tarde\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("ya fue resuelta")));
        assertEquals(VerificacionEstado.VERIFICADA, verificaciones.findById(id).orElseThrow().getEstado());
    }

    @Test
    @DisplayName("[Caja negra] archivo vacio 400, formato falso 415, grande 413, sin archivo 400, JSON en vez de multipart 415")
    void erroresDeArchivo() throws Exception {
        mvc.perform(multipart(BASE).file(archivo("c.pdf", "application/pdf", new byte[0]))
                        .with(como(juan.getEmail(), "USER")))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(BASE).file(archivo("c.pdf", "application/pdf", DiscapacidadDePrueba.png()))
                        .with(como(juan.getEmail(), "USER")))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(BASE).file(archivo("c.exe", "application/octet-stream", DiscapacidadDePrueba.pdf()))
                        .with(como(juan.getEmail(), "USER")))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart(BASE).file(archivo("c.pdf", "application/pdf",
                        DiscapacidadDePrueba.pdfDe(5 * 1024 * 1024 + 1)))
                        .with(como(juan.getEmail(), "USER")))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.message").value(containsString("5 MB")));
        mvc.perform(multipart(BASE).with(como(juan.getEmail(), "USER")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE).with(como(juan.getEmail(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tieneDiscapacidad\":true}"))
                .andExpect(status().isUnsupportedMediaType());

        assertEquals(0, verificaciones.count());
        assertEquals(0, archivosGuardados());
    }

    // ---- Seguridad ----

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"USER", "EMPLEADO"})
    @DisplayName("[Caja negra] USER y EMPLEADO reciben 403 en todos los endpoints del admin")
    void soloAdminRevisa(String rol) throws Exception {
        UUID id = subir(juan);
        RequestPostProcessor quien = como(eva.getEmail(), rol);

        mvc.perform(get(BASE).with(quien)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/pendientes/cantidad").with(quien)).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/" + id + "/documento").with(quien)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(quien)).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/rechazar").with(quien)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/" + id + "/revocar").with(quien)
                .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"x\"}")).andExpect(status().isForbidden());
        assertEquals(VerificacionEstado.EN_REVISION, verificaciones.findById(id).orElseThrow().getEstado());
        assertEquals(0, accesos.count());
    }

    @Test
    @DisplayName("[Caja negra] un USER nunca ve el certificado de otro: ni por id ni por /mia")
    void unUserNoVeElDeOtro() throws Exception {
        UUID id = subir(juan);

        mvc.perform(get(BASE + "/" + id + "/documento").with(como(eva.getEmail(), "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/mia/documento").with(como(eva.getEmail(), "USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/mia").with(como(eva.getEmail(), "USER")))
                .andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"))
                .andExpect(jsonPath("$.solicitud").doesNotExist());

        // El dueño si ve el suyo, y queda auditado como USER.
        mvc.perform(get(BASE + "/mia/documento").with(como(juan.getEmail(), "USER")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DiscapacidadDePrueba.pdf()));
        assertEquals("USER", accesos.findAll().getFirst().getActorRol());
    }

    @Test
    @DisplayName("[Caja negra] un anonimo recibe 401 al subir o consultar")
    void anonimo() throws Exception {
        mvc.perform(multipart(BASE).file(pdf())).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/mia")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Caja negra] el visitante puede cancelar su solicitud en revision y volver a enviar")
    void cancelar() throws Exception {
        subir(juan);

        mvc.perform(post(BASE + "/mia/cancelar").with(como(juan.getEmail(), "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"));
        mvc.perform(post(BASE + "/mia/cancelar").with(como(juan.getEmail(), "USER")))
                .andExpect(status().isConflict());
        subir(juan);
    }

    // ---- Migracion de cuentas autodeclaradas ----

    @Test
    @DisplayName("[Caja negra] una cuenta autodeclarada conserva el acceso durante la gracia y lo pierde al terminar")
    void graciaDeMigracion() throws Exception {
        juan.setTieneDiscapacidad(true);
        visitantes.save(juan);

        assertEquals(1, service.migrarAutodeclarados());
        assertEquals(0, service.migrarAutodeclarados());
        Instant hasta = visitantes.findById(juan.getId()).orElseThrow().getDiscapacidadAccesoProvisorioHasta();
        assertTrue(hasta.isAfter(Instant.now().plus(29, ChronoUnit.DAYS)));
        mvc.perform(get(BASE + "/mia").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.puedeUsarCocherasAccesibles").value(true))
                .andExpect(jsonPath("$.accesoProvisorioHasta").isNotEmpty())
                .andExpect(jsonPath("$.puedeEnviar").value(true));
        assertEquals(201, reservarAccesible(juan));

        // Termina el plazo sin certificado: el barrido le quita el acceso.
        Visitante vencido = visitantes.findById(juan.getId()).orElseThrow();
        vencido.setDiscapacidadAccesoProvisorioHasta(Instant.now().minusSeconds(60));
        visitantes.save(vencido);
        assertEquals(1, service.terminarAccesosProvisorios());
        assertFalse(visitantes.findById(juan.getId()).orElseThrow().getTieneDiscapacidad());
        assertEquals(400, reservarAccesible(juan));
    }

    @Test
    @DisplayName("[Caja negra] si subio el certificado durante la gracia, conserva el acceso hasta que lo revisen")
    void graciaConSolicitudEnRevision() throws Exception {
        juan.setTieneDiscapacidad(true);
        juan.setDiscapacidadAccesoProvisorioHasta(Instant.now().minusSeconds(60));
        visitantes.save(juan);
        subir(juan);

        assertEquals(0, service.terminarAccesosProvisorios());
        assertTrue(visitantes.findById(juan.getId()).orElseThrow().getTieneDiscapacidad());
    }

    @Test
    @DisplayName("[Caja negra] una verificacion con vencimiento ayer pasa a VENCIDA y pierde el acceso")
    void vencimiento() throws Exception {
        UUID id = subir(juan);
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isOk());
        var aprobada = verificaciones.findById(id).orElseThrow();
        aprobada.setVenceEl(LocalDate.now().minusDays(1));
        verificaciones.save(aprobada);

        assertEquals(1, service.vencerVerificaciones());

        mvc.perform(get(BASE + "/mia").with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$.estado").value("VENCIDA"))
                .andExpect(jsonPath("$.puedeEnviar").value(true));
        assertEquals(400, reservarAccesible(juan));
    }

    @Test
    @DisplayName("[Caja negra] /disponibles no le ofrece ACCESIBLE a un USER sin verificacion vigente")
    void disponiblesSinVerificacion() throws Exception {
        Cochera cochera = new Cochera();
        cochera.setNumero("AC-99");
        cochera.setSector("PB");
        cochera.setTipo(CocheraTipo.ACCESIBLE);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cocheras.save(cochera);
        LocalDateTime desde = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        String url = "/api/v1/cocheras/disponibles?desde=" + desde + "&hasta=" + desde.plusHours(1);
        subir(juan);

        mvc.perform(get(url).with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$[*].numero", not(hasItem("AC-99"))));
        UUID id = verificaciones.findAll().getFirst().getId();
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isOk());
        mvc.perform(get(url).with(como(juan.getEmail(), "USER")))
                .andExpect(jsonPath("$[*].numero", hasItem("AC-99")));
    }

    @Test
    @DisplayName("[Caja negra] el listado acepta filtrar por estado y rechaza uno inexistente")
    void filtroPorEstado() throws Exception {
        UUID id = subir(juan);
        subir(eva);
        mvc.perform(post(BASE + "/" + id + "/aprobar").with(admin())).andExpect(status().isOk());

        mvc.perform(get(BASE).with(admin())).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(BASE).param("estado", "VERIFICADA").with(admin()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].solicitante.email").value("juan@test.com"));
        mvc.perform(get(BASE).param("estado", "NO_EXISTE").with(admin())).andExpect(status().isBadRequest());
    }
}
