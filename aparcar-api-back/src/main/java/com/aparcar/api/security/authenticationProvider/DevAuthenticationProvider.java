package com.aparcar.api.security.authenticationProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import static com.aparcar.api.config.ApplicationConstants.DEV_ENV;
import static com.aparcar.api.config.ApplicationConstants.TEST_ENV;

@Component
@RequiredArgsConstructor
@Profile({DEV_ENV, TEST_ENV})
public class DevAuthenticationProvider implements AuthenticationProvider {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            String email = authentication.getName();
            String password = authentication.getCredentials().toString();
            UserDetails user = userDetailsService.loadUserByUsername(email);

            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BadCredentialsException("Bad credentials.");
            }

            // Additional validations

            return new UsernamePasswordAuthenticationToken(user.getUsername(), password, user.getAuthorities());
        } catch (UsernameNotFoundException e) {
            throw new BadCredentialsException("Bad credentials.");
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return (UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication));
    }
}
