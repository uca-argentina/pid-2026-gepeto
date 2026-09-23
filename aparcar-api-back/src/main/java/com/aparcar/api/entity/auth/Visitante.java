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
}
