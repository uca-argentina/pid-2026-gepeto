package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.dto.acceso.AccesoAccion;
import com.aparcar.api.dto.acceso.AccesoRegistroResponseDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaAccion;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.AccesoIntentoRepository;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IAccesoService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra de /api/v1/accesos contra una base real.
 *
 * <p>Los datos se arman en el propio test y relativos al reloj real, de forma
 * que el barrido programado de vencidas no cambie el resultado si llega a
 * correr en el medio: las reservas vigentes estan lejos de vencer, y las
 * vencidas dan el mismo 400 las marque el barrido o la validacion.
 */
@IntegrationTests
@Sql({"/tarifas-test.sql", "/admin-test.sql"})
class AccesoControllerTests {
    /** El principal por defecto de @WithMockUser; admin-test.sql le da una cuenta ADMIN. */
    private static final String ADMIN = "user";

    @Autowired MockMvc mvc;
    @Autowired ReservaRepository reservas;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired AccesoIntentoRepository intentos;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean IAccesoService accesoService;

    private Visitante usuario;
    private Visitante otroUsuario;
    private Vehiculo vehiculo;
    private Cochera cochera;
    private LocalDateTime ahora;
    private RequestPostProcessor sesion;

    @BeforeEach
    void setUp() {
        sesion = securityContext(getContext());
        usuario = visitantes.save(new Visitante("Juan Usuario", "acc-1", "juan@test.com", "hash", null,
                Set.of(AppAuthority.USER), true));
        otroUsuario = visitantes.save(new Visitante("Eva Usuario", "acc-2", "eva@test.com", "hash", null,
                Set.of(AppAuthority.USER), true));
        vehiculo = new Vehiculo();
        vehiculo.setVisitante(usuario);
        vehiculo.setPatente("AB123CD");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo = vehiculos.save(vehiculo);
        cochera = new Cochera();
        cochera.setNumero("ACC-01");
        cochera.setSector("Planta Baja");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
        ahora = LocalDateTime.now(clock);
    }

    @AfterEach
    void tearDown() {
        intentos.deleteAll();
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
    }

    /**
     * La sesion de @WithMockUser, capturada una vez por test como en el resto
     * de los tests de integracion: despues de cada request el filtro de
     * seguridad limpia el contexto del hilo.
     */
    private RequestPostProcessor sesion() {
        return sesion;
    }

    private static RequestPostProcessor como(String email, String rol) {
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority(rol)))));
    }

    /** Una reserva armada directo en la base, con su codigo. */
    private Reserva reserva(String codigo, ReservaEstado estado, LocalDateTime desde, LocalDateTime hasta) {
        Reserva reserva = new Reserva();
        reserva.setDesde(desde);
        reserva.setHasta(hasta);
        reserva.setVisitante(usuario);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(estado);
        reserva.setCodigo(codigo);
        reserva.setCodigoEmitidoAt(desde.minusHours(1).atZone(clock.getZone()).toInstant());
        return reservas.save(reserva);
    }

    private static String json(String codigo) {
        return "{\"codigo\":\"" + codigo + "\"}";
    }

    private static String json(String codigo, AccesoAccion accion) {
        return "{\"codigo\":\"" + codigo + "\",\"accion\":\"" + accion + "\"}";
    }

    /** La franja vigente que arranca en el bloque de 15 minutos en curso. */
    private LocalDateTime bloqueActual() {
        return ahora.withMinute(ahora.getMinute() / 15 * 15).withSecond(0).withNano(0);
    }

    // ---- Seguridad ----

    @Test
    @WithAnonymousUser
    @DisplayName("[Caja negra] un anonimo recibe 401")
    void anonimoRecibe401() throws Exception {
        mvc.perform(post("/api/v1/accesos/consulta").contentType(MediaType.APPLICATION_JSON).content(json("X")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "USER")
    @DisplayName("[Caja negra] un USER recibe 403 en los tres endpoints: solo el ADMIN valida")
    void userRecibe403() throws Exception {
        reserva("ABCDE12345", ReservaEstado.CONFIRMADA, bloqueActual(), bloqueActual().plusHours(1));

        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.INGRESO)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accesos/movimientos").with(sesion())).andExpect(status().isForbidden());
        assertEquals(ReservaEstado.CONFIRMADA, reservas.findByCodigo("ABCDE12345").orElseThrow().getEstado());
    }

    // ---- Flujo completo ----

    @Test
    @DisplayName("[Caja negra] reservar, ver el QR, ingresar y egresar: la cochera se ocupa y se libera")
    void flujoCompleto() throws Exception {
        LocalDateTime desde = bloqueActual();
        LocalDateTime hasta = desde.plusHours(1);
        String alta = mvc.perform(post("/api/v1/reservas").with(como(usuario.getEmail(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\":\"" + vehiculo.getId() + "\",\"cocheraId\":\"" + cochera.getId()
                                + "\",\"desde\":\"" + desde + "\",\"hasta\":\"" + hasta + "\"}"))
                .andExpect(status().isCreated())
                // El codigo se genera en el alta, en la misma operacion.
                .andExpect(jsonPath("$.acceso.codigo").isString())
                .andExpect(jsonPath("$.acceso.ingresoDesde").value(containsString(desde.minusMinutes(15).toString())))
                .andReturn().getResponse().getContentAsString();
        String codigo = JsonPath.read(alta, "$.acceso.codigo");
        assertEquals(10, codigo.length());

        // El QR lleva el prefijo; el lector lo ignora.
        mvc.perform(post("/api/v1/accesos/consulta").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("APARCAR:" + codigo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.accion").value("INGRESO"))
                .andExpect(jsonPath("$.reserva.visitanteNombre").value("Juan Usuario"))
                .andExpect(jsonPath("$.reserva.patente").value("AB123CD"))
                .andExpect(jsonPath("$.reserva.cocheraNumero").value("ACC-01"));

        // Tipeado con guion y en minusculas es lo mismo.
        String tipeado = (codigo.substring(0, 5) + "-" + codigo.substring(5)).toLowerCase();
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(tipeado, AccesoAccion.INGRESO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaRegistrado").value(false))
                .andExpect(jsonPath("$.reserva.estado").value("EN_CURSO"));

        // Con el vehiculo adentro, la cochera no figura libre en su franja.
        String libres = "/api/v1/cocheras/disponibles?desde=" + desde + "&hasta=" + hasta;
        mvc.perform(get(libres).with(como(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].numero", not(hasItem("ACC-01"))));

        // Doble clic: no se registra dos veces.
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(codigo, AccesoAccion.INGRESO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaRegistrado").value(true));

        // Un segundo escaneo enseguida no registra la salida.
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(codigo, AccesoAccion.EGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("PERMANENCIA_MINIMA"));

        // Pasada la permanencia minima, sale.
        Reserva enCurso = reservas.findByCodigo(codigo).orElseThrow();
        enCurso.setIngresoAt(enCurso.getIngresoAt().minusMinutes(5));
        reservas.save(enCurso);
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(codigo, AccesoAccion.EGRESO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excedido").value(false))
                .andExpect(jsonPath("$.reserva.estado").value("FINALIZADA"));

        mvc.perform(get(libres).with(como(ADMIN, "ADMIN")))
                .andExpect(jsonPath("$[*].numero", hasItem("ACC-01")));

        // Uso unico.
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json(codigo, AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("YA_UTILIZADA"));

        mvc.perform(get("/api/v1/accesos/movimientos").with(como(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accion").value("EGRESO"))
                .andExpect(jsonPath("$[1].accion").value("INGRESO"))
                .andExpect(jsonPath("$[0].operador").value("Administrador de prueba"));

        // Cada intento quedo registrado, tambien los rechazados.
        assertTrue(intentos.findAll().stream().anyMatch(i -> i.getResultado().equals("PERMANENCIA_MINIMA")));
        assertTrue(intentos.findAll().stream().anyMatch(i -> i.getResultado().equals("YA_REGISTRADO")));
    }

    // ---- Visibilidad del codigo ----

    @Test
    @DisplayName("[Caja negra] el USER ve su codigo y nunca el de otro; el ADMIN ve todos")
    void elUserVeSoloSuCodigo() throws Exception {
        Reserva propia = reserva("ABCDE12345", ReservaEstado.CONFIRMADA, ahora.plusHours(2), ahora.plusHours(3));

        mvc.perform(get("/api/v1/reservas").with(como(usuario.getEmail(), "USER")))
                .andExpect(jsonPath("$[0].acceso.codigo").value("ABCDE12345"));
        mvc.perform(get("/api/v1/reservas").with(como(otroUsuario.getEmail(), "USER")))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/reservas/" + propia.getId()).with(como(otroUsuario.getEmail(), "USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservas/" + propia.getId()).with(como(ADMIN, "ADMIN")))
                .andExpect(jsonPath("$.acceso.codigo").value("ABCDE12345"));
    }

    @Test
    @DisplayName("[Caja negra] una reserva cancelada ya no muestra su codigo")
    void canceladaNoMuestraCodigo() throws Exception {
        reserva("ABCDE12345", ReservaEstado.CANCELADA, ahora.plusHours(2), ahora.plusHours(3));

        mvc.perform(get("/api/v1/reservas").with(como(usuario.getEmail(), "USER")))
                .andExpect(jsonPath("$[0].acceso.codigo").value(nullValue()));
    }

    // ---- Errores ----

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] codigo con formato invalido → 400; sin codigo o sin accion → 400")
    void formatoInvalido() throws Exception {
        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON).content(json("ABC")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("formato")));
        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345")))
                .andExpect(status().isBadRequest());
        assertTrue(intentos.findAll().stream().anyMatch(i -> i.getResultado().equals("FORMATO_INVALIDO")));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] codigo inexistente → 404 en consulta y en registro")
    void inexistente() throws Exception {
        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ZZZZZ-99999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No existe ninguna reserva con el código ZZZZZ-99999."));
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ZZZZZ99999", AccesoAccion.INGRESO)))
                .andExpect(status().isNotFound());
        assertEquals(2, intentos.findAll().stream().filter(i -> i.getResultado().equals("INEXISTENTE")).count());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] fuera de tolerancia (anticipado): la consulta lo explica y el registro da 400")
    void fueraDeTolerancia() throws Exception {
        reserva("ABCDE12345", ReservaEstado.CONFIRMADA, ahora.plusHours(2), ahora.plusHours(3));

        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("ANTICIPADO"));
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("ANTICIPADO"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] vencida (no-show) → 400 y queda VENCIDA")
    void vencida() throws Exception {
        reserva("ABCDE12345", ReservaEstado.CONFIRMADA, ahora.minusHours(2), ahora.plusHours(1));

        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("VENCIDA"));
        assertEquals(ReservaEstado.VENCIDA, reservas.findByCodigo("ABCDE12345").orElseThrow().getEstado());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] cancelada → 400")
    void cancelada() throws Exception {
        reserva("ABCDE12345", ReservaEstado.CANCELADA, bloqueActual(), bloqueActual().plusHours(1));

        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("CANCELADA"))
                .andExpect(jsonPath("$.message").value("La reserva fue cancelada: el código ya no es válido."));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] ya usada → 400; egreso sin ingreso → 400")
    void yaUsadaYEgresoSinIngreso() throws Exception {
        Reserva usada = reserva("ABCDE12345", ReservaEstado.FINALIZADA, ahora.minusHours(3), ahora.minusHours(1));
        usada.setIngresoAt(ahora.minusHours(3));
        usada.setEgresoAt(ahora.minusHours(1));
        reservas.save(usada);
        reserva("FGHJK67890", ReservaEstado.CONFIRMADA, bloqueActual(), bloqueActual().plusHours(1));

        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.EGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("YA_UTILIZADA"));
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("FGHJK67890", AccesoAccion.EGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("SIN_INGRESO"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] un vehiculo que se paso de hora egresa igual, marcado como excedido")
    void egresoExcedido() throws Exception {
        Reserva adentro = reserva("ABCDE12345", ReservaEstado.EN_CURSO, ahora.minusHours(2), ahora.minusMinutes(40));
        adentro.setIngresoAt(ahora.minusHours(2));
        reservas.save(adentro);

        // Mientras siga adentro, la cochera no figura libre aunque su franja ya termino.
        mvc.perform(get("/api/v1/cocheras/disponibles?desde=" + bloqueActual() + "&hasta="
                        + bloqueActual().plusHours(1)).with(sesion()))
                .andExpect(jsonPath("$[*].numero", not(hasItem("ACC-01"))));

        String respuesta = mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.EGRESO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.excedido").value(true))
                .andReturn().getResponse().getContentAsString();
        int exceso = JsonPath.read(respuesta, "$.minutosExceso");
        assertTrue(exceso >= 40 && exceso <= 41, "exceso: " + exceso);
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] una cochera ocupada rechaza el siguiente codigo hasta registrar el egreso anterior")
    void ingresoEsperaElEgresoAnterior() throws Exception {
        Reserva anterior = reserva("ABCDE12345", ReservaEstado.EN_CURSO, ahora.minusHours(2), ahora.minusMinutes(5));
        anterior.setIngresoAt(ahora.minusHours(2));
        reservas.save(anterior);
        reserva("FGHJK67890", ReservaEstado.CONFIRMADA, ahora.minusMinutes(5), ahora.plusHours(1));

        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("APARCAR:FGHJK67890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("COCHERA_OCUPADA"));
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("FGHJK67890", AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("COCHERA_OCUPADA"));
        assertEquals(ReservaEstado.CONFIRMADA, reservas.findByCodigo("FGHJK67890").orElseThrow().getEstado());

        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.EGRESO)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("FGHJK67890", AccesoAccion.INGRESO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserva.estado").value("EN_CURSO"));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] la tolerancia anticipada no permite ingresar un vehiculo que sigue adentro")
    void vehiculoNoPuedeIngresarConDosReservas() throws Exception {
        Reserva anterior = reserva("ABCDE12345", ReservaEstado.EN_CURSO, ahora.minusHours(1), ahora.plusMinutes(10));
        anterior.setIngresoAt(ahora.minusHours(1));
        reservas.save(anterior);

        Cochera otra = new Cochera();
        otra.setNumero("ACC-02");
        otra.setSector("Planta Baja");
        otra.setTipo(CocheraTipo.AUTO);
        otra.setEstado(CocheraEstado.HABILITADA);
        otra = cocheras.save(otra);
        Reserva siguiente = reserva("FGHJK67890", ReservaEstado.CONFIRMADA, ahora.plusMinutes(10), ahora.plusHours(1));
        siguiente.setCochera(otra);
        reservas.save(siguiente);

        mvc.perform(post("/api/v1/accesos/consulta").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("FGHJK-67890")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(false))
                .andExpect(jsonPath("$.motivo").value("VEHICULO_EN_CURSO"));
        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("FGHJK67890", AccesoAccion.INGRESO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("VEHICULO_EN_CURSO"));
        assertEquals(ReservaEstado.CONFIRMADA, reservas.findByCodigo("FGHJK67890").orElseThrow().getEstado());
    }

    @ParameterizedTest
    @EnumSource(AccesoAccion.class)
    @DisplayName("[Concurrencia] dos operadores con el mismo codigo registran un solo movimiento")
    void mismoCodigoConcurrente(AccesoAccion accion) throws Exception {
        Reserva reserva = reserva("ABCDE12345",
                accion == AccesoAccion.INGRESO ? ReservaEstado.CONFIRMADA : ReservaEstado.EN_CURSO,
                ahora.minusMinutes(10), ahora.plusHours(1));
        if (accion == AccesoAccion.EGRESO) {
            reserva.setIngresoAt(ahora.minusMinutes(5));
            reservas.save(reserva);
        }

        List<Object> resultados = registrarSimultaneamente("ABCDE12345", "ABCDE12345", accion);

        assertEquals(1, resultados.stream().filter(AccesoRegistroResponseDto.class::isInstance).count());
        assertEquals(1, resultados.stream().filter(ObjectOptimisticLockingFailureException.class::isInstance).count());
        Reserva guardada = reservas.findAll().getFirst();
        assertEquals(accion == AccesoAccion.INGRESO ? ReservaEstado.EN_CURSO : ReservaEstado.FINALIZADA,
                guardada.getEstado());
        assertEquals(1, guardada.getHistorial().stream()
                .filter(m -> m.getAccion() == ReservaAccion.valueOf(accion.name())).count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("[Concurrencia] dos codigos distintos no pueden ocupar la misma cochera o el mismo vehiculo")
    void codigosDistintosConcurrentes(boolean mismaCochera) throws Exception {
        reserva("ABCDE12345", ReservaEstado.CONFIRMADA, ahora.minusMinutes(20), ahora.plusMinutes(10));
        Reserva siguiente = reserva("FGHJK67890", ReservaEstado.CONFIRMADA, ahora.plusMinutes(10), ahora.plusHours(1));
        if (mismaCochera) {
            Vehiculo otro = new Vehiculo();
            otro.setVisitante(usuario);
            otro.setPatente("AC456EF");
            otro.setTipo(VehiculoTipo.AUTO);
            siguiente.setVehiculo(vehiculos.save(otro));
        } else {
            Cochera otra = new Cochera();
            otra.setNumero("ACC-02");
            otra.setSector("Planta Baja");
            otra.setTipo(CocheraTipo.AUTO);
            otra.setEstado(CocheraEstado.HABILITADA);
            siguiente.setCochera(cocheras.save(otra));
        }
        reservas.save(siguiente);

        List<Object> resultados = registrarSimultaneamente("ABCDE12345", "FGHJK67890", AccesoAccion.INGRESO);

        assertEquals(1, resultados.stream().filter(AccesoRegistroResponseDto.class::isInstance).count());
        ValidationException rechazo = (ValidationException) resultados.stream()
                .filter(ValidationException.class::isInstance).findFirst().orElseThrow();
        assertEquals(List.of(mismaCochera ? "COCHERA_OCUPADA" : "VEHICULO_EN_CURSO"), rechazo.getErrors());
        assertEquals(1, reservas.findAll().stream().filter(r -> r.getEstado() == ReservaEstado.EN_CURSO).count());
        assertEquals(1, reservas.findAll().stream().flatMap(r -> r.getHistorial().stream())
                .filter(m -> m.getAccion() == ReservaAccion.INGRESO).count());
    }

    private List<Object> registrarSimultaneamente(String primerCodigo, String segundoCodigo, AccesoAccion accion)
            throws Exception {
        CountDownLatch leidas = new CountDownLatch(2);
        CountDownLatch registrar = new CountDownLatch(1);
        try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tareas = List.of(primerCodigo, segundoCodigo).stream().map(codigo -> ejecutor.submit(() -> {
                try {
                    return (Object) new TransactionTemplate(transactionManager).execute(status -> {
                        // Ambas transacciones leen el mismo estado antes de competir.
                        reservas.findByCodigo(codigo).orElseThrow();
                        leidas.countDown();
                        try {
                            if (!registrar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tiempo agotado");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return accesoService.registrar(codigo, accion, ADMIN);
                    });
                } catch (RuntimeException e) {
                    return e;
                }
            })).toList();
            try {
                assertTrue(leidas.await(10, TimeUnit.SECONDS), "Las dos transacciones deben estar listas");
            } finally {
                registrar.countDown();
            }
            return List.of(tareas.get(0).get(20, TimeUnit.SECONDS), tareas.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("[Caja negra] un conflicto de concurrencia (optimistic lock) → 409")
    void conflictoDeConcurrencia() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(Reserva.class, UUID.randomUUID()))
                .when(accesoService).registrar(anyString(), any(), anyString());

        mvc.perform(post("/api/v1/accesos/registro").with(sesion()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("ABCDE12345", AccesoAccion.INGRESO)))
                .andExpect(status().isConflict());
    }
}
