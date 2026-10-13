package com.aparcar.api.integration;

import com.aparcar.api.config.IntegrationTests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Caja negra: qué puede y qué no puede tocar el rol EMPLEADO.
 *
 * <p>La idea del rol es la frontera entre <b>operar</b> y <b>configurar</b>. El
 * empleado atiende el día a día —ve las reservas de todos, las carga, las
 * cancela, da de alta visitantes y controla el ingreso en la puerta— pero no
 * cambia las reglas con las que el sistema funciona: ni los precios, ni las
 * cuentas, ni qué cocheras existen o están habilitadas.
 *
 * <p>Esta clase deja esa frontera por escrito. Si alguien más adelante agrega
 * un endpoint de configuración y lo deja abierto al personal, o recorta uno de
 * operación y deja al empleado sin poder trabajar, acá se rompe y se ve por
 * qué. Los dos errores son silenciosos de otra forma: el primero no falla
 * nunca, y el segundo falla recién cuando alguien está en el mostrador.
 */
@IntegrationTests
class EmpleadoAccessSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Lo que el empleado necesita para trabajar")
    class Operacion {

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("Ve el listado completo de reservas, que es su pantalla de trabajo")
        void veTodasLasReservas() throws Exception {
            mockMvc.perform(get("/api/v1/reservas").with(securityContext(getContext())))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("Ve la grilla de cocheras: es la pantalla de ocupación con la que abre el panel")
        void veLaGrillaDeCocheras() throws Exception {
            mockMvc.perform(get("/api/v1/cocheras").with(securityContext(getContext())))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("Ve el catálogo de visitantes y vehículos, que necesita para reservar a nombre de otro")
        void veLosCatalogosParaReservarPorOtro() throws Exception {
            var context = getContext();
            mockMvc.perform(get("/api/v1/visitantes").with(securityContext(context)))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/vehiculos").with(securityContext(context)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("Puede leer tarifas y cotizar: sin eso no podría armar una reserva")
        void puedeCotizar() throws Exception {
            mockMvc.perform(get("/api/v1/tarifas").with(securityContext(getContext())))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("Llega al control de accesos, que es su tarea en la puerta")
        void llegaAlControlDeAccesos() throws Exception {
            // Lo que importa es que no rebote por rol. El contenido lo cubren
            // los tests del propio controlador.
            mockMvc.perform(get("/api/v1/accesos/movimientos").with(securityContext(getContext())))
                    .andExpect(noEs(403));
        }
    }

    @Nested
    @DisplayName("Lo que configura el sistema y no le corresponde")
    class Configuracion {

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("No gestiona cuentas: crear un empleado es potestad del administrador")
        void noGestionaCuentas() throws Exception {
            mockMvc.perform(get("/api/v1/usuarios").with(securityContext(getContext())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("No cambia precios, aunque sí pueda leerlos")
        void noCambiaPrecios() throws Exception {
            mockMvc.perform(put("/api/v1/tarifas/AUTO")
                            .with(securityContext(getContext()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"hora\":1,\"fraccion\":1,\"mediaJornada\":1,\"jornadaCompleta\":1}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("No habilita ni deshabilita una cochera, aunque la vea en la pantalla de ocupación")
        void noCambiaElEstadoDeUnaCochera() throws Exception {
            mockMvc.perform(put("/api/v1/cocheras/" + java.util.UUID.randomUUID())
                            .with(securityContext(getContext()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"numero\":\"A-01\",\"sector\":\"Planta Baja\","
                                    + "\"tipo\":\"AUTO\",\"estado\":\"DESHABILITADA\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("No arma series de reservas recurrentes")
        void noArmaRecurrentes() throws Exception {
            mockMvc.perform(get("/api/v1/reservas/recurrentes").with(securityContext(getContext())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "empleado@test.com", authorities = "EMPLEADO")
        @DisplayName("No entra al tablero de métricas")
        void noEntraAlTableroDeMetricas() throws Exception {
            mockMvc.perform(get("/api/v1/dashboard").with(securityContext(getContext())))
                    .andExpect(status().isForbidden());
        }
    }

    /** Azúcar para leer mejor la expectativa de "cualquier cosa menos 403". */
    private static org.springframework.test.web.servlet.ResultMatcher noEs(int codigo) {
        return resultado -> {
            int real = resultado.getResponse().getStatus();
            if (real == codigo) {
                throw new AssertionError("Se esperaba cualquier estado menos " + codigo + ", y llegó " + real);
            }
        };
    }
}
