package com.aparcar.api.repository;

import com.aparcar.api.entity.puntos.BilleteraPuntos;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BilleteraPuntosRepository extends JpaRepository<BilleteraPuntos, UUID> {
    Optional<BilleteraPuntos> findByVisitanteId(UUID visitanteId);

    boolean existsByVisitanteId(UUID visitanteId);

    /**
     * La billetera bloqueada para escribir: dos operaciones sobre los mismos
     * puntos (dos canjes, un canje y un ingreso) se ordenan, y la segunda ve el
     * saldo que dejo la primera.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BilleteraPuntos b WHERE b.visitanteId = :visitanteId")
    Optional<BilleteraPuntos> findParaActualizar(@Param("visitanteId") UUID visitanteId);

    /** Rachas vigentes: el ultimo dia contado fue hoy o ayer. */
    long countByRachaActualGreaterThanAndUltimoDiaRachaGreaterThanEqual(int racha, LocalDate desde);

    @Query("SELECT COALESCE(MAX(b.mejorRacha), 0) FROM BilleteraPuntos b")
    int maximaMejorRacha();
}
