package com.aparcar.api.repository;

import com.aparcar.api.entity.auth.Visitante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Repositorio de {@link Visitante}, que es a la vez la cuenta de login y la
 * persona que reserva.
 */
@Repository
public interface VisitanteRepository extends JpaRepository<Visitante, UUID> {
    @Query("SELECT v FROM Visitante v WHERE LOWER(TRIM(v.email)) = LOWER(TRIM(:email))")
    Optional<Visitante> findByEmail(String email);

    @Query("SELECT COUNT(v) > 0 FROM Visitante v WHERE LOWER(TRIM(v.email)) = LOWER(TRIM(:email))")
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(v) > 0 FROM Visitante v WHERE TRIM(v.documento) = TRIM(:documento)")
    boolean existsByDocumento(String documento);

    @Query("SELECT v.email FROM Visitante v WHERE v.isActive = false")
    Set<String> findInactiveEmails();

    // ---- Verificacion de discapacidad ----

    /**
     * La cuenta, bloqueada para escribir: serializa dos subidas simultaneas de
     * la misma persona, para que no queden dos solicitudes en revision.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Visitante v WHERE LOWER(TRIM(v.email)) = LOWER(TRIM(:email))")
    Optional<Visitante> findByEmailParaActualizar(@Param("email") String email);

    /** A quienes avisar de una solicitud nueva. */
    @Query("SELECT DISTINCT v.email FROM Visitante v JOIN v.authorities a "
            + "WHERE a = com.aparcar.api.entity.auth.AppAuthority.ADMIN AND v.isActive = true")
    List<String> findEmailsDeAdminsActivos();

    /** Cuentas con acceso provisorio (migradas) cuyo plazo ya termino. */
    List<Visitante> findByTieneDiscapacidadTrueAndDiscapacidadAccesoProvisorioHastaBefore(Instant momento);

    /**
     * Cuentas que se autodeclararon antes de la verificacion: acceso sin
     * ninguna solicitud aprobada y sin plazo de gracia todavia.
     */
    @Query("SELECT v FROM Visitante v WHERE v.tieneDiscapacidad = true "
            + "AND v.discapacidadAccesoProvisorioHasta IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM VerificacionDiscapacidad s WHERE s.visitante = v "
            + "AND s.estado = com.aparcar.api.entity.discapacidad.VerificacionEstado.VERIFICADA)")
    List<Visitante> findAutodeclaradosSinVerificacion();
}
