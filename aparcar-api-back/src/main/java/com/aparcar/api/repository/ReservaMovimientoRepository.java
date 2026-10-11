package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.ReservaAccion;
import com.aparcar.api.entity.reserva.ReservaMovimiento;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaMovimientoRepository extends JpaRepository<ReservaMovimiento, UUID> {

    /** Los movimientos de esas acciones desde un instante, los mas recientes primero. */
    @Query("SELECT m FROM ReservaMovimiento m "
            + "JOIN FETCH m.reserva r JOIN FETCH r.visitante JOIN FETCH r.vehiculo JOIN FETCH r.cochera "
            + "WHERE m.accion IN :acciones AND m.fecha >= :desde "
            + "ORDER BY m.fecha DESC, m.id DESC")
    List<ReservaMovimiento> findRecientes(@Param("acciones") Collection<ReservaAccion> acciones,
                                          @Param("desde") Instant desde,
                                          Limit limit);
}
