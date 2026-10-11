package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.AccesoIntento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AccesoIntentoRepository extends JpaRepository<AccesoIntento, UUID> {
}
