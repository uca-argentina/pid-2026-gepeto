package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.PlanificacionReserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PlanificacionReservaRepository extends JpaRepository<PlanificacionReserva, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PlanificacionReserva p WHERE p.id = 1")
    PlanificacionReserva bloquear();
}
