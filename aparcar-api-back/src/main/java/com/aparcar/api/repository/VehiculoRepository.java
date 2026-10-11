package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.Vehiculo;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, UUID> {
    /** Un vehiculo no puede ingresar simultaneamente con dos reservas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vehiculo v WHERE v.id = :id")
    Optional<Vehiculo> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPatente(String patente);

    List<Vehiculo> findByVisitanteId(UUID visitanteId);

    List<Vehiculo> findByVisitanteEmail(String email);
}
