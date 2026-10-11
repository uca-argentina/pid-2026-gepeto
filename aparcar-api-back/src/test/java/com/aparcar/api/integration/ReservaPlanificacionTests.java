package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.component.ReservasVencidas;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contratos completos, persistencia y carreras reales contra H2, sin mocks de asignacion. */
@IntegrationTests
@Sql("/tarifas-test.sql")
class ReservaPlanificacionTests {
    @Autowired MockMvc mvc;
    @Autowired ReservaRepository reservas;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired ReservasVencidas vencidas;

    private Visitante admin;
    private Visitante primero;
    private Visitante segundo;
    private Vehiculo autoAdmin;
    private Vehiculo autoPrimero;
    private Vehiculo autoSegundo;
    private Cochera cochera;
    private LocalDateTime desde;

    @BeforeEach
    void preparar() {
        admin = visitante("admin", AppAuthority.ADMIN);
        primero = visitante("primero", AppAuthority.USER);
        segundo = visitante("segundo", AppAuthority.USER);
        autoAdmin = vehiculo(admin, "AD123MN");
        autoPrimero = vehiculo(primero, "AB123CD");
        autoSegundo = vehiculo(segundo, "EF456GH");
        cochera = new Cochera();
        cochera.setNumero("PLAN-01");
        cochera.setSector("PB");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);
        cochera = cocheras.save(cochera);
        desde = LocalDateTime.now(clock).plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM acceso_intentos");
        reservas.deleteAll();
        vehiculos.deleteAll();
        cocheras.deleteAll();
        visitantes.deleteAll();
    }

    private Visitante visitante(String nombre, AppAuthority rol) {
        return visitantes.save(new Visitante(nombre, "plan-" + nombre, nombre + "@plan.test", "hash", null,
                Set.of(rol), true));
    }

    private Vehiculo vehiculo(Visitante visitante, String patente) {
        Vehiculo auto = new Vehiculo();
        auto.setVisitante(visitante);
        auto.setTipo(VehiculoTipo.AUTO);
        auto.setPatente(patente);
        return vehiculos.save(auto);
    }

    private static RequestPostProcessor como(Visitante visitante) {
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                visitante.getEmail(), "", visitante.getAuthorities().stream()
                        .map(rol -> new SimpleGrantedAuthority(rol.name())).toList())));
    }

    private String solicitud(Vehiculo auto, boolean esperar) {
        return """
                {"visitanteId":"%s","vehiculoId":"%s","cocheraId":%s,
                 "desde":"%s","hasta":"%s","listaEspera":%s,"tipoCochera":"AUTO"}
                """.formatted(auto.getVisitante().getId(), auto.getId(),
                esperar ? "null" : "\"" + cochera.getId() + "\"", desde, desde.plusHours(1), esperar);
    }

    private String recurrente(LocalDateTime inicio, LocalDateTime fin, int semanas) {
        return """
                {"visitanteId":"%s","vehiculoId":"%s","cocheraId":"%s",
                 "desde":"%s","hasta":"%s","diasSemana":[%d],"repetirHasta":"%s"}
                """.formatted(admin.getId(), autoAdmin.getId(), cochera.getId(), inicio, fin,
                inicio.getDayOfWeek().getValue(), inicio.toLocalDate().plusWeeks(semanas));
    }

    private MvcResult crear(Visitante actor, Vehiculo auto, boolean esperar) throws Exception {
        return mvc.perform(post("/api/v1/reservas").with(como(actor)).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(auto, esperar)))
                .andExpect(status().isCreated()).andReturn();
    }

    private static UUID id(MvcResult resultado) throws Exception {
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private Reserva guardada(UUID id) {
        return reservas.findById(id).orElseThrow();
    }

    private Reserva ocupar(LocalDateTime inicio, LocalDateTime fin) {
        Reserva reserva = new Reserva();
        reserva.setVisitante(admin);
        reserva.setVehiculo(autoAdmin);
        reserva.setCochera(cochera);
        reserva.setDesde(inicio);
        reserva.setHasta(fin);
        reserva.setEstado(ReservaEstado.CONFIRMADA);
        return reservas.save(reserva);
    }

    private void cancelar(UUID id) throws Exception {
        mvc.perform(post("/api/v1/reservas/{id}/cancelar", id).with(como(admin)))
                .andExpect(status().isOk());
    }

    @Test
    void esperaExigeConsentimientoYNoOcupaNiEntregaCodigo() throws Exception {
        ocupar(desde, desde.plusHours(1));
        mvc.perform(post("/api/v1/reservas").with(como(primero)).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(autoPrimero, false)))
                .andExpect(status().isBadRequest());

        MvcResult espera = crear(primero, autoPrimero, true);
        assertEquals(ReservaEstado.LISTA_ESPERA, guardada(id(espera)).getEstado());
        mvc.perform(get("/api/v1/reservas/{id}", id(espera)).with(como(primero)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cochera").value(nullValue()))
                .andExpect(jsonPath("$.acceso.codigo").value(nullValue()))
                .andExpect(jsonPath("$.precioTotal").value(1000));
        assertEquals(2, reservas.count());
    }

    @Test
    void cancelacionPromueveFifoConPrecioAcordadoYCodigoNuevo() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID primera = id(crear(primero, autoPrimero, true));
        UUID segunda = id(crear(segundo, autoSegundo, true));
        jdbc.update("UPDATE reservas SET fecha_creacion = ? WHERE id = ?", "2026-01-01T00:00:00Z", primera);
        jdbc.update("UPDATE reservas SET fecha_creacion = ? WHERE id = ?", "2026-01-01T00:00:01Z", segunda);
        jdbc.update("UPDATE tarifas SET hora = 5000 WHERE tipo = 'AUTO'");

        cancelar(ocupante.getId());

        Reserva promovida = guardada(primera);
        assertEquals(ReservaEstado.CONFIRMADA, promovida.getEstado());
        assertEquals(cochera.getId(), promovida.getCochera().getId());
        assertEquals(0, new BigDecimal("1000").compareTo(promovida.getPrecioTotal()));
        assertNotNull(promovida.getCodigo());
        assertNotNull(promovida.getCodigoEmitidoAt());
        assertNotNull(promovida.getConfirmadaDesdeEsperaAt());
        assertEquals(ReservaEstado.LISTA_ESPERA, guardada(segunda).getEstado());

        cancelar(primera);
        assertEquals(ReservaEstado.CONFIRMADA, guardada(segunda).getEstado());
    }

    @Test
    void mismoVehiculoNoAcumulaEsperasSolapadas() throws Exception {
        ocupar(desde, desde.plusHours(1));
        crear(primero, autoPrimero, true);
        mvc.perform(post("/api/v1/reservas").with(como(primero)).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitud(autoPrimero, true)))
                .andExpect(status().isBadRequest());
        assertEquals(2, reservas.count());
    }

    @Test
    void esperaCanceladaNoSePromueveAlLiberarseLaCochera() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID espera = id(crear(primero, autoPrimero, true));
        mvc.perform(post("/api/v1/reservas/{id}/cancelar", espera).with(como(primero)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
        cancelar(ocupante.getId());
        assertEquals(ReservaEstado.CANCELADA, guardada(espera).getEstado());
        assertNull(guardada(espera).getCodigo());
    }

    @Test
    void notificacionEsPersistenteYExclusivaDelPropietarioAunqueLaReservaLaCargueAdmin() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID espera = id(crear(admin, autoPrimero, true));
        cancelar(ocupante.getId());

        mvc.perform(get("/api/v1/reservas/notificaciones").with(como(primero)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(espera.toString()));
        for (Visitante ajeno : List.of(segundo, admin)) {
            mvc.perform(get("/api/v1/reservas/notificaciones").with(como(ajeno)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
            mvc.perform(post("/api/v1/reservas/{id}/notificacion/leida", espera).with(como(ajeno)))
                    .andExpect(status().isForbidden());
        }
        assertNull(guardada(espera).getNotificacionLeidaAt());
        for (int intento = 0; intento < 2; intento++) {
            mvc.perform(post("/api/v1/reservas/{id}/notificacion/leida", espera).with(como(primero)))
                    .andExpect(status().is2xxSuccessful());
        }
        assertNotNull(guardada(espera).getNotificacionLeidaAt());
        mvc.perform(get("/api/v1/reservas/notificaciones").with(como(primero)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void conflictoEnOcurrenciaPosteriorRevierteLaSerieCompleta() throws Exception {
        Reserva existente = ocupar(desde.plusWeeks(1), desde.plusWeeks(1).plusHours(1));
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(recurrente(desde, desde.plusHours(1), 2)))
                .andExpect(status().isBadRequest());
        assertEquals(List.of(existente.getId()), reservas.findAll().stream().map(Reserva::getId).toList());
    }

    @Test
    void recurrenteConservaDuracionNocturnaYGeneraCodigoPorOcurrencia() throws Exception {
        LocalDateTime inicio = desde.withHour(23);
        LocalDateTime fin = inicio.plusHours(2);
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(recurrente(inicio, fin, 2)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(3)));
        List<Reserva> serie = reservas.findAll();
        assertEquals(3, serie.size());
        assertEquals(1, serie.stream().map(Reserva::getRecurrenciaId).distinct().count());
        assertEquals(3, serie.stream().map(Reserva::getCodigo).distinct().count());
        for (Reserva reserva : serie) {
            assertNotNull(reserva.getRecurrenciaId());
            assertNotNull(reserva.getCodigo());
            assertEquals(Duration.ofHours(2), Duration.between(reserva.getDesde(), reserva.getHasta()));
            assertEquals(inicio.getDayOfWeek(), reserva.getDesde().getDayOfWeek());
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
        }
    }

    @Test
    void visitanteNoPuedeCrearRecurrentesFalsificandoTitular() throws Exception {
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(primero))
                        .contentType(MediaType.APPLICATION_JSON).content(recurrente(desde, desde.plusHours(1), 1)))
                .andExpect(status().isForbidden());
        assertEquals(0, reservas.count());
    }

    @Test
    void recurrentesResuelvenAdministradorAutenticadoSinTitularDelCliente() throws Exception {
        String solicitud = recurrente(desde, desde.plusHours(1), 1)
                .replace("\"visitanteId\":\"" + admin.getId() + "\",", "");
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                .andExpect(status().isCreated()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].visitante.id").value(admin.getId().toString()));
        assertTrue(reservas.findAll().stream().allMatch(r -> r.getVisitante().getId().equals(admin.getId())));
    }

    @Test
    void administradorNoPuedeCrearSeriesParaVisitantesNiOtroAdministrador() throws Exception {
        Visitante otroAdmin = visitante("otro-admin", AppAuthority.ADMIN);
        Vehiculo autoOtroAdmin = vehiculo(otroAdmin, "ZZ123AA");
        String solicitud = recurrente(desde, desde.plusHours(1), 1);
        for (Vehiculo ajeno : List.of(autoPrimero, autoOtroAdmin)) {
            for (String titular : List.of("\"" + ajeno.getVisitante().getId() + "\"", "null")) {
                mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                                .contentType(MediaType.APPLICATION_JSON).content(solicitud
                                        .replace("\"" + admin.getId() + "\"", titular)
                                        .replace(autoAdmin.getId().toString(), ajeno.getId().toString())))
                        .andExpect(status().isForbidden());
            }
        }
        // El titular ajeno tambien se rechaza si el vehiculo es propio.
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitud
                                .replace(admin.getId().toString(), primero.getId().toString())))
                .andExpect(status().isForbidden());
        assertEquals(0, reservas.count());
    }

    @Test
    void rolPersistidoDebeSeguirSiendoAdminAunqueLaSesionConserveElRolAnterior() throws Exception {
        RequestPostProcessor sesionAnterior = como(admin);
        admin.setAuthorities(Set.of(AppAuthority.USER));
        visitantes.save(admin);
        mvc.perform(post("/api/v1/reservas/recurrentes").with(sesionAnterior)
                        .contentType(MediaType.APPLICATION_JSON).content(recurrente(desde, desde.plusHours(1), 1)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reservas/recurrentes").with(sesionAnterior))
                .andExpect(status().isForbidden());
        assertEquals(0, reservas.count());
    }

    @Test
    void listadoRecurrenteEsPrivadoYExcluyeReservasComunes() throws Exception {
        Visitante otroAdmin = visitante("otro-admin", AppAuthority.ADMIN);
        Vehiculo autoOtroAdmin = vehiculo(otroAdmin, "ZZ123AA");
        String propia = recurrente(desde, desde.plusHours(1), 1);
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(propia))
                .andExpect(status().isCreated());
        String ajena = recurrente(desde.plusHours(2), desde.plusHours(3), 1)
                .replace(admin.getId().toString(), otroAdmin.getId().toString())
                .replace(autoAdmin.getId().toString(), autoOtroAdmin.getId().toString());
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(otroAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(ajena))
                .andExpect(status().isCreated());
        ocupar(desde.plusDays(1), desde.plusDays(1).plusHours(1));

        mvc.perform(get("/api/v1/reservas/recurrentes").with(como(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].visitante.id").value(admin.getId().toString()))
                .andExpect(jsonPath("$[1].visitante.id").value(admin.getId().toString()));
        mvc.perform(get("/api/v1/reservas/recurrentes").with(como(primero)))
                .andExpect(status().isForbidden());
    }

    @Test
    void disponibilidadPropiaRespetaLaDiscapacidadDelAdministrador() throws Exception {
        cochera.setTipo(CocheraTipo.ACCESIBLE);
        cocheras.save(cochera);
        mvc.perform(get("/api/v1/cocheras/disponibles").with(como(admin))
                        .param("desde", desde.toString()).param("hasta", desde.plusHours(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/v1/cocheras/disponibles").with(como(admin)).param("propia", "true")
                        .param("desde", desde.toString()).param("hasta", desde.plusHours(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        admin.setTieneDiscapacidad(true);
        visitantes.save(admin);
        mvc.perform(get("/api/v1/cocheras/disponibles").with(como(admin)).param("propia", "true")
                        .param("desde", desde.toString()).param("hasta", desde.plusHours(1).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    private String altaVisitante(String patente, String tipo) {
        return """
                {"nombre":"Nuevo visitante","documento":"43210987","email":"nuevo@plan.test",
                 "patente":"%s","tipoVehiculo":"%s","cocheraId":null,
                 "listaEspera":true,"tipoCochera":"%s","desde":"%s","hasta":"%s"}
                """.formatted(patente, tipo, tipo, desde, desde.plusHours(1));
    }

    @ParameterizedTest
    @CsvSource({"xyz123,AUTO", "xy123zz,AUTO", "123xyz,MOTO", "x123yzz,MOTO"})
    void altaNuevoVisitanteAceptaEsperaSinCocheraYPatenteSegunTipo(String patente, String tipo) throws Exception {
        cochera.setTipo(CocheraTipo.valueOf(tipo));
        cocheras.save(cochera);
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        MvcResult resultado = mvc.perform(post("/api/v1/visitantes/alta").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(altaVisitante(patente, tipo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vehiculo.patente").value(patente.toUpperCase()))
                .andExpect(jsonPath("$.reserva.estado").value("LISTA_ESPERA"))
                .andExpect(jsonPath("$.reserva.cochera").value(nullValue()))
                .andExpect(jsonPath("$.reserva.acceso.codigo").value(nullValue())).andReturn();
        UUID reservaId = UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.reserva.id"));
        assertTrue(visitantes.existsByEmail("nuevo@plan.test"));
        cancelar(ocupante.getId());
        assertEquals(ReservaEstado.CONFIRMADA, guardada(reservaId).getEstado());
        assertNotNull(guardada(reservaId).getConfirmadaDesdeEsperaAt());
    }

    @ParameterizedTest
    @CsvSource({"XYZ123,MOTO", "123XYZ,AUTO"})
    void altaEsperaRechazaPatenteDeOtroTipoConMensajeUtilYRevierteCuenta(String patente, String tipo) throws Exception {
        mvc.perform(post("/api/v1/visitantes/alta").with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(altaVisitante(patente, tipo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("formato valido para " + tipo)));
        assertEquals(3, visitantes.count());
        assertEquals(3, vehiculos.count());
        assertEquals(0, reservas.count());
    }

    @Test
    void dosSolicitudesConcurrentesPorUltimoLugarConfirmanUnaYDejanLaOtraEnEspera() throws Exception {
        List<Integer> estados = concurrentes(
                () -> crear(primero, autoPrimero, true).getResponse().getStatus(),
                () -> crear(segundo, autoSegundo, true).getResponse().getStatus());
        assertEquals(List.of(201, 201), estados);
        List<Reserva> resultado = reservas.findAll();
        assertEquals(1, resultado.stream().filter(r -> r.getEstado() == ReservaEstado.CONFIRMADA).count());
        assertEquals(1, resultado.stream().filter(r -> r.getEstado() == ReservaEstado.LISTA_ESPERA).count());
        assertEquals(1, resultado.stream().filter(r -> r.getCochera() != null).count());
        assertEquals(1, resultado.stream().filter(r -> r.getCodigo() != null).count());
    }

    @Test
    void cancelacionConcurrenteConAltaNuevaRespetaLaEsperaAnterior() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID espera = id(crear(primero, autoPrimero, true));
        List<Integer> estados = concurrentes(
                () -> mvc.perform(post("/api/v1/reservas/{id}/cancelar", ocupante.getId()).with(como(admin)))
                        .andReturn().getResponse().getStatus(),
                () -> mvc.perform(post("/api/v1/reservas").with(como(segundo)).contentType(MediaType.APPLICATION_JSON)
                                .content(solicitud(autoSegundo, false)))
                        .andReturn().getResponse().getStatus());
        assertEquals(List.of(200, 400), estados);
        assertEquals(ReservaEstado.CONFIRMADA, guardada(espera).getEstado());
        assertEquals(2, reservas.count());
    }

    @Test
    void egresoAnticipadoConfirmaEsperaSinEsperarAlFinalDeLaFranja() throws Exception {
        LocalDateTime ahora = LocalDateTime.now(clock);
        desde = ahora.withMinute(ahora.getMinute() / 15 * 15).withSecond(0).withNano(0);
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        ocupante.setEstado(ReservaEstado.EN_CURSO);
        ocupante.setIngresoAt(LocalDateTime.now(clock).minusMinutes(5));
        ocupante.setCodigo("ABCDE12345");
        reservas.save(ocupante);
        UUID espera = id(crear(primero, autoPrimero, true));

        mvc.perform(post("/api/v1/accesos/registro").with(como(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"ABCDE12345\",\"accion\":\"EGRESO\"}"))
                .andExpect(status().isOk());

        assertEquals(ReservaEstado.FINALIZADA, guardada(ocupante.getId()).getEstado());
        Reserva promovida = guardada(espera);
        assertEquals(ReservaEstado.CONFIRMADA, promovida.getEstado());
        assertEquals(desde, promovida.getDesde());
        assertEquals(desde.plusHours(1), promovida.getHasta());
        assertNotNull(promovida.getCodigo());
    }

    @Test
    void barridoDeNoPresentadosLiberaLugarYDaToleranciaDesdeLaPromocion() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID espera = id(crear(primero, autoPrimero, true));
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime inicio = ahora.minusHours(2).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fin = ahora.plusHours(2).withMinute(0).withSecond(0).withNano(0);
        // Usamos el mismo mapeo temporal que la aplicacion: mezclar LocalDateTime
        // por JDBC directo con Hibernate desplaza la franja en H2 al arrancar en UTC.
        Reserva pendiente = guardada(espera);
        for (Reserva reserva : List.of(ocupante, pendiente)) {
            reserva.setDesde(inicio);
            reserva.setHasta(fin);
        }
        reservas.saveAll(List.of(ocupante, pendiente));

        assertEquals(inicio, guardada(espera).getDesde());
        assertEquals(fin, guardada(espera).getHasta());

        vencidas.marcarVencidas();

        assertEquals(ReservaEstado.VENCIDA, guardada(ocupante.getId()).getEstado());
        Reserva promovida = guardada(espera);
        assertEquals(ReservaEstado.CONFIRMADA, promovida.getEstado());
        assertNotNull(promovida.getCodigoEmitidoAt());
        // Otro barrido debe respetar la tolerancia nueva aunque desde sea antiguo.
        vencidas.marcarVencidas();
        assertEquals(ReservaEstado.CONFIRMADA, guardada(espera).getEstado());
        mvc.perform(post("/api/v1/accesos/consulta").with(como(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + promovida.getCodigo() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valido").value(true));
    }

    @Test
    void habilitarCocheraPromueveLaEsperaPendiente() throws Exception {
        cochera.setEstado(CocheraEstado.DESHABILITADA);
        cocheras.save(cochera);
        UUID espera = id(crear(primero, autoPrimero, true));
        mvc.perform(put("/api/v1/cocheras/{id}", cochera.getId()).with(como(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"numero":"PLAN-01","sector":"PB","tipo":"AUTO","estado":"HABILITADA"}
                                """))
                .andExpect(status().isOk());
        assertEquals(ReservaEstado.CONFIRMADA, guardada(espera).getEstado());
        assertEquals(cochera.getId(), guardada(espera).getCochera().getId());
    }

    @Test
    void esperaCuyoHorarioTerminoVenceSinAsignacionNiNotificacion() throws Exception {
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        UUID espera = id(crear(primero, autoPrimero, true));
        LocalDateTime ahora = LocalDateTime.now(clock);
        jdbc.update("UPDATE reservas SET desde = ?, hasta = ? WHERE id = ?",
                ahora.minusHours(2), ahora.minusHours(1), espera);
        cancelar(ocupante.getId());
        Reserva vencida = guardada(espera);
        assertEquals(ReservaEstado.VENCIDA, vencida.getEstado());
        assertNull(vencida.getCochera());
        assertNull(vencida.getCodigo());
        assertNull(vencida.getConfirmadaDesdeEsperaAt());
    }

    @Test
    void esperaAccesibleExigeDiscapacidadYLaRevalidaAntesDePromover() throws Exception {
        cochera.setTipo(CocheraTipo.ACCESIBLE);
        cocheras.save(cochera);
        Reserva ocupante = ocupar(desde, desde.plusHours(1));
        String solicitudAccesible = solicitud(autoPrimero, true).replace("\"AUTO\"", "\"ACCESIBLE\"");
        mvc.perform(post("/api/v1/reservas").with(como(primero)).contentType(MediaType.APPLICATION_JSON)
                        .content(solicitudAccesible))
                .andExpect(status().isBadRequest());
        primero.setTieneDiscapacidad(true);
        visitantes.save(primero);
        UUID espera = id(mvc.perform(post("/api/v1/reservas").with(como(primero))
                        .contentType(MediaType.APPLICATION_JSON).content(solicitudAccesible))
                .andExpect(status().isCreated()).andReturn());
        primero.setTieneDiscapacidad(false);
        visitantes.save(primero);

        cancelar(ocupante.getId());

        assertEquals(ReservaEstado.LISTA_ESPERA, guardada(espera).getEstado());
        assertNull(guardada(espera).getCochera());
        assertNull(guardada(espera).getConfirmadaDesdeEsperaAt());
    }

    @Test
    void esperaConservaCategoriaAcordadaAunqueExistaOtraCompatibleDisponible() throws Exception {
        primero.setTieneDiscapacidad(true);
        visitantes.save(primero);
        Cochera accesible = new Cochera();
        accesible.setNumero("PLAN-ACCESIBLE");
        accesible.setSector("PB");
        accesible.setTipo(CocheraTipo.ACCESIBLE);
        accesible.setEstado(CocheraEstado.HABILITADA);
        cocheras.save(accesible);
        Reserva ocupante = ocupar(desde, desde.plusHours(1));

        UUID espera = id(crear(primero, autoPrimero, true));

        assertEquals(ReservaEstado.LISTA_ESPERA, guardada(espera).getEstado());
        assertEquals(CocheraTipo.AUTO, guardada(espera).getTipoTarifa());
        assertEquals(0, new BigDecimal("1000").compareTo(guardada(espera).getPrecioTotal()));
        cancelar(ocupante.getId());
        assertEquals(cochera.getId(), guardada(espera).getCochera().getId());
        assertEquals(CocheraTipo.AUTO, guardada(espera).getTipoTarifa());
    }

    @Test
    void recurrentesRechazanDiasInvalidosLimiteDeOcurrenciasYMasDeUnAnio() throws Exception {
        String base = recurrente(desde, desde.plusHours(1), 13);
        String dias = "\"diasSemana\":[" + desde.getDayOfWeek().getValue() + "]";
        List<String> invalidas = List.of(
                base.replace(dias, "\"diasSemana\":[1,2,3,4,5,6,7]"),
                base.replace(dias, "\"diasSemana\":[]"),
                base.replace(dias, "\"diasSemana\":[0,8]"),
                base.replace(dias, "\"diasSemana\":[" + (desde.getDayOfWeek().getValue() % 7 + 1) + "]"),
                recurrente(desde, desde.plusHours(1), 53));
        for (String solicitud : invalidas) {
            mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(solicitud))
                    .andExpect(status().isBadRequest());
            assertEquals(0, reservas.count(), "Una serie rechazada no deja ocurrencias parciales");
        }
    }

    private static <T> List<T> concurrentes(Callable<T> primera, Callable<T> segunda) throws Exception {
        CountDownLatch listas = new CountDownLatch(2);
        CountDownLatch comenzar = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tareas = List.of(primera, segunda).stream().map(tarea -> executor.submit(() -> {
                listas.countDown();
                assertTrue(comenzar.await(10, TimeUnit.SECONDS), "No se libero el inicio concurrente");
                return tarea.call();
            })).toList();
            assertTrue(listas.await(10, TimeUnit.SECONDS), "No iniciaron ambos participantes");
            comenzar.countDown();
            return List.of(tareas.get(0).get(20, TimeUnit.SECONDS), tareas.get(1).get(20, TimeUnit.SECONDS));
        }
    }
}
