package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.convenio.Convenio;
import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.entity.reserva.*;
import com.aparcar.api.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTests
@Transactional
@Sql("/tarifas-test.sql")
class ConvenioControllerTests {
    @Autowired MockMvc mvc;
    @Autowired ConvenioRepository convenios;
    @Autowired CredencialConvenioRepository credenciales;
    @Autowired VisitanteRepository visitantes;
    @Autowired VehiculoRepository vehiculos;
    @Autowired CocheraRepository cocheras;
    @Autowired ReservaRepository reservas;
    @Autowired BilleteraPuntosRepository billeteras;
    @Autowired MovimientoPuntosRepository movimientos;
    @Autowired EntityManager em;
    @Autowired Clock clock;

    private Visitante user;
    private Visitante admin;
    private Visitante empleado;
    private Vehiculo auto;
    private Cochera cochera;
    private Convenio uca;
    private Convenio nacion;
    private LocalDateTime desde;

    @BeforeEach
    void preparar() {
        user = persona("Cliente", "30.001.007", AppAuthority.USER);
        admin = persona("Admin", "40000222", AppAuthority.ADMIN);
        empleado = persona("Empleado", "40000333", AppAuthority.EMPLEADO);
        auto = vehiculo(user, "CV123AA");
        cochera = new Cochera();
        cochera.setNumero("CONV-1"); cochera.setSector("Test");
        cochera.setTipo(CocheraTipo.AUTO); cochera.setEstado(CocheraEstado.HABILITADA);
        cocheras.save(cochera);
        uca = convenio("UCA", "UCA-XXX", "15");
        nacion = convenio("Club Nación", "NACION-XXX", "10");
        desde = LocalDateTime.now(clock).toLocalDate().plusDays(3).atTime(10, 0);
    }

    private Visitante persona(String nombre, String documento, AppAuthority rol) {
        return visitantes.save(new Visitante(nombre, documento, nombre.toLowerCase() + "@convenio.test", "hash",
                null, Set.of(rol), true));
    }

    private Vehiculo vehiculo(Visitante titular, String patente) {
        Vehiculo v = new Vehiculo(); v.setVisitante(titular); v.setPatente(patente); v.setTipo(VehiculoTipo.AUTO);
        return vehiculos.save(v);
    }

    private Convenio convenio(String nombre, String formato, String porcentaje) {
        Convenio c = new Convenio(); c.setNombre(nombre); c.setFormatoCredencial(formato);
        c.setDescuentoPct(new BigDecimal(porcentaje).setScale(2));
        return convenios.save(c);
    }

    private RequestPostProcessor como(Visitante persona) {
        String rol = persona.getAuthorities().iterator().next().name();
        return securityContext(new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(
                persona.getEmail(), "", List.of(new SimpleGrantedAuthority(rol)))));
    }

    private String credencial(Convenio convenio, String valor) {
        return "{\"convenioId\":\"" + convenio.getId() + "\",\"credencial\":\"" + valor + "\"}";
    }

    private void guardar(Convenio convenio, String valor) throws Exception {
        mvc.perform(put("/api/v1/convenios/credenciales").with(como(user)).contentType(MediaType.APPLICATION_JSON)
                .content(credencial(convenio, valor))).andExpect(status().isOk());
    }

    private ResultActions cotizar(Visitante actor, String extra) throws Exception {
        return mvc.perform(post("/api/v1/tarifas/cotizacion").with(como(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"AUTO\",\"desde\":\"" + desde + "\",\"hasta\":\"" + desde.plusHours(1) + "\"" + extra + "}"));
    }

    private String reserva(String extra) {
        return """
                {"visitanteId":"%s","vehiculoId":"%s","cocheraId":"%s","desde":"%s","hasta":"%s"%s}
                """.formatted(user.getId(), auto.getId(), cochera.getId(), desde, desde.plusHours(1), extra);
    }

    private ResultActions reservar(Visitante actor, String extra) throws Exception {
        return mvc.perform(post("/api/v1/reservas").with(como(actor)).contentType(MediaType.APPLICATION_JSON).content(reserva(extra)));
    }

    @Test
    void permisosCatalogoAdministracionYCredencialesAjenas() throws Exception {
        mvc.perform(get("/api/v1/convenios")).andExpect(status().isUnauthorized());
        String body = "{\"nombre\":\"Nuevo\",\"formatoCredencial\":\"NUEVO-XXX\",\"descuentoPct\":12.5}";
        for (Visitante actor : List.of(user, empleado)) {
            mvc.perform(get("/api/v1/convenios").with(como(actor))).andExpect(status().isOk());
            mvc.perform(post("/api/v1/convenios").with(como(actor)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(put("/api/v1/convenios/" + uca.getId()).with(como(actor)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/convenios/" + uca.getId()).with(como(actor))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/convenios").with(como(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.descuentoPct").value(12.5));
        mvc.perform(get("/api/v1/convenios/credenciales").with(como(user)).param("visitanteId", admin.getId().toString()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/convenios/credenciales/" + uca.getId()).with(como(user)).param("visitanteId", admin.getId().toString()))
                .andExpect(status().isForbidden());
        cotizar(user, ",\"visitanteId\":\"" + admin.getId() + "\"").andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"UCA-008", "UCA-07", "UCA-0007", "NACION-007", "UCA-XXX", "UCA-007-extra"})
    void verificaDocumentoCompletoYFormatoEnServidor(String valor) throws Exception {
        mvc.perform(post("/api/v1/convenios/verificar").with(como(user)).contentType(MediaType.APPLICATION_JSON)
                .content(credencial(uca, valor))).andExpect(status().isBadRequest());
        reservar(user, ",\"credencialesConvenio\":[" + credencial(uca, valor) + "]").andExpect(status().isBadRequest());
        assertEquals(0, reservas.count());
    }

    @Test
    void normalizaYGuardaUnaSolaCredencialSinPersistirVerificacion() throws Exception {
        mvc.perform(post("/api/v1/convenios/verificar").with(como(user)).contentType(MediaType.APPLICATION_JSON)
                .content(credencial(uca, " uca-007 "))).andExpect(status().isOk()).andExpect(jsonPath("$.credencial").value("UCA-007"));
        assertEquals(0, credenciales.count());
        guardar(uca, " uca-007 "); guardar(uca, "UCA-007");
        assertEquals(1, credenciales.count());
        mvc.perform(get("/api/v1/convenios/credenciales").with(como(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].credencial").value("UCA-007"));
        mvc.perform(get("/api/v1/convenios/credenciales").with(como(admin))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/v1/convenios/credenciales/" + uca.getId()).with(como(user))).andExpect(status().isNoContent());
        cotizar(user, "").andExpect(jsonPath("$.total").value(1000)).andExpect(jsonPath("$.convenio").isEmpty());
    }

    @Test
    void documentoLibreSoloPersonalYTomaDocumentoDelTitularSeleccionado() throws Exception {
        String body = credencial(uca, "UCA-007").replace("}", ",\"visitanteId\":\"" + user.getId() + "\"}");
        mvc.perform(post("/api/v1/convenios/verificar").with(como(empleado)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        String nuevo = credencial(uca, "UCA-123").replace("}", ",\"documento\":\"44.333.123\"}");
        mvc.perform(post("/api/v1/convenios/verificar").with(como(empleado)).contentType(MediaType.APPLICATION_JSON).content(nuevo))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/convenios/verificar").with(como(user)).contentType(MediaType.APPLICATION_JSON).content(nuevo))
                .andExpect(status().isForbidden());
        cotizar(empleado, ",\"documento\":\"44333123\",\"credencialesConvenio\":[" + credencial(uca, "UCA-123") + "]")
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(850));
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN", "EMPLEADO"})
    void todasLasCuentasUsanMejorConvenioGuardadoOSuministrado(String rol) throws Exception {
        guardar(nacion, "NACION-007");
        Visitante actor = switch (rol) { case "ADMIN" -> admin; case "EMPLEADO" -> empleado; default -> user; };
        String extra = ",\"credencialesConvenio\":[" + credencial(uca, "UCA-007") + "]";
        cotizar(actor, extra + (actor == user ? "" : ",\"visitanteId\":\"" + user.getId() + "\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(1000))
                .andExpect(jsonPath("$.total").value(850)).andExpect(jsonPath("$.convenio.nombre").value("UCA"));
        reservar(actor, extra + ",\"precioEsperado\":850").andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioTotal").value(1000)).andExpect(jsonPath("$.precioFinal").value(850))
                .andExpect(jsonPath("$.convenio.descuentoMonto").value(150));
    }

    @Test
    void reservaConservaCopiaTrasEditarYBorrarYRechazaPrecioDesactualizado() throws Exception {
        guardar(uca, "UCA-007");
        reservar(user, ",\"precioEsperado\":850").andExpect(status().isCreated());
        UUID id = reservas.findAll().getFirst().getId();
        mvc.perform(put("/api/v1/convenios/" + uca.getId()).with(como(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Universidad\",\"formatoCredencial\":\"UCA-XXX\",\"descuentoPct\":25}"))
                .andExpect(status().isOk());
        desde = desde.plusHours(2);
        reservar(user, ",\"precioEsperado\":850").andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/convenios/" + uca.getId()).with(como(admin))).andExpect(status().isNoContent());
        em.flush(); em.clear();
        mvc.perform(get("/api/v1/reservas/" + id).with(como(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.convenio.nombre").value("UCA")).andExpect(jsonPath("$.convenio.descuentoPct").value(15))
                .andExpect(jsonPath("$.precioFinal").value(850));
        assertEquals(0, credenciales.count());
    }

    @Test
    void revalidaDocumentoYModificarFormatoEliminaHabilitacionAnterior() throws Exception {
        guardar(uca, "UCA-007");
        user.setDocumento("30111008"); visitantes.saveAndFlush(user);
        cotizar(user, "").andExpect(jsonPath("$.total").value(1000));
        mvc.perform(get("/api/v1/convenios/credenciales").with(como(user))).andExpect(jsonPath("$.length()").value(0));
        user.setDocumento("30111007"); visitantes.saveAndFlush(user);
        mvc.perform(put("/api/v1/convenios/" + uca.getId()).with(como(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"UCA\",\"formatoCredencial\":\"ALUMNO-XXX\",\"descuentoPct\":15}"))
                .andExpect(status().isOk());
        assertEquals(0, credenciales.count());
        cotizar(user, "").andExpect(jsonPath("$.total").value(1000));
    }

    @ParameterizedTest
    @CsvSource({"100,850,0,500", "150,850,0,500", "200,800,200,300"})
    void convenioYPuntosNoSeAcumulanYEmpateConservaPuntos(int pedidos, int total, int usados, int saldo) throws Exception {
        guardar(uca, "UCA-007");
        BilleteraPuntos billetera = new BilleteraPuntos(user.getId()); billetera.setSaldo(500); billeteras.saveAndFlush(billetera);
        cotizar(user, ",\"puntos\":" + pedidos).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(total))
                .andExpect(jsonPath("$.puntosAplicados").value(usados));
        reservar(user, ",\"puntosAUsar\":" + pedidos + ",\"precioEsperado\":" + total).andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioFinal").value(total)).andExpect(jsonPath("$.puntos.puntosUsados").value(usados));
        assertEquals(saldo, billeteras.findByVisitanteId(user.getId()).orElseThrow().getSaldo());
        Reserva guardada = reservas.findAll().getFirst();
        assertEquals(usados == 0 ? uca.getId() : null, guardada.getConvenioId());
        assertEquals(usados == 0 ? 0 : 1, movimientos.count());
    }

    @Test
    void esperaYAltaDeVisitanteConservanConvenio() throws Exception {
        guardar(uca, "UCA-007");
        cochera.setEstado(CocheraEstado.DESHABILITADA); cocheras.save(cochera);
        String espera = reserva(",\"listaEspera\":true,\"tipoCochera\":\"AUTO\",\"precioEsperado\":850")
                .replace("\"cocheraId\":\"" + cochera.getId() + "\",", "");
        mvc.perform(post("/api/v1/reservas").with(como(user)).contentType(MediaType.APPLICATION_JSON).content(espera))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("LISTA_ESPERA"))
                .andExpect(jsonPath("$.precioFinal").value(850));
        String alta = """
                {"nombre":"Nueva","documento":"44111123","email":"nueva@convenio.test","patente":"CV987AA",
                 "tipoVehiculo":"AUTO","listaEspera":true,"tipoCochera":"AUTO","desde":"%s","hasta":"%s",
                 "credencialesConvenio":[%s],"precioEsperado":850}
                """.formatted(desde, desde.plusHours(1), credencial(uca, "UCA-123"));
        mvc.perform(post("/api/v1/visitantes/alta").with(como(empleado)).contentType(MediaType.APPLICATION_JSON).content(alta))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reserva.convenio.nombre").value("UCA"))
                .andExpect(jsonPath("$.reserva.precioFinal").value(850));
    }

    @Test
    void serieRecurrenteCopiaConvenioEnCadaOcurrencia() throws Exception {
        Vehiculo propio = vehiculo(admin, "CV456AA");
        String body = """
                {"vehiculoId":"%s","cocheraId":"%s","desde":"%s","hasta":"%s","diasSemana":[%d],
                 "repetirHasta":"%s","credencialesConvenio":[%s],"precioEsperado":850}
                """.formatted(propio.getId(), cochera.getId(), desde, desde.plusHours(1), desde.getDayOfWeek().getValue(),
                desde.toLocalDate().plusWeeks(1), credencial(uca, "UCA-222"));
        mvc.perform(post("/api/v1/reservas/recurrentes").with(como(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].convenio.nombre").value("UCA")).andExpect(jsonPath("$[1].precioFinal").value(850));
    }

    @ParameterizedTest
    @ValueSource(strings = {"XXX", "XXX-XXX", "UCA-(.*)XXX", "UCA-XXX-X", "UCA"})
    void formatoPersonalizableEsLiteralYRechazaPatronesAmbiguos(String formato) throws Exception {
        mvc.perform(post("/api/v1/convenios").with(como(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Inválido\",\"formatoCredencial\":\"" + formato + "\",\"descuentoPct\":15}"))
                .andExpect(status().isBadRequest());
    }
}
