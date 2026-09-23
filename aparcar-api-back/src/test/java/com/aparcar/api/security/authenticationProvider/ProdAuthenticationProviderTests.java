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
 * Caja blanca: a diferencia de DevAuthenticationProvider, este SÍ valida
 * la contraseña — es el que corre en producción real (perfil "prod").
 */
@ExtendWith(MockitoExtension.class)
class ProdAuthenticationProviderTests {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ProdAuthenticationProvider provider;

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
    @DisplayName("rechaza con BadCredentialsException (no UsernameNotFoundException) cuando el email no existe")
    void rejectsWithBadCredentialsWhenUserDoesNotExist() {
        // Importante: no debe filtrar si el email existe o no (evita "enumeration attacks").
        when(userDetailsService.loadUserByUsername("no-existe@mateo.com"))
                .thenThrow(new UsernameNotFoundException("User not found"));

        Authentication input = new UsernamePasswordAuthenticationToken("no-existe@mateo.com", "algo");

        assertThrows(BadCredentialsException.class, () -> provider.authenticate(input));
    }
}
