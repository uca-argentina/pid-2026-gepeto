package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.Vehiculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehiculoRepository extends JpaRepository<Vehiculo, UUID> {
    boolean existsByPatente(String patente);

    List<Vehiculo> findByVisitanteId(UUID visitanteId);

    List<Vehiculo> findByVisitanteEmail(String email);
}
