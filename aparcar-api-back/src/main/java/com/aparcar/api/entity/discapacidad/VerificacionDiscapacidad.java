package com.aparcar.api.entity.discapacidad;

import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una solicitud de verificacion de discapacidad: el visitante sube su
 * certificado (por ejemplo el CUD) y un ADMIN lo aprueba o lo rechaza.
 *
 * <p>Las solicitudes no se borran: quedan como registro de quien decidio que y
 * cuando. Lo que si se borra es el archivo del certificado, pasada la
 * retencion: es un dato de salud y solo hace falta mientras se revisa. Del
 * documento se guardan solo metadatos; nunca su contenido ni datos medicos.
 *
 * <p>Quien resolvio o revoco se guarda como copia (id, nombre y email al
 * momento de actuar), igual que en el historial de reservas: el registro
 * sobrevive a que esa cuenta cambie o se elimine.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "verificaciones_discapacidad")
public class VerificacionDiscapacidad {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visitante_id", nullable = false, updatable = false)
    private Visitante visitante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificacionEstado estado;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Nombre interno del archivo (UUID + extension), generado por el servidor. */
    @Column(name = "documento_clave", nullable = false, updatable = false, length = 60)
    private String documentoClave;

    /** Nombre original saneado, solo para mostrarlo. Nunca se usa como ruta. */
    @Column(name = "documento_nombre", nullable = false, updatable = false, length = 120)
    private String documentoNombre;

    @Column(name = "documento_tipo", nullable = false, updatable = false, length = 40)
    private String documentoTipo;

    @Column(name = "documento_tamano", nullable = false, updatable = false)
    private Long documentoTamano;

    @Column(name = "documento_sha256", nullable = false, updatable = false, length = 64)
    private String documentoSha256;

    /** Cuando se borro el archivo. Null mientras exista. */
    @Column(name = "documento_eliminado_at")
    private Instant documentoEliminadoAt;

    @Column(name = "resuelta_at")
    private Instant resueltaAt;

    @Column(name = "resuelta_por_id")
    private UUID resueltaPorId;

    @Column(name = "resuelta_por_nombre")
    private String resueltaPorNombre;

    @Column(name = "resuelta_por_email")
    private String resueltaPorEmail;

    @Column(name = "revocada_at")
    private Instant revocadaAt;

    @Column(name = "revocada_por_id")
    private UUID revocadaPorId;

    @Column(name = "revocada_por_nombre")
    private String revocadaPorNombre;

    @Column(name = "revocada_por_email")
    private String revocadaPorEmail;

    /** Motivo del rechazo o de la revocacion, visible para el visitante. */
    @Column(length = 500)
    private String motivo;

    /**
     * Vigencia del certificado, opcional: la verificacion vale hasta ese dia inclusive.
     *
     * <p>Se enlaza como LocalDate directo, sin pasar por java.sql.Date: esa
     * conversion usa la zona por defecto de la JVM, que ZonaHorariaConfig cambia
     * despues de arrancar. Si el driver se quedo con la zona del arranque (UTC
     * en el CI), un dia guardado a medianoche del predio volvia como el dia
     * anterior.
     */
    @Column(name = "vence_el")
    @JdbcType(LocalDateJdbcType.class)
    private LocalDate venceEl;

    /** Si dos administradores resuelven a la vez, el segundo choca con esta version. */
    @Version
    @Column(nullable = false)
    private Long version;

    public boolean documentoDisponible() {
        return documentoEliminadoAt == null;
    }

    /** Desde cuando corre la retencion del archivo: la ultima decision sobre la solicitud. */
    public Instant cerradaAt() {
        return revocadaAt != null ? revocadaAt : resueltaAt;
    }
}
