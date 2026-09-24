package com.aparcar.api.entity.reserva;

import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
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

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private Instant fechaCreacion;
}
