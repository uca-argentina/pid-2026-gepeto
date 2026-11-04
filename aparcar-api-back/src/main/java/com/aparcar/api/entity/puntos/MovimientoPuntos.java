package com.aparcar.api.entity.puntos;

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
 * Un movimiento del libro de puntos: inmutable, con signo (positivo suma,
 * negativo resta). La combinacion visitante + reserva + tipo es unica en la
 * base: una reserva no puede acreditar, canjear ni reintegrar dos veces.
 */
@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "movimientos_puntos")
public class MovimientoPuntos {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "visitante_id", nullable = false, updatable = false)
    private UUID visitanteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private TipoMovimientoPuntos tipo;

    @Column(nullable = false, updatable = false)
    private Integer puntos;

    /** Copia del id, sin FK: el movimiento sobrevive a la reserva. */
    @Column(name = "reserva_id", updatable = false)
    private UUID reservaId;

    @Column(nullable = false, updatable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, updatable = false)
    private Instant fecha;

    public MovimientoPuntos(UUID visitanteId, TipoMovimientoPuntos tipo, int puntos, UUID reservaId,
                            String descripcion, Instant fecha) {
        this.visitanteId = visitanteId;
        this.tipo = tipo;
        this.puntos = puntos;
        this.reservaId = reservaId;
        this.descripcion = descripcion;
        this.fecha = fecha;
    }
}
