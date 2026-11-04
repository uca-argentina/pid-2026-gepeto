package com.aparcar.api.repository;

import com.aparcar.api.entity.sancion.Amonestacion;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AmonestacionRepository extends JpaRepository<Amonestacion, UUID> {

    /**
     * El historial completo, de la más nueva a la más vieja.
     *
     * <p>Sin recorte por fecha: es lo que se muestra en el desplegable del
     * perfil, que es permanente.
     */
    List<Amonestacion> findByVisitanteIdOrderByFechaDesc(UUID visitanteId);

    /**
     * Las que todavía cuentan para el bloqueo.
     *
     * <p>Es la otra lectura de la misma tabla: solo las posteriores al corte de
     * la ventana. Son las que se dibujan como puntitos y las que deciden si la
     * cuenta se bloquea.
     */
    long countByVisitanteIdAndFechaAfter(UUID visitanteId, LocalDateTime desde);

    /**
     * Si ya existe la falta automática de esta reserva por este motivo.
     *
     * <p>La tarea que las aplica corre cada minuto y vuelve a mirar las mismas
     * reservas una y otra vez. Sin esta consulta, una reserva sin ingreso
     * acumularía una falta por minuto.
     */
    boolean existsByReservaIdAndOrigen(UUID reservaId, OrigenAmonestacion origen);

    /** Cuántas tiene cada persona de una lista, para el panel de cuentas. */
    @Query("SELECT a.visitante.id, COUNT(a) FROM Amonestacion a "
            + "WHERE a.visitante.id IN :ids AND a.fecha > :desde GROUP BY a.visitante.id")
    List<Object[]> contarVigentesPorVisitante(@Param("ids") List<UUID> ids,
                                              @Param("desde") LocalDateTime desde);
}
