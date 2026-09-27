package com.aparcar.api.entity.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ElementCollection(targetClass = AppAuthority.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "visitante_authorities", joinColumns = @JoinColumn(name = "visitante_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Set<AppAuthority> authorities;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Si la persona tiene una discapacidad que la habilita a usar las cocheras
     * de tipo ACCESIBLE.
     *
     * <p>Es un dato autodeclarado: lo marca el propio visitante desde su
     * perfil (o el admin al darlo de alta, si ya lo sabe). No hay flujo de
     * aprobacion: el sistema confia en la declaracion, igual que un permiso
     * de estacionamiento en la calle.
     *
     * <p>Arranca en false para cualquier cuenta nueva, se cree por el
     * constructor que se cree, asi ninguna alta existente tiene que acordarse
     * de setearlo.
     */
    @Column(name = "tiene_discapacidad", nullable = false)
    private Boolean tieneDiscapacidad = false;

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