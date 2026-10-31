package com.aparcar.api.repository;

import com.aparcar.api.entity.puntos.MovimientoPuntos;
import com.aparcar.api.entity.puntos.TipoMovimientoPuntos;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface MovimientoPuntosRepository extends JpaRepository<MovimientoPuntos, UUID> {
    List<MovimientoPuntos> findByVisitanteIdOrderByFechaDescIdDesc(UUID visitanteId, Limit limit);

    boolean existsByVisitanteIdAndReservaIdAndTipo(UUID visitanteId, UUID reservaId, TipoMovimientoPuntos tipo);

    /** Para el tope diario: acreditaciones de franja en el dia [desde, hasta). */
    long countByVisitanteIdAndTipoAndFechaGreaterThanEqualAndFechaLessThan(
            UUID visitanteId, TipoMovimientoPuntos tipo, Instant desde, Instant hasta);

    /** El saldo segun el libro; tiene que coincidir con el de la billetera. */
    @Query("SELECT COALESCE(SUM(m.puntos), 0) FROM MovimientoPuntos m WHERE m.visitanteId = :visitanteId")
    long sumarPorVisitante(@Param("visitanteId") UUID visitanteId);

    @Query("SELECT COALESCE(SUM(m.puntos), 0) FROM MovimientoPuntos m WHERE m.tipo = :tipo")
    long sumarPorTipo(@Param("tipo") TipoMovimientoPuntos tipo);
}
