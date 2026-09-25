package com.aparcar.api.repository;

import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    // ---- Solapamiento de franjas ----
    //
    // Toda la regla de "no se pisan" vive en esta condicion:
    //
    //     r.desde < :hasta AND r.hasta > :desde
    //
    // Es el test estandar de interseccion de intervalos semiabiertos. Cubre los
    // cuatro casos de superposicion (la nueva empieza dentro, termina dentro,
    // contiene a la existente, o esta contenida) y deja pasar el caso borde que
    // queremos permitir: si una termina exactamente cuando la otra empieza, no
    // hay solapamiento y la cochera se puede volver a reservar en ese instante.
    //
    // Que una reserva vencida libere la cochera sola sale de aca: su rango ya
    // no intersecta ningun rango futuro, sin necesidad de que corra ninguna
    // tarea de limpieza.

    /** True si la cochera ya tiene una reserva en ese estado pisando el rango. */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r "
            + "WHERE r.cochera.id = :cocheraId AND r.estado = :estado "
            + "AND r.desde < :hasta AND r.hasta > :desde")
    boolean existeSolapadaEnCochera(@Param("cocheraId") UUID cocheraId,
                                    @Param("estado") ReservaEstado estado,
                                    @Param("desde") LocalDateTime desde,
                                    @Param("hasta") LocalDateTime hasta);

    /**
     * True si el vehiculo ya esta comprometido en otra cochera durante el rango.
     * Un mismo auto no puede estar en dos lugares a la vez.
     */
    @Query("SELECT COUNT(r) > 0 FROM Reserva r "
            + "WHERE r.vehiculo.id = :vehiculoId AND r.estado = :estado "
            + "AND r.desde < :hasta AND r.hasta > :desde")
    boolean existeSolapadaEnVehiculo(@Param("vehiculoId") UUID vehiculoId,
                                     @Param("estado") ReservaEstado estado,
                                     @Param("desde") LocalDateTime desde,
                                     @Param("hasta") LocalDateTime hasta);

    /** Las reservas en ese estado que pisan el rango, sin importar la cochera. */
    @Query("SELECT r FROM Reserva r JOIN FETCH r.cochera "
            + "WHERE r.estado = :estado AND r.desde < :hasta AND r.hasta > :desde")
    List<Reserva> findSolapadas(@Param("estado") ReservaEstado estado,
                                @Param("desde") LocalDateTime desde,
                                @Param("hasta") LocalDateTime hasta);

    /** Reservas ya vencidas que siguen marcadas como vigentes. */
    List<Reserva> findByEstadoAndHastaLessThanEqual(ReservaEstado estado, LocalDateTime momento);

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
