package com.aparcar.api.entity.reserva;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Fila unica que coordina la asignacion de lugares entre todas las instancias. */
@Entity
@Getter
@NoArgsConstructor
@Table(name = "planificacion_reservas")
public class PlanificacionReserva {
    @Id
    private Integer id;
}
