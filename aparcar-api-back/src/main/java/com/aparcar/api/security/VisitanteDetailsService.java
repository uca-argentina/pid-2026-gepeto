package com.aparcar.api.security;

import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that loads user-specific data for Spring Security authentication.
 * Bridges the application's user repository with Spring Security's
 * authentication mechanism.
 */
@Service
@RequiredArgsConstructor
public class VisitanteDetailsService implements UserDetailsService {
    private final VisitanteRepository visitanteRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Visitante user = visitanteRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found for email " + username));
        List<GrantedAuthority> authorities = user.getAuthorities().stream()
                .map(appAuthority -> new SimpleGrantedAuthority(appAuthority.name())).collect(Collectors.toList());
        return new User(user.getEmail(), user.getPassword(), authorities);
    }
}
