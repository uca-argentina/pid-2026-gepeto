package com.aparcar.api.repository;

import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfiguracionSancionesRepository
        extends JpaRepository<ConfiguracionSanciones, Integer> {
}
