package com.aparcar.api.dto.auth;

import jakarta.validation.constraints.Email;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Alta de cuenta sin reserva, compartida por visitantes y Gestión de usuarios.
 * No acepta roles ni estado: esos valores los decide el servidor.
 */
@Data
@NoArgsConstructor
public class RegistrationDto {
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name can't be longer than 100 characters")
    private String nombre;

    @NotBlank(message = "El documento es obligatorio")
    @Size(max = 255, message = "El documento no puede superar los 255 caracteres")
    private String documento;

    @Email
    @NotBlank(message = "Email is required")
    @Size(max = 255, message = "El email no puede superar los 255 caracteres")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Size(max = 72, message = "La contraseña no puede superar los 72 caracteres")
    private String password;

    // Opcional: no todos los visitantes cargan teléfono al registrarse
    @Size(max = 255, message = "El teléfono no puede superar los 255 caracteres")
    private String telefono;

    public RegistrationDto(String nombre, String documento, String email, String password, String telefono) {
        setNombre(nombre);
        setDocumento(documento);
        setEmail(email);
        this.password = password;
        this.telefono = telefono;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre == null ? null : nombre.trim();
    }

    public void setDocumento(String documento) {
        this.documento = documento == null ? null : documento.trim();
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    // BCrypt limita por bytes, no por caracteres; no truncar claves Unicode.
    @JsonIgnore
    @AssertTrue(message = "La contraseña no puede superar los 72 bytes UTF-8")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
