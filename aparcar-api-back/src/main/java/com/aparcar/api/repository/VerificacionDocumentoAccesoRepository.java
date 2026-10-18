package com.aparcar.api.repository;

import com.aparcar.api.entity.discapacidad.VerificacionDocumentoAcceso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificacionDocumentoAccesoRepository extends JpaRepository<VerificacionDocumentoAcceso, UUID> {
    List<VerificacionDocumentoAcceso> findByVerificacionIdOrderByFechaAsc(UUID verificacionId);
}
