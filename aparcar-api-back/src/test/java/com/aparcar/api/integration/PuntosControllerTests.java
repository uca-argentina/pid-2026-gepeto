package com.aparcar.api.integration;

import com.aparcar.api.component.ReservasVencidas;
import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.puntos.FranjaPuntos;
import com.aparcar.api.entity.puntos.MovimientoPuntos;
import com.aparcar.api.entity.puntos.TipoMovimientoPuntos;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.BilleteraPuntosRepository;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.MovimientoPuntosRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IPuntosService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra de los puntos contra una base real: billetera, cotizacion con
 * descuento, canje al reservar, acreditacion al ingresar y reintegro.
 */
@IntegrationTests
@Sql({"/tarifas-test.sql", "/admin-test.sql"})
class PuntosControllerTests {
    /** admin-test.sql le da una cuenta ADMIN a este email. */
    private static final String ADMIN = "user";

    @Autowired MockMvc mvc;
    @Autowired ReservaRepository reservas;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired BilleteraPuntosRepository billeteras;
    @Autowired MovimientoPuntosRepository movimientos;
    @Autowired IPuntosService puntosService;
    @Autowired ReservasVencidas reservasVencidas;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;

    private Visitante usuario;
    private Vehiculo vehiculo;
    private Cochera cochera;
    private LocalDateTime ahora;
    private LocalDateTime manana;

    @BeforeEach
    void setUp() {
        limpiarPuntos();
        usuario = visitantes.save(new Visitante("Ana Puntos", "pts-1", "ana@test.com", "hash", null,
                Set.of(AppAuthority.USER), true));
        vehiculo = new Vehiculo();
        vehiculo.setVisitante(usuario);
        vehiculo.setPatente("PT123AA");
        vehiculo.setTipo(VehiculoTipo.AUTO);
        vehiculo = vehiculos.save(vehiculo);
        cochera = new Cochera();
        cochera.setNumero("PTS-01");
        cochera.setSector("Planta Baja");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
        ahora = LocalDateTime.now(clock);
        manana = ahora.toLocalDate().plusDays(1).atStartOfDay();
    }

    @AfterEach
    void tearDown() {
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
        limpiarPuntos();
    }

    private void limpiarPuntos() {
        movimientos.deleteAllInBatch();
        billeteras.deleteAllInBatch();
    }

    private static RequestPostProcessor como(String email, String rol) {
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                email, "", List.of(new SimpleGrantedAuthority(rol)))));
    }

    private RequestPostProcessor ana() {
        return como(usuario.getEmail(), "USER");
    }

    /** Un saldo inicial con su movimiento, para que el libro cierre con la billetera. */
    private void conSaldo(int puntos) {
        BilleteraPuntos billetera = new BilleteraPuntos(usuario.getId());
        billetera.setSaldo(puntos);
        billeteras.save(billetera);
        movimientos.save(new MovimientoPuntos(usuario.getId(), TipoMovimientoPuntos.ACREDITACION_FRANJA, puntos,
                null, "Saldo de prueba", clock.instant().minusSeconds(86_400)));
    }

    private int saldo() {
        return billeteras.findByVisitanteId(usuario.getId()).map(BilleteraPuntos::getSaldo).orElse(0);
    }

    private void assertLibroCierra() {
        assertEquals(saldo(), movimientos.sumarPorVisitante(usuario.getId()),
                "El saldo de la billetera tiene que ser la suma de sus movimientos");
    }

    private String reservaJson(LocalDateTime desde, LocalDateTime hasta, String extra) {
        return "{\"vehiculoId\":\"" + vehiculo.getId() + "\",\"cocheraId\":\"" + cochera.getId()
                + "\",\"desde\":\"" + desde + "\",\"hasta\":\"" + hasta + "\"" + extra + "}";
    }

    private String cotizacion(LocalDateTime desde, LocalDateTime hasta) {
        return "/api/v1/tarifas/cotizacion?tipo=AUTO&desde=" + desde + "&hasta=" + hasta;
    }

    /** La franja que arranca en el bloque de 15 minutos en curso. */
    private LocalDateTime bloqueActual() {
        return ahora.withMinute(ahora.getMinute() / 15 * 15).withSecond(0).withNano(0);
    }

    // ---- Seguridad ----

    @Test
    @DisplayName("[Caja negra] un anonimo recibe 401 en la billetera")
    void anonimo() throws Exception {
        mvc.perform(get("/api/v1/puntos/billetera")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("[Caja negra] el resumen es solo del ADMIN")
    void resumenSoloAdmin() throws Exception {
        mvc.perform(get("/api/v1/puntos/resumen").with(ana())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/puntos/resumen").with(como("empleado@test.com", "EMPLEADO")))
                .andExpect(status().isForbidden());
        conSaldo(120);
        mvc.perform(get("/api/v1/puntos/resumen").with(como(ADMIN, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emitidos").value(120))
                .andExpect(jsonPath("$.canjeados").value(0))
                .andExpect(jsonPath("$.enCirculacion").value(120));
    }

    @Test
    @DisplayName("[Caja negra] ningun endpoint acredita puntos")
    void nadaAcreditaPorApi() throws Exception {
        mvc.perform(post("/api/v1/puntos/billetera").with(ana())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/puntos/movimientos").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"puntos\":1000}"))
                .andExpect(status().isForbidden());
        assertEquals(0, saldo());
    }

    // ---- Billetera ----

    @Test
    @DisplayName("[Caja negra] sin actividad, la billetera esta en cero con las franjas y no se crea")
    void billeteraVacia() throws Exception {
        mvc.perform(get("/api/v1/puntos/billetera").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(0))
                .andExpect(jsonPath("$.rachaActual").value(0))
                .andExpect(jsonPath("$.diasParaBonus").value(7))
                .andExpect(jsonPath("$.franjas", hasSize(4)))
                .andExpect(jsonPath("$.franjas[0].clave").value("NOCTURNA"))
                .andExpect(jsonPath("$.franjas[0].inicio").value("22:00"))
                .andExpect(jsonPath("$.franjas[0].puntos").value(20));
        mvc.perform(get("/api/v1/puntos/movimientos").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        assertFalse(billeteras.existsByVisitanteId(usuario.getId()));
    }

    // ---- Cotizacion ----

    @Test
    @DisplayName("[Caja negra] la cotizacion sin puntos mantiene el total y agrega lo que sumaria")
    void cotizacionSinPuntos() throws Exception {
        mvc.perform(get(cotizacion(manana.plusHours(22), manana.plusHours(23))).with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1000.00))
                .andExpect(jsonPath("$.subtotal").value(1000.00))
                .andExpect(jsonPath("$.descuento").value(0))
                .andExpect(jsonPath("$.horas").value(1))
                .andExpect(jsonPath("$.puntosAplicados").value(0))
                .andExpect(jsonPath("$.saldoDisponible").value(0))
                .andExpect(jsonPath("$.puntosAGanar").value(20));
    }

    @Test
    @DisplayName("[Caja negra] la cotizacion con puntos descuenta: 100 puntos son 10 %")
    void cotizacionConPuntos() throws Exception {
        conSaldo(300);
        mvc.perform(get(cotizacion(manana.plusHours(10), manana.plusHours(11)) + "&puntos=100").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(1000.00))
                .andExpect(jsonPath("$.descuentoPct").value(10.00))
                .andExpect(jsonPath("$.descuento").value(100.00))
                .andExpect(jsonPath("$.total").value(900.00))
                .andExpect(jsonPath("$.puntosAplicados").value(100))
                .andExpect(jsonPath("$.saldoDisponible").value(300))
                .andExpect(jsonPath("$.puntosAGanar").value(15));
        mvc.perform(get(cotizacion(manana.plusHours(10), manana.plusHours(11)) + "&usarTodos=true").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntosAplicados").value(300))
                .andExpect(jsonPath("$.total").value(700.00));
        // Cotizar no gasta nada.
        assertEquals(300, saldo());
    }

    @ParameterizedTest(name = "puntos={0} → 400")
    @ValueSource(strings = {"abc", "1.5", "0", "-10", "301", "600"})
    @DisplayName("[Caja negra] puntos invalidos, sin saldo o sobre el tope en la cotizacion → 400")
    void cotizacionInvalida(String puntos) throws Exception {
        conSaldo(300);
        mvc.perform(get(cotizacion(manana.plusHours(10), manana.plusHours(11)) + "&puntos=" + puntos).with(ana()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("[Caja negra] la cotizacion corta avisa que no suma")
    void cotizacionCorta() throws Exception {
        mvc.perform(get(cotizacion(manana.plusHours(22), manana.plusHours(22).plusMinutes(45))).with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntosAGanar").value(0))
                .andExpect(jsonPath("$.motivoSinPuntos").value(containsString("menos de 60 minutos")));
    }

    // ---- Canje, ingreso y reintegro ----

    @Test
    @DisplayName("[Caja negra] reservar con descuento, ingresar y sumar: el libro cierra con la billetera")
    void flujoCompleto() throws Exception {
        conSaldo(300);
        LocalDateTime desde = bloqueActual();
        LocalDateTime hasta = desde.plusHours(1);
        String alta = mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(desde, hasta, ",\"puntosAUsar\":100,\"precioEsperado\":900.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioTotal").value(1000.00))
                .andExpect(jsonPath("$.puntos.puntosUsados").value(100))
                .andExpect(jsonPath("$.puntos.descuentoPct").value(10.00))
                .andExpect(jsonPath("$.puntos.descuentoMonto").value(100.00))
                .andExpect(jsonPath("$.puntos.precioFinal").value(900.00))
                .andExpect(jsonPath("$.puntos.puntosAcreditados").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertEquals(200, saldo());
        String codigo = JsonPath.read(alta, "$.acceso.codigo");
        int franja = FranjaPuntos.de(desde.toLocalTime()).getPuntos();

        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\",\"accion\":\"INGRESO\"}"))
                .andExpect(status().isOk());
        // Doble escaneo: no vuelve a sumar.
        mvc.perform(post("/api/v1/accesos/registro").with(como(ADMIN, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\",\"accion\":\"INGRESO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yaRegistrado").value(true));

        assertEquals(200 + franja, saldo());
        assertLibroCierra();
        mvc.perform(get("/api/v1/puntos/billetera").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldo").value(200 + franja))
                .andExpect(jsonPath("$.rachaActual").value(1))
                .andExpect(jsonPath("$.mejorRacha").value(1))
                .andExpect(jsonPath("$.diasParaBonus").value(6));
        mvc.perform(get("/api/v1/puntos/movimientos").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("ACREDITACION_FRANJA"))
                .andExpect(jsonPath("$[0].puntos").value(franja))
                .andExpect(jsonPath("$[1].tipo").value("CANJE"))
                .andExpect(jsonPath("$[1].puntos").value(-100));
        UUID id = UUID.fromString(JsonPath.read(alta, "$.id"));
        mvc.perform(get("/api/v1/reservas/" + id).with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos.puntosAcreditados").value(franja))
                .andExpect(jsonPath("$.puntos.puntosAGanar").value(0));
    }

    @Test
    @DisplayName("[Caja negra] si el precio esperado no es el final con descuento, no reserva ni gasta")
    void precioEsperadoSinDescuento() throws Exception {
        conSaldo(300);
        mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(manana.plusHours(10), manana.plusHours(11),
                                ",\"puntosAUsar\":100,\"precioEsperado\":1000.00")))
                .andExpect(status().isBadRequest());
        assertEquals(300, saldo());
        assertEquals(0, reservas.count());
    }

    @Test
    @DisplayName("[Caja negra] reservar con mas puntos que el saldo → 400 y no se crea")
    void canjeSinSaldo() throws Exception {
        conSaldo(50);
        mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(manana.plusHours(10), manana.plusHours(11), ",\"puntosAUsar\":100")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No tenés puntos suficientes: tu saldo es de 50."));
        assertEquals(0, reservas.count());
        assertEquals(50, saldo());
    }

    @Test
    @DisplayName("[Caja negra] el ADMIN no canjea puntos de otra cuenta, y una reserva en espera no los usa")
    void canjeSoloPropio() throws Exception {
        conSaldo(300);
        mvc.perform(post("/api/v1/reservas").with(como(ADMIN, "ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(manana.plusHours(10), manana.plusHours(11),
                                ",\"visitanteId\":\"" + usuario.getId() + "\",\"puntosAUsar\":100")))
                .andExpect(status().isBadRequest());
        // Sin lugares libres la reserva entraria en espera: ahi no se canjea.
        cochera.setEstado(CocheraEstado.DESHABILITADA);
        cocheras.save(cochera);
        mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"vehiculoId\":\"" + vehiculo.getId() + "\",\"listaEspera\":true,"
                                + "\"tipoCochera\":\"AUTO\",\"desde\":\"" + manana.plusHours(10) + "\",\"hasta\":\""
                                + manana.plusHours(11) + "\",\"usarTodosLosPuntos\":true}"))
                .andExpect(status().isBadRequest());
        assertEquals(300, saldo());
    }

    @Test
    @DisplayName("[Caja negra] cancelar antes de usarla devuelve los puntos una sola vez")
    void cancelarReintegra() throws Exception {
        conSaldo(300);
        String alta = mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(manana.plusHours(10), manana.plusHours(11), ",\"usarTodosLosPuntos\":true")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puntos.puntosUsados").value(300))
                .andExpect(jsonPath("$.puntos.precioFinal").value(700.00))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, saldo());
        String id = JsonPath.read(alta, "$.id");

        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(ana()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntos.reintegrados").value(true))
                .andExpect(jsonPath("$.puntos.puntosAGanar").value(0));
        mvc.perform(post("/api/v1/reservas/" + id + "/cancelar").with(ana()))
                .andExpect(status().isBadRequest());

        assertEquals(300, saldo());
        assertLibroCierra();
        assertTrue(movimientos.existsByVisitanteIdAndReservaIdAndTipo(usuario.getId(), UUID.fromString(id),
                TipoMovimientoPuntos.REINTEGRO));
    }

    @Test
    @DisplayName("[Caja negra] una reserva con puntos que vence sin ingreso no los devuelve")
    void vencidaNoReintegra() throws Exception {
        conSaldo(300);
        String alta = mvc.perform(post("/api/v1/reservas").with(ana()).contentType(MediaType.APPLICATION_JSON)
                        .content(reservaJson(manana.plusHours(10), manana.plusHours(11), ",\"puntosAUsar\":100")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // Se corre la reserva al pasado, con la ventana de ingreso ya cerrada.
        Reserva reserva = reservas.findById(UUID.fromString(JsonPath.read(alta, "$.id"))).orElseThrow();
        reserva.setDesde(ahora.minusHours(3).withSecond(0).withNano(0));
        reserva.setHasta(reserva.getDesde().plusHours(1));
        reservas.save(reserva);

        reservasVencidas.marcarVencidas();

        assertEquals(ReservaEstado.VENCIDA, reservas.findById(reserva.getId()).orElseThrow().getEstado());
        mvc.perform(post("/api/v1/reservas/" + reserva.getId() + "/cancelar").with(ana()))
                .andExpect(status().isBadRequest());
        assertEquals(200, saldo());
        assertNull(reservas.findById(reserva.getId()).orElseThrow().getPuntosReintegradosAt());
        assertLibroCierra();
    }

    // ---- Concurrencia ----

    @Test
    @DisplayName("Dos canjes simultaneos sobre el mismo saldo: uno gana, el otro ve el saldo nuevo")
    void canjesSimultaneos() throws Exception {
        conSaldo(150);
        List<Object> resultados = enParalelo(() -> {
            Reserva falsa = new Reserva();
            falsa.setId(UUID.randomUUID());
            falsa.setDesde(manana.plusHours(10));
            var descuento = puntosService.prepararCanje(usuario, new BigDecimal("1000.00"),
                    new BigDecimal("100"), false);
            puntosService.confirmarCanje(usuario, falsa, descuento);
            return descuento;
        });

        long fallidos = resultados.stream().filter(r -> r instanceof RuntimeException).count();
        assertEquals(1, fallidos, "Uno de los dos canjes tiene que fallar: " + resultados);
        resultados.stream().filter(r -> r instanceof RuntimeException)
                .forEach(e -> assertTrue(e instanceof ValidationException || e instanceof ConflictoException,
                        "Fallo inesperado: " + e));
        assertEquals(50, saldo());
        assertLibroCierra();
    }

    @Test
    @DisplayName("Dos acreditaciones simultaneas de la misma reserva suman una sola vez")
    void acreditacionesSimultaneas() throws Exception {
        Reserva reserva = new Reserva();
        reserva.setDesde(manana.minusHours(2));
        reserva.setHasta(manana.minusHours(1));
        reserva.setVisitante(usuario);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(ReservaEstado.EN_CURSO);
        UUID id = reservas.save(reserva).getId();

        List<Object> resultados = enParalelo(() -> {
            puntosService.acreditarIngreso(reservas.findById(id).orElseThrow(), ahora);
            return "ok";
        });

        resultados.stream().filter(r -> r instanceof RuntimeException)
                .forEach(e -> assertInstanceOf(ConflictoException.class, e));
        assertEquals(FranjaPuntos.NOCTURNA.getPuntos(), saldo());
        assertEquals(1, movimientos.findByVisitanteIdOrderByFechaDescIdDesc(usuario.getId(),
                org.springframework.data.domain.Limit.of(10)).size());
        assertLibroCierra();
    }

    /** Corre la tarea en dos transacciones que arrancan a la vez; devuelve el resultado o la excepcion. */
    private List<Object> enParalelo(Supplier<Object> tarea) throws Exception {
        CountDownLatch listas = new CountDownLatch(2);
        CountDownLatch largar = new CountDownLatch(1);
        try (var ejecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futuros = List.of(1, 2).stream().map(i -> ejecutor.submit(() -> {
                listas.countDown();
                if (!largar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Tiempo agotado");
                try {
                    return new TransactionTemplate(transactionManager).execute(status -> tarea.get());
                } catch (RuntimeException e) {
                    return e;
                }
            })).toList();
            assertTrue(listas.await(10, TimeUnit.SECONDS));
            largar.countDown();
            return List.of(futuros.get(0).get(20, TimeUnit.SECONDS), futuros.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
