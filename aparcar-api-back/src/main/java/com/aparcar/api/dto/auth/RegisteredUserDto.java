package com.aparcar.api.dto.auth;

import com.aparcar.api.entity.auth.AppAuthority;

import java.util.Set;

public record RegisteredUserDto(
        String nombre,
        String documento,
        String email,
        String telefono,
        Set<AppAuthority> authorities
) {
}
