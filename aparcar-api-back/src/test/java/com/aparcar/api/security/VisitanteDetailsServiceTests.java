package com.aparcar.api.security;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Caja blanca: prueba directamente el puente entre Visitante y
 * UserDetails que usa Spring Security para autenticar.
 */
@ExtendWith(MockitoExtension.class)
class VisitanteDetailsServiceTests {

    @Mock
    private VisitanteRepository visitanteRepository;

    @InjectMocks
    private VisitanteDetailsService appUserDetailsService;

    @Test
    @DisplayName("loadUserByUsername mapea las authorities del Visitante a GrantedAuthority")
    void loadUserByUsernameMapsAuthorities() {
        Visitante user = new Visitante();
        user.setEmail("mateo@mateo.com");
        user.setPassword("hashed-password");
        user.setAuthorities(Set.of(AppAuthority.USER, AppAuthority.ADMIN));

        when(visitanteRepository.findByEmail("mateo@mateo.com")).thenReturn(Optional.of(user));

        UserDetails result = appUserDetailsService.loadUserByUsername("mateo@mateo.com");

        assertEquals("mateo@mateo.com", result.getUsername());
        assertEquals("hashed-password", result.getPassword());
        assertEquals(2, result.getAuthorities().size());
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("USER")));
        assertTrue(result.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ADMIN")));
    }

    @Test
    @DisplayName("loadUserByUsername lanza UsernameNotFoundException si el email no existe")
    void loadUserByUsernameThrowsWhenUserNotFound() {
        when(visitanteRepository.findByEmail("no-existe@mateo.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> appUserDetailsService.loadUserByUsername("no-existe@mateo.com"));
    }
}
