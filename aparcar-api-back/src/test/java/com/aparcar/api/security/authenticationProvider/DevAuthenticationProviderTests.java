package com.aparcar.api.security.authenticationProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Caja blanca: verifica que el provider de dev valide las credenciales
 * con el mismo comportamiento observable que el provider de producción.
 */
@ExtendWith(MockitoExtension.class)
class DevAuthenticationProviderTests {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DevAuthenticationProvider provider;

    @Test
    @DisplayName("autentica exitosamente cuando la contraseña matchea el hash")
    void authenticatesWhenPasswordMatches() {
        UserDetails userDetails = new User(
                "mateo@mateo.com", "hashed-password", List.of(new SimpleGrantedAuthority("USER")));
        when(userDetailsService.loadUserByUsername("mateo@mateo.com")).thenReturn(userDetails);
        when(passwordEncoder.matches("correcta", "hashed-password")).thenReturn(true);

        Authentication input = new UsernamePasswordAuthenticationToken("mateo@mateo.com", "correcta");
        Authentication result = provider.authenticate(input);

        assertEquals("mateo@mateo.com", result.getName());
        assertEquals(1, result.getAuthorities().size());
    }

    @Test
    @DisplayName("rechaza con BadCredentialsException cuando la contraseña no matchea")
    void rejectsWhenPasswordDoesNotMatch() {
        UserDetails userDetails = new User(
                "mateo@mateo.com", "hashed-password", List.of(new SimpleGrantedAuthority("USER")));
        when(userDetailsService.loadUserByUsername("mateo@mateo.com")).thenReturn(userDetails);
        when(passwordEncoder.matches("incorrecta", "hashed-password")).thenReturn(false);

        Authentication input = new UsernamePasswordAuthenticationToken("mateo@mateo.com", "incorrecta");

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(input));
    }

    @Test
    @DisplayName("rechaza con BadCredentialsException cuando el email no existe")
    void rejectsWithBadCredentialsWhenUserDoesNotExist() {
        when(userDetailsService.loadUserByUsername("no-existe@mateo.com"))
                .thenThrow(new UsernameNotFoundException("User not found for email no-existe@mateo.com"));

        Authentication input = new UsernamePasswordAuthenticationToken("no-existe@mateo.com", "algo");

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(input));
    }

    @Test
    @DisplayName("supports() solo acepta UsernamePasswordAuthenticationToken")
    void supportsOnlyUsernamePasswordAuthenticationToken() {
        assertEquals(true, provider.supports(UsernamePasswordAuthenticationToken.class));
    }
}
