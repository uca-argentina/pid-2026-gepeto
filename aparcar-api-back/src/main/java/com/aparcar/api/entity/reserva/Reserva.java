package com.aparcar.api.entity.reserva;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "reservas")
public class Reserva {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Inicio de la franja reservada, inclusive.
     *
     * <p>La reserva dejo de ser "por dia" para pasar a ser un rango: dos
     * reservas sobre la misma cochera se pisan si y solo si sus rangos se
     * solapan. El intervalo es semiabierto [desde, hasta), asi que una reserva
     * que termina justo cuando arranca la siguiente NO se considera
     * superpuesta: el lugar queda libre en ese instante.
     */
    @Column(nullable = false)
    private LocalDateTime desde;

    /**
     * Fin de la franja, exclusive. Cuando pasa, la cochera queda libre sola:
     * ningun rango posterior se solapa con uno ya terminado, sin depender de
     * que corra ninguna tarea.
     */
    @Column(nullable = false)
    private LocalDateTime hasta;

    @ManyToOne(optional = false)
    @JoinColumn(name = "visitante_id", nullable = false)
    private Visitante visitante;

    @ManyToOne(optional = false)
    @JoinColumn(name = "vehiculo_id", nullable = false)
    private Vehiculo vehiculo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cochera_id", nullable = false)
    private Cochera cochera;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservaEstado estado;

    /** Importe acordado al crear. Null identifica reservas anteriores a las tarifas. */
    @Column(name = "precio_total", precision = 18, scale = 2, updatable = false)
    private BigDecimal precioTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_tarifa", length = 20, updatable = false)
    private CocheraTipo tipoTarifa;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;

    /** Evita que dos bajas simultaneas sobrescriban al autor y al motivo. */
    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL)
    @OrderBy("fecha ASC, id ASC")
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private List<ReservaMovimiento> historial = new ArrayList<>();

    public List<ReservaMovimiento> getHistorial() {
        return List.copyOf(historial);
    }

    public void registrarAccion(ReservaAccion accion, Visitante actor, boolean esAdmin) {
        historial.add(new ReservaMovimiento(this, accion, actor, esAdmin));
    }

    /** Null para reservas anteriores a la trazabilidad: no se infiere un autor. */
    public ReservaMotivoCancelacion getMotivoCancelacion() {
        if (estado != ReservaEstado.CANCELADA) return null;
        for (ReservaMovimiento movimiento : historial) {
            if (movimiento.getAccion() == ReservaAccion.DESHABILITACION) {
                return ReservaMotivoCancelacion.DESHABILITACION;
            }
            if (movimiento.getAccion() == ReservaAccion.CANCELACION) {
                return movimiento.getActorRol() == AppAuthority.ADMIN
                        ? ReservaMotivoCancelacion.ADMINISTRACION : ReservaMotivoCancelacion.USUARIO;
            }
        }
        return null;
    }

    /** Como se esta reservando: por franja, media jornada o jornada completa. */
    public ModalidadReserva getModalidad() {
        return ModalidadReserva.de(desde, hasta);
    }

    /** True si la franja esta corriendo en el instante dado. */
    public boolean estaVigenteEn(LocalDateTime momento) {
        return !momento.isBefore(desde) && momento.isBefore(hasta);
    }

    /** True si esta reserva se pisa con el rango [desde, hasta). */
    public boolean seSolapaCon(LocalDateTime otroDesde, LocalDateTime otroHasta) {
        return desde.isBefore(otroHasta) && hasta.isAfter(otroDesde);
    }
}
