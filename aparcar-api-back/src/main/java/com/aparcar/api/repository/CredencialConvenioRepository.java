package com.aparcar.api.repository;

import com.aparcar.api.entity.convenio.CredencialConvenio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredencialConvenioRepository extends JpaRepository<CredencialConvenio, UUID> {
    @EntityGraph(attributePaths = "convenio")
    List<CredencialConvenio> findByVisitanteId(UUID visitanteId);
    Optional<CredencialConvenio> findByVisitanteIdAndConvenioId(UUID visitanteId, UUID convenioId);
    void deleteByVisitanteIdAndConvenioId(UUID visitanteId, UUID convenioId);
    void deleteByConvenioId(UUID convenioId);
    void deleteByVisitanteId(UUID visitanteId);
}
