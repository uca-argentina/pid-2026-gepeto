package com.aparcar.api.repository;

import com.aparcar.api.entity.convenio.Convenio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ConvenioRepository extends JpaRepository<Convenio, UUID> {
    List<Convenio> findAllByOrderByNombreAscIdAsc();
}
