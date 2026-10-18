package com.aparcar.api.entity.discapacidad;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Quien abrio un certificado y cuando. Solo la identidad y la fecha: nunca el
 * contenido. Ids copiados, sin FK, para que la auditoria sobreviva a las bajas.
 */
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "verificacion_documento_accesos")
public class VerificacionDocumentoAcceso {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "verificacion_id", nullable = false, updatable = false)
    private UUID verificacionId;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    /** ADMIN o USER (el dueño mirando su propio certificado). */
    @Column(name = "actor_rol", nullable = false, updatable = false, length = 20)
    private String actorRol;

    public VerificacionDocumentoAcceso(UUID verificacionId, Instant fecha, UUID actorId, String actorEmail,
                                       String actorRol) {
        this.verificacionId = verificacionId;
        this.fecha = fecha;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.actorRol = actorRol;
    }
}
