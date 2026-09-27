package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Tarifa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarifaRepository extends JpaRepository<Tarifa, CocheraTipo> {
}
