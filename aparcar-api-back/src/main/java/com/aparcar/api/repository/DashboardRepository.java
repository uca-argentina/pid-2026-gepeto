package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Proyecciones acotadas: no hidratan visitantes, vehiculos ni historiales. */
public interface DashboardRepository extends Repository<Reserva, UUID> {
    interface CocheraFila {
        UUID getId();
        String getNumero();
        String getSector();
        CocheraEstado getEstado();
    }

    interface ReservaFila {
        UUID getId();
        UUID getCocheraId();
        ReservaEstado getEstado();
        LocalDateTime getDesde();
        LocalDateTime getHasta();
        Instant getCodigoEmitidoAt();
        LocalDateTime getIngresoAt();
        LocalDateTime getEgresoAt();
    }

    @Query("SELECT c.id AS id, c.numero AS numero, c.sector AS sector, c.estado AS estado FROM Cochera c")
    List<CocheraFila> cocheras();

    /** Solo el dia, vehiculos aun adentro y proximos ingresos (incluso al cruzar medianoche). */
    @Query("""
            SELECT r.id AS id, r.cochera.id AS cocheraId, r.estado AS estado,
                   r.desde AS desde, r.hasta AS hasta, r.codigoEmitidoAt AS codigoEmitidoAt,
                   r.ingresoAt AS ingresoAt, r.egresoAt AS egresoAt
            FROM Reserva r
            WHERE (r.desde >= :inicioDia AND r.desde < :finDia)
               OR r.estado = com.aparcar.api.entity.reserva.ReservaEstado.EN_CURSO
               OR (r.ingresoAt >= :inicioDia AND r.ingresoAt <= :ahora)
               OR (r.egresoAt >= :inicioDia AND r.egresoAt <= :ahora)
               OR (r.estado = com.aparcar.api.entity.reserva.ReservaEstado.CONFIRMADA
                   AND r.desde >= :ahora AND r.desde < :horizonte)
            """)
    List<ReservaFila> reservasRelevantes(@Param("inicioDia") LocalDateTime inicioDia,
                                        @Param("finDia") LocalDateTime finDia,
                                        @Param("ahora") LocalDateTime ahora,
                                        @Param("horizonte") LocalDateTime horizonte);
}
