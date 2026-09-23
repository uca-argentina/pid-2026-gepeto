package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {
    boolean existsByCocheraIdAndFechaAndEstado(UUID cocheraId, LocalDate fecha, ReservaEstado estado);

    List<Reserva> findByFechaAndEstado(LocalDate fecha, ReservaEstado estado);

    boolean existsByCocheraId(UUID cocheraId);

    List<Reserva> findByCocheraIdAndEstado(UUID cocheraId, ReservaEstado estado);

    boolean existsByVehiculoId(UUID vehiculoId);

    boolean existsByVisitanteId(UUID visitanteId);

    // Se sobreescriben findAll/findByVisitanteEmail con JOIN FETCH: el unico
    // lugar donde se usan (ReservaService.listar) siempre necesita visitante,
    // vehiculo y cochera juntos para armar el DTO, asi que evitamos el N+1
    // sin importar el fetch por defecto de Hibernate para @ManyToOne.

    @Override
    @Query("SELECT r FROM Reserva r JOIN FETCH r.visitante JOIN FETCH r.vehiculo JOIN FETCH r.cochera")
    List<Reserva> findAll();

    @Query("SELECT r FROM Reserva r JOIN FETCH r.visitante JOIN FETCH r.vehiculo JOIN FETCH r.cochera "
            + "WHERE r.visitante.email = :email")
    List<Reserva> findByVisitanteEmail(@Param("email") String email);
}