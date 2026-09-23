package com.aparcar.api.integration;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.config.IntegrationTests;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTests
public class UserControllerTests {

    @Autowired
    private VisitanteRepository visitanteRepository;

    @Autowired
    private IRevokedUserCache revokedUserCache;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void tearDown() {
        // Clear the repository and cache after each test
        visitanteRepository.deleteAll();
        revokedUserCache.clear();
    }

    @Test
    @WithAnonymousUser
    @DisplayName("/users/** should return 401 Unauthorized for anonymous users")
    void shouldReturnUnauthorizedForAnonymousUsers() throws Exception {
        mockMvc.perform(post("/users/activate"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/users/inactive"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("/users/** should return 403 Forbidden for regular users")
    void shouldReturnForbiddenForRegularUsers() throws Exception {
        var context = getContext();
        mockMvc.perform(post("/users/activate")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/users/inactive")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/users")
                        .with(securityContext(context)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("/users/activate returns 400 Bad Request for invalid email")
    void activateShouldReturnBadRequestForInvalidEmail() throws Exception {
        var context = getContext();
        mockMvc.perform(post("/users/activate")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"invalid-email\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/users/activate")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("/users/activate returns 200 OK for valid email")
    void activateShouldReturnOkForValidEmail() throws Exception {
        Visitante user = new Visitante();
                user.setNombre("Test User");
                user.setDocumento("30111222");
                user.setEmail("some@email.com");
        user.setPassword("password");
        user.setIsActive(false);
        visitanteRepository.save(user);

        var context = getContext();
        mockMvc.perform(post("/users/activate")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"some@email.com\"}"))
                .andExpect(status().isOk());

        // Verify that the user is now active
        Visitante activatedUser = visitanteRepository.findByEmail("some@email.com")
                .orElseThrow(() -> new IllegalStateException("User not found after activation."));

        assertTrue(activatedUser.getIsActive());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("/users/inactive returns 200 OK with a set of inactive users")
    void inactiveShouldReturnOkWithASetOfInactiveUsers() throws Exception {
        Visitante user1 = new Visitante();
                user1.setNombre("Test User");
                user1.setDocumento("30111222");
                user1.setEmail("some@email.com");
        user1.setPassword("password");
        user1.setIsActive(false);
        Visitante user2 = new Visitante();
                user2.setNombre("Test User");
                user2.setDocumento("30111333");
                user2.setEmail("another@email.com");
        user2.setPassword("password");
        user2.setIsActive(true);
        visitanteRepository.save(user1);
        visitanteRepository.save(user2);

        var context = getContext();
        mockMvc.perform(get("/users/inactive")
                        .with(securityContext(context)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emails").isArray())
                .andExpect(jsonPath("$.emails.length()", is(1)))
                .andExpect(jsonPath("$.emails[0]").value(user1.getEmail()));
    }

    @Test
    @WithMockUser(authorities = "ADMIN", username = "some@email.com")
    @DisplayName("DELETE /users returns 400 Bad Request for caller email equal to deleted email")
    void deleteShouldReturnBadRequestForInvalidEmail() throws Exception {
        Visitante user = new Visitante();
                user.setNombre("Test User");
                user.setDocumento("30111222");
                user.setEmail("some@email.com");
        user.setPassword("password");
        user.setIsActive(false);
        visitanteRepository.save(user);

        var context = getContext();
        mockMvc.perform(delete("/users")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"some@email.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "ADMIN", username = "caller@email.com")
    @DisplayName("DELETE /users returns 200 OK for valid email")
    void deleteShouldReturnOkForValidEmail() throws Exception {
        Visitante user = new Visitante();
                user.setNombre("Test User");
                user.setDocumento("30111222");
                user.setEmail("some@email.com");
        user.setPassword("password");
        user.setIsActive(false);
        visitanteRepository.save(user);

        var context = getContext();
        mockMvc.perform(delete("/users")
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"some@email.com\"}"))
                .andExpect(status().isNoContent());

        assertFalse(visitanteRepository.existsByEmail(user.getEmail()));
        assertTrue(revokedUserCache.isRevoked(user.getEmail()));
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("PUT /api/v1/usuarios/{id} actualiza nombre, telefono y authorities")
    void updateUserUpdatesEditableFields() throws Exception {
        Visitante user = new Visitante();
        user.setNombre("Nombre Viejo");
        user.setDocumento("30111222");
        user.setEmail("some@email.com");
        user.setPassword("password");
        user.setTelefono("111");
        user.setAuthorities(java.util.Set.of(com.aparcar.api.entity.auth.AppAuthority.USER));
        user.setIsActive(true);
        visitanteRepository.save(user);

        var context = getContext();
        mockMvc.perform(put("/api/v1/usuarios/" + user.getId())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Nombre Nuevo\", \"documento\": \"30111222\", \"telefono\": \"222\", \"authorities\": [\"USER\", \"ADMIN\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.telefono").value("222"))
                .andExpect(jsonPath("$.authorities", containsInAnyOrder("USER", "ADMIN")));

        Visitante updated = visitanteRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("User not found"));
        assertEquals("Nombre Nuevo", updated.getNombre());
        assertEquals(2, updated.getAuthorities().size());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    @DisplayName("PUT /api/v1/usuarios/{id} devuelve 404 si el usuario no existe")
    void updateUserReturnsNotFoundForUnknownId() throws Exception {
        var context = getContext();
        mockMvc.perform(put("/api/v1/usuarios/" + java.util.UUID.randomUUID())
                        .with(securityContext(context))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Nombre\", \"documento\": \"30111222\", \"telefono\": \"111\", \"authorities\": [\"USER\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("PUT /api/v1/usuarios/{id} devuelve 401 para anonimos")
    void updateUserRejectsAnonymousUsers() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Nombre\", \"documento\": \"30111222\", \"telefono\": \"111\", \"authorities\": [\"USER\"]}"))
                .andExpect(status().isUnauthorized());
    }
}
