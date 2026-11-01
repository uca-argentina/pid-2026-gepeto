package com.aparcar.api.entity.sancion;

import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Reserva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una falta aplicada a una persona.
 *
 * <p>Es un registro inmutable: se crea y no se edita ni se borra. Eso es lo que
 * permite que el historial del perfil sea permanente aunque la falta ya no
 * cuente para el bloqueo.
 *
 * <p>Hay dos lecturas distintas de la misma tabla, y conviene no confundirlas:
 *
 * <ul>
 *   <li><b>Las vigentes</b>: las de los últimos tres meses. Son las que suman
 *       para bloquear la cuenta y las que se dibujan como puntitos.
 *   <li><b>El historial</b>: todas, desde siempre. Es lo que se ve en el
 *       desplegable del perfil.
 * </ul>
 *
 * <p>La falta es de la <b>persona</b>, no del vehículo: el bloqueo alcanza la
 * cuenta entera. El vehículo y la reserva quedan guardados como contexto, para
 * que el historial diga con qué auto y en qué reserva pasó.
 */
@Entity
@Table(name = "amonestaciones")
@Getter
@Setter
@NoArgsConstructor
public class Amonestacion {

    @Id
    @GeneratedValue
    private UUID id;

    /** A quién se le aplicó. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "visitante_id", nullable = false)
    private Visitante visitante;

    /**
     * La reserva que la originó, si hubo una.
     *
     * <p>Queda en null si la reserva se borra: el historial es permanente y no
     * tiene que desaparecer porque se haya limpiado una reserva vieja.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    /** La patente involucrada, copiada como texto para que sobreviva al vehículo. */
    @Column(length = 20)
    private String patente;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenAmonestacion origen;

    /** Lo que escribió quien la aplicó, o el texto que arma el sistema. */
    @Column(nullable = false, length = 500)
    private String motivo;

    /**
     * Quién la aplicó. Null cuando la aplicó el sistema.
     *
     * <p>Se guarda además el nombre como texto, para que el historial siga
     * siendo legible si esa cuenta se da de baja.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Visitante autor;

    @Column(length = 255)
    private String autorNombre;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Amonestacion(Visitante visitante, Reserva reserva, String patente,
                        OrigenAmonestacion origen, String motivo,
                        Visitante autor, LocalDateTime fecha) {
        this.visitante = visitante;
        this.reserva = reserva;
        this.patente = patente;
        this.origen = origen;
        this.motivo = motivo;
        this.autor = autor;
        this.autorNombre = autor != null ? autor.getNombre() : null;
        this.fecha = fecha;
    }

    /** True si la aplicó el sistema y no una persona. */
    public boolean esAutomatica() {
        return origen != OrigenAmonestacion.MANUAL;
    }
}
