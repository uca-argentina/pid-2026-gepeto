package com.aparcar.api.dto.auth;

import java.util.Set;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String nombre,
        String documento,
        String email,
        String telefono,
        Set<String> authorities,
        Boolean isActive
) {
}
