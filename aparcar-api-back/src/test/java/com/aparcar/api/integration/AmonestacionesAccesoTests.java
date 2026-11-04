package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra: quién puede qué dentro del régimen de faltas.
 *
 * <p>Hay tres niveles y es fácil que se mezclen, porque las cuatro rutas
 * cuelgan del mismo prefijo:
 *
 * <ul>
 *   <li>Lo propio ({@code /mias}) lo ve cualquiera que haya iniciado sesión.
 *   <li>Aplicar una falta y mirar las de otro es del personal.
 *   <li>Levantar un bloqueo y cambiar los números es solo del administrador.
 * </ul>
 *
 * <p>El orden de los matchers importa: {@code /mias} y {@code /configuracion}
 * tienen que resolverse antes que la regla general del prefijo. Si alguien los
 * reordena, un visitante perdería el acceso a sus propias faltas o un empleado
 * ganaría el de cambiar el límite. Ninguna de las dos cosas falla sola.
 */
@IntegrationTests
class AmonestacionesAccesoTests {

    @Autowired
    private MockMvc mockMvc;

    private static final String UN_ID = UUID.randomUUID().toString();

    @Test
    @WithAnonymousUser
    @DisplayName("Sin sesión no se ve nada")
    void anonimoNoVeNada() throws Exception {
        mockMvc.perform(get("/api/v1/amonestaciones/mias")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/amonestaciones/configuracion")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "visitante@test.com", authorities = "USER")
    @DisplayName("Un visitante llega a sus propias faltas")
    void elVisitanteVeLoSuyo() throws Exception {
        // 404 porque la cuenta de prueba no existe en la base; lo que importa
        // es que no sea 403: la ruta le está permitida.
        mockMvc.perform(get("/api/v1/amonestaciones/mias").with(securityContext(getContext())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "visitante@test.com", authorities = "USER")
    @DisplayName("Un visitante no ve las faltas de otro ni aplica ninguna")
    void elVisitanteNoVeLasDeOtros() throws Exception {
        var context = getContext();
        mockMvc.perform(get("/api/v1/amonestaciones/visitante/" + UN_ID).with(securityContext(context)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/amonestaciones")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\":\"" + UN_ID + "\",\"motivo\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
    @DisplayName("El empleado consulta las faltas de otro: las necesita para decidir")
    void elEmpleadoConsultaLasDeOtro() throws Exception {
        mockMvc.perform(get("/api/v1/amonestaciones/visitante/" + UN_ID)
                        .with(securityContext(getContext())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
    @DisplayName("El empleado no levanta bloqueos ni cambia los números del régimen")
    void elEmpleadoNoConfiguraNiRestituye() throws Exception {
        var context = getContext();

        mockMvc.perform(post("/api/v1/amonestaciones/visitante/" + UN_ID + "/restituir")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/amonestaciones/configuracion").with(securityContext(context)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/amonestaciones/configuracion")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxFaltas\":99,\"diasBloqueo\":1,"
                                + "\"toleranciaIngresoMin\":0,\"toleranciaEgresoMin\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = "ADMIN")
    @DisplayName("El administrador lee la configuración, que viene sembrada por la migración")
    void elAdminLeeLaConfiguracion() throws Exception {
        mockMvc.perform(get("/api/v1/amonestaciones/configuracion")
                        .with(securityContext(getContext())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", authorities = "ADMIN")
    @DisplayName("Un máximo de cero se rechaza: bloquearía a cualquiera con su primera falta")
    void rechazaUnMaximoAbsurdo() throws Exception {
        mockMvc.perform(put("/api/v1/amonestaciones/configuracion")
                        .with(securityContext(getContext()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxFaltas\":0,\"diasBloqueo\":30,"
                                + "\"toleranciaIngresoMin\":15,\"toleranciaEgresoMin\":15}"))
                .andExpect(status().isBadRequest());
    }
}
