package com.aparcar.api.service;

import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.sancion.Amonestacion;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** El régimen de faltas: aplicarlas, contarlas y levantar el bloqueo. */
public interface IAmonestacionService {

    /** Aplica una falta escrita por una persona, a partir de una reserva. */
    Amonestacion aplicarManual(UUID reservaId, String motivo, String autorEmail);

    /** Registra una falta y bloquea la cuenta si con esta se pasó del máximo. */
    Amonestacion registrar(Visitante visitante, Reserva reserva, String patente,
                           OrigenAmonestacion origen, String motivo, Visitante autor);

    /** El historial completo, permanente, de la más nueva a la más vieja. */
    List<Amonestacion> historial(UUID visitanteId);

    /** Cuántas faltas cuentan hoy para el bloqueo. */
    long contarVigentes(UUID visitanteId, LocalDateTime ahora);

    /** Lo mismo para varias personas, para no consultar una por una. */
    Map<UUID, Long> contarVigentesDe(List<UUID> visitanteIds);

    /** Levanta el bloqueo sin borrar las faltas. */
    void restituir(UUID visitanteId);
}
