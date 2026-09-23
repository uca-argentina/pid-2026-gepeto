package com.aparcar.api.dto.auth;

import com.aparcar.api.entity.auth.AppAuthority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateUserDto(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name can't be longer than 100 characters")
        String nombre,

        @NotBlank(message = "El documento es obligatorio")
        String documento,

        String telefono,

        @NotEmpty(message = "At least one authority is required")
        Set<AppAuthority> authorities
) {
}
