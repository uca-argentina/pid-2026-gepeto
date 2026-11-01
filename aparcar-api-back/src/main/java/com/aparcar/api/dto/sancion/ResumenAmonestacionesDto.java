package com.aparcar.api.dto.sancion;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Todo lo que una pantalla necesita saber sobre las faltas de una persona.
 *
 * <p>Va junto a propósito: el perfil dibuja los puntitos, el aviso de bloqueo y
 * el historial de una sola vez, y partirlo en tres llamadas solo daría lugar a
 * que se vean desincronizados.
 */
@Data
@AllArgsConstructor
public class ResumenAmonestacionesDto {
    /** Las que cuentan hoy para el bloqueo. */
    private long vigentes;
    /** El máximo configurado. Los puntitos son tantos como este número. */
    private int maxFaltas;
    /** La ventana configurada, para que la pantalla no diga "3 meses" fijo. */
    private int mesesVentana;
    private boolean bloqueado;
    /** Cuándo se restituye sola la cuenta. Null si no está bloqueada. */
    private LocalDateTime bloqueadoHasta;
    /** El historial completo, permanente. */
    private List<AmonestacionResponseDto> historial;
}
