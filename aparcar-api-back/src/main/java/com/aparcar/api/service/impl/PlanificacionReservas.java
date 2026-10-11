package com.aparcar.api.service.impl;

import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.PlanificacionReservaRepository;
import com.aparcar.api.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;

/** Asignacion atomica para un predio, con prioridad de llegada entre solicitudes compatibles. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class PlanificacionReservas {
    private final PlanificacionReservaRepository planificacionRepository;
    private final ReservaRepository reservaRepository;
    private final CocheraRepository cocheraRepository;
    private final CodigoAcceso codigoAcceso;
    private final ReglasAcceso reglasAcceso;

    /** Siempre antes de leer/modificar reservas, cocheras o tomar otros locks. */
    public void bloquear() {
        if (planificacionRepository.bloquear() == null) {
            throw new IllegalStateException("Falta inicializar la planificacion de reservas.");
        }
    }

    public Cochera disponible(CocheraTipo tipo, Visitante visitante, Vehiculo vehiculo,
                               LocalDateTime desde, LocalDateTime hasta) {
        return cocheraRepository.findByEstado(CocheraEstado.HABILITADA).stream()
                .filter(c -> c.getTipo() == tipo)
                .filter(c -> compatible(c.getTipo(), visitante, vehiculo))
                .sorted(Comparator.comparing(Cochera::getNumero).thenComparing(Cochera::getId))
                .filter(c -> !reservaRepository.existeSolapadaEnCochera(c.getId(), ReservaEstado.CONFIRMADA, desde, hasta))
                .filter(c -> !reservaRepository.existeEnCursoEnCochera(c.getId(), desde, hasta, reglasAcceso.ahora()))
                .findFirst().orElse(null);
    }

    public static boolean compatible(CocheraTipo tipo, Visitante visitante, Vehiculo vehiculo) {
        return tipo == CocheraTipo.ACCESIBLE ? visitante.puedeUsarCocheraAccesible()
                : tipo != null && tipo.name().equals(vehiculo.getTipo().name());
    }

    /** El llamador ya tiene el candado, y las liberaciones pendientes se ven en esta transaccion. */
    public void promover() {
        LocalDateTime ahora = reglasAcceso.ahora();
        for (Reserva reserva : reservaRepository.findByEstadoOrderByFechaCreacionAscIdAsc(ReservaEstado.LISTA_ESPERA)) {
            if (!reserva.getHasta().isAfter(ahora)) {
                reserva.setEstado(ReservaEstado.VENCIDA);
                continue;
            }
            if (reservaRepository.existeSolapadaEnVehiculo(reserva.getVehiculo().getId(),
                    ReservaEstado.CONFIRMADA, reserva.getDesde(), reserva.getHasta())
                    || reservaRepository.existeEnCursoConVehiculo(reserva.getVehiculo().getId(),
                    reserva.getDesde(), reserva.getHasta(), ahora)) {
                continue;
            }
            Cochera cochera = disponible(reserva.getTipoTarifa(), reserva.getVisitante(), reserva.getVehiculo(),
                    reserva.getDesde(), reserva.getHasta());
            if (cochera == null) continue;

            reserva.setCochera(cochera);
            reserva.setEstado(ReservaEstado.CONFIRMADA);
            reserva.setConfirmadaDesdeEsperaAt(reglasAcceso.instante());
            codigoAcceso.asignar(reserva, reglasAcceso.instante());
            // El precio y la categoria fueron acordados al entrar en espera; no se recotizan.
            reservaRepository.saveAndFlush(reserva);
        }
    }
}
