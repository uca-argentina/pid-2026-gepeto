package com.aparcar.api.service.impl;

import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.sancion.Amonestacion;
import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.AmonestacionRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IAmonestacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El régimen de faltas.
 *
 * <p>Toda falta entra por acá, venga de una persona o de la tarea automática, y
 * por eso el bloqueo se decide en un solo lugar: cada vez que se registra una,
 * se vuelve a contar y se evalúa si corresponde inhabilitar la cuenta. Si el
 * bloqueo se decidiera en el controller o en la pantalla, una falta automática
 * podría no bloquear nunca.
 *
 * <p>Dos conceptos que se parecen y no son lo mismo:
 *
 * <ul>
 *   <li><b>Faltas vigentes</b>: las que caen dentro de la ventana configurada.
 *       Son las que bloquean.
 *   <li><b>Historial</b>: todas. Es permanente y no se borra nunca.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmonestacionService implements IAmonestacionService {

    private final AmonestacionRepository amonestacionRepository;
    private final VisitanteRepository visitanteRepository;
    private final ReservaRepository reservaRepository;
    private final ConfiguracionSancionesService configuracion;

    @Override
    @Transactional
    public Amonestacion aplicarManual(UUID reservaId, String motivo, String autorEmail) {
        if (motivo == null || motivo.isBlank()) {
            throw new ValidationException("Hay que indicar la causa de la amonestación.");
        }

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new NotFoundException("Reserva no encontrada."));
        Visitante autor = visitanteRepository.findByEmail(autorEmail)
                .orElseThrow(() -> new NotFoundException("Cuenta del operador no encontrada."));

        // Una persona no puede amonestarse a sí misma: sería la forma más
        // simple de inflar el contador de otro o de blanquear el propio.
        if (reserva.getVisitante().getId().equals(autor.getId())) {
            throw new ValidationException("No podés aplicarte una falta a vos mismo.");
        }

        return registrar(reserva.getVisitante(), reserva,
                reserva.getVehiculo() != null ? reserva.getVehiculo().getPatente() : null,
                OrigenAmonestacion.MANUAL, motivo.trim(), autor);
    }

    /**
     * Registra la falta y, si con esta se pasó del máximo, bloquea la cuenta.
     *
     * <p>Lo usan tanto el alta manual como la tarea automática, para que el
     * bloqueo no dependa de por dónde entró la falta.
     */
    @Override
    @Transactional
    public Amonestacion registrar(Visitante visitante, Reserva reserva, String patente,
                                  OrigenAmonestacion origen, String motivo, Visitante autor) {
        LocalDateTime ahora = LocalDateTime.now();
        Amonestacion amonestacion = amonestacionRepository.save(
                new Amonestacion(visitante, reserva, patente, origen, motivo, autor, ahora));

        ConfiguracionSanciones config = configuracion.obtener();
        long vigentes = contarVigentes(visitante.getId(), ahora);

        if (vigentes > config.getMaxFaltas() && !visitante.estaBloqueado(ahora)) {
            visitante.setBloqueadoHasta(ahora.plusDays(config.getDiasBloqueo()));
            visitanteRepository.save(visitante);
            log.info("Cuenta {} bloqueada hasta {} por acumular {} faltas (máximo {}).",
                    visitante.getEmail(), visitante.getBloqueadoHasta(), vigentes, config.getMaxFaltas());
        }

        return amonestacion;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Amonestacion> historial(UUID visitanteId) {
        return amonestacionRepository.findByVisitanteIdOrderByFechaDesc(visitanteId);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarVigentes(UUID visitanteId, LocalDateTime ahora) {
        return amonestacionRepository.countByVisitanteIdAndFechaAfter(visitanteId, corte(ahora));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Long> contarVigentesDe(List<UUID> visitanteIds) {
        if (visitanteIds == null || visitanteIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> conteo = new HashMap<>();
        for (Object[] fila : amonestacionRepository.contarVigentesPorVisitante(
                visitanteIds, corte(LocalDateTime.now()))) {
            conteo.put((UUID) fila[0], (Long) fila[1]);
        }
        return conteo;
    }

    @Override
    @Transactional
    public void restituir(UUID visitanteId) {
        Visitante visitante = visitanteRepository.findById(visitanteId)
                .orElseThrow(() -> new NotFoundException("Cuenta no encontrada."));

        // Solo se levanta el bloqueo. Las faltas quedan: restituir es dejar
        // usar la cuenta de nuevo, no perdonar lo que pasó. Si además se
        // borraran, la próxima falta volvería a empezar de cero y el límite
        // perdería sentido.
        visitante.setBloqueadoHasta(null);
        visitanteRepository.save(visitante);
        log.info("Cuenta {} restituida a mano.", visitante.getEmail());
    }

    /**
     * El inicio de la ventana en la que una falta todavía cuenta.
     *
     * <p>Se lee de la configuración en cada llamada, no se cachea: cambiar la
     * ventana desde el panel tiene efecto sobre el próximo conteo, sin
     * reiniciar nada.
     */
    private LocalDateTime corte(LocalDateTime ahora) {
        return ahora.minusMonths(configuracion.obtener().getMesesVentana());
    }
}
