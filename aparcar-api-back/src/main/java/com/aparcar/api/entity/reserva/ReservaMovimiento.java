package com.aparcar.api.entity.reserva;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** Datos del autor al momento de actuar: no cambian al editar o eliminar su cuenta. */
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "reserva_movimientos")
public class ReservaMovimiento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, updatable = false)
    private Reserva reserva;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ReservaAccion accion;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    // Es una copia de la identidad, no una FK: borrar un admin no borra su historial.
    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "actor_nombre", nullable = false, updatable = false)
    private String actorNombre;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_rol", nullable = false, updatable = false, length = 20)
    private AppAuthority actorRol;

    public ReservaMovimiento(Reserva reserva, ReservaAccion accion, Visitante actor, boolean esAdmin) {
        this.reserva = reserva;
        this.accion = accion;
        this.fecha = Instant.now();
        this.actorId = actor.getId();
        this.actorNombre = actor.getNombre();
        this.actorEmail = actor.getEmail();
        this.actorRol = esAdmin ? AppAuthority.ADMIN : AppAuthority.USER;
    }
}
