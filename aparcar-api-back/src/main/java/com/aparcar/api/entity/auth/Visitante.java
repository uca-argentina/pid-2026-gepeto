package com.aparcar.api.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Entidad unica del sistema: es a la vez la cuenta con la que se inicia sesion
 * y la persona que reserva una cochera.
 *
 * <p>Antes esto eran dos entidades separadas (AppUser y Visitante) unidas por
 * un @OneToOne opcional. Esa separacion generaba "visitantes fantasma": el
 * admin cargaba un visitante sin cuenta, el mismo visitante se registraba por
 * su cuenta y quedaban dos filas distintas para la misma persona, cada una con
 * sus propias reservas. Al unificarlas, un documento es una persona es una
 * cuenta, y no hay forma de crear una sin la otra.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "visitantes")
public class Visitante {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nombre;

    // Identifica a la persona. Es ademas la contraseña inicial cuando el alta
    // la hace un admin desde el panel.
    @Column(nullable = false, unique = true)
    private String documento;

    // Es el identificador de login, asi que ahora es obligatorio: antes era
    // opcional porque un visitante podia no tener cuenta.
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String telefono;

    @Column(name = "nombre_estacionamiento", length = 100)
    private String nombreEstacionamiento;

    @ElementCollection(targetClass = AppAuthority.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "visitante_authorities", joinColumns = @JoinColumn(name = "visitante_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Set<AppAuthority> authorities;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Hasta cuándo la cuenta está inhabilitada por acumular faltas.
     *
     * <p>Null, o una fecha ya pasada, significa que no está bloqueada: el
     * bloqueo se vence solo, sin que nadie tenga que ejecutar nada. La tarea
     * programada que restituye cuentas existe para limpiar el campo y avisar,
     * no para que el bloqueo deje de aplicar.
     *
     * <p>Es distinto de {@code isActive}, que es una baja administrativa de la
     * cuenta y no tiene vencimiento. Una cuenta bloqueada sigue activa: entra,
     * ve su historial y sus faltas; lo único que no puede es reservar.
     */
    @Column(name = "bloqueado_hasta")
    private java.time.LocalDateTime bloqueadoHasta;

    /** True si hay un bloqueo por faltas vigente en este momento. */
    public boolean estaBloqueado(java.time.LocalDateTime ahora) {
        return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
    }

    /**
     * Si la persona puede usar las cocheras de tipo ACCESIBLE.
     *
     * <p>Es un valor DERIVADO de la verificacion con certificado: lo pone en
     * true solo la aprobacion de un ADMIN (o el plazo de gracia de una cuenta
     * que se habia autodeclarado antes de la verificacion), y lo vuelven a
     * false el rechazo, la revocacion, el vencimiento o el fin de la gracia.
     * Ningun endpoint de perfil, alta ni registro lo recibe. Lo mantiene
     * {@code VerificacionDiscapacidadService}; reservas y cocheras solo lo leen.
     *
     * <p>Arranca en false para cualquier cuenta nueva, se cree por el
     * constructor que se cree.
     */
    @Column(name = "tiene_discapacidad", nullable = false)
    private Boolean tieneDiscapacidad = false;

    /**
     * Hasta cuando conserva el acceso a las ACCESIBLE una cuenta que se habia
     * autodeclarado antes de la verificacion con certificado. Null para el
     * resto. Al resolverse su primera solicitud, o al vencer, vuelve a null.
     */
    @Column(name = "discapacidad_acceso_provisorio_hasta")
    private Instant discapacidadAccesoProvisorioHasta;

    public Visitante(String nombre, String documento, String email, String password, String telefono,
                     Set<AppAuthority> authorities, Boolean isActive) {
        this.nombre = nombre;
        this.documento = documento;
        this.email = email;
        this.password = password;
        this.telefono = telefono;
        this.authorities = authorities;
        this.isActive = isActive;
    }

    /**
     * True si esta persona puede reservar una cochera ACCESIBLE. Tolera null
     * (lo trata como false) para no depender de que nadie haya seteado el
     * campo a mano en un objeto armado fuera de la base.
     */
    public boolean puedeUsarCocheraAccesible() {
        return Boolean.TRUE.equals(tieneDiscapacidad);
    }
}
