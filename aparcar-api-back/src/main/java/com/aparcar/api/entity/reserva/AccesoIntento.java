package com.aparcar.api.entity.reserva;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Cada intento de validar un codigo de acceso, haya salido bien o no: quien lo
 * hizo, que se pidio y que paso.
 *
 * <p>La reserva y el operador se guardan como copia, sin FK, igual que en
 * {@link ReservaMovimiento}: el registro sobrevive a que se borre cualquiera de
 * los dos, y un intento con un codigo inexistente no tiene reserva.
 */
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "acceso_intentos")
public class AccesoIntento {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AccesoOperacion operacion;

    /** Lo que se recibio, recortado: puede no ser un codigo valido. */
    @Column(nullable = false, updatable = false, length = 40)
    private String codigo;

    @Column(name = "reserva_id", updatable = false)
    private UUID reservaId;

    /** OK, YA_REGISTRADO o el motivo del rechazo. */
    @Column(nullable = false, updatable = false, length = 30)
    private String resultado;

    @Column(name = "actor_email", nullable = false, updatable = false)
    private String actorEmail;

    public AccesoIntento(Instant fecha, AccesoOperacion operacion, String codigo, UUID reservaId,
                         String resultado, String actorEmail) {
        this.fecha = fecha;
        this.operacion = operacion;
        this.codigo = codigo == null ? "" : codigo.length() > 40 ? codigo.substring(0, 40) : codigo;
        this.reservaId = reservaId;
        this.resultado = resultado;
        this.actorEmail = actorEmail;
    }
}
