package com.aparcar.api.repository;

import com.aparcar.api.entity.discapacidad.VerificacionDiscapacidad;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificacionDiscapacidadRepository extends JpaRepository<VerificacionDiscapacidad, UUID> {

    /** La ultima solicitud de la persona: de ahi sale su estado. */
    Optional<VerificacionDiscapacidad> findFirstByVisitanteIdOrderByFechaCreacionDescIdDesc(UUID visitanteId);

    boolean existsByVisitanteIdAndEstado(UUID visitanteId, VerificacionEstado estado);

    long countByEstado(VerificacionEstado estado);

    /** La cola del admin: mas viejas primero, con el solicitante. */
    @Query("SELECT v FROM VerificacionDiscapacidad v JOIN FETCH v.visitante "
            + "WHERE v.estado = :estado ORDER BY v.fechaCreacion ASC, v.id ASC")
    List<VerificacionDiscapacidad> findCola(@Param("estado") VerificacionEstado estado, Limit limit);

    @Query("SELECT v FROM VerificacionDiscapacidad v JOIN FETCH v.visitante WHERE v.id = :id")
    Optional<VerificacionDiscapacidad> findConVisitante(@Param("id") UUID id);

    /** Verificaciones cuya vigencia termino antes de hoy. */
    @Query("SELECT v FROM VerificacionDiscapacidad v JOIN FETCH v.visitante "
            + "WHERE v.estado = com.aparcar.api.entity.discapacidad.VerificacionEstado.VERIFICADA "
            + "AND v.venceEl IS NOT NULL AND v.venceEl < :hoy")
    List<VerificacionDiscapacidad> findVencidas(@Param("hoy") LocalDate hoy);

    /** En esos estados, con el archivo todavia guardado, cerradas antes del limite. */
    @Query("SELECT v FROM VerificacionDiscapacidad v "
            + "WHERE v.estado IN :estados AND v.documentoEliminadoAt IS NULL "
            + "AND COALESCE(v.revocadaAt, v.resueltaAt) < :limite")
    List<VerificacionDiscapacidad> findDocumentosARetirar(@Param("estados") Collection<VerificacionEstado> estados,
                                                         @Param("limite") Instant limite);
}
