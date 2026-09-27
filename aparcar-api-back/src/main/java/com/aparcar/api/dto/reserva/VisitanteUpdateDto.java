package com.aparcar.api.dto.reserva;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Datos de la cuenta autenticada. Documento y nombre del estacionamiento
 * solo se pueden editar si la cuenta es ADMIN (validado en el servicio).
 */
@Data
public class VisitanteUpdateDto {
    @Size(max = 255, message = "El teléfono no puede superar los 255 caracteres")
    private String telefono;

    @Size(max = 255, message = "El documento no puede superar los 255 caracteres")
    @Pattern(regexp = "(?s).*\\S.*", message = "El documento no puede quedar vacío")
    private String documento;

    @Size(max = 100, message = "El nombre del estacionamiento no puede superar los 100 caracteres")
    private String nombreEstacionamiento;

    // Es el identificador de login, asi que no puede quedar vacio.
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato valido")
    @Size(max = 255, message = "El email no puede superar los 255 caracteres")
    private String email;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public void setDocumento(String documento) {
        this.documento = documento == null ? null : documento.trim();
    }

    public void setNombreEstacionamiento(String nombreEstacionamiento) {
        this.nombreEstacionamiento = nombreEstacionamiento == null ? null : nombreEstacionamiento.trim();
    }

    /**
     * Si la persona tiene una discapacidad (habilita las cocheras ACCESIBLE).
     *
     * <p>Opcional a proposito: si no viene (null), el valor guardado no se
     * toca. Asi un cliente que todavia no manda este campo no le borra la
     * declaracion a nadie sin querer.
     */
    private Boolean tieneDiscapacidad;
}
