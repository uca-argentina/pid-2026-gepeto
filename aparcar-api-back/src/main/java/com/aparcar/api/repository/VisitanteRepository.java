package com.aparcar.api.repository;

import com.aparcar.api.entity.auth.Visitante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
