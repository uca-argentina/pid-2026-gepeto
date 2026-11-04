package com.aparcar.api.dto.auth;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * @param faltasVigentes Faltas de los ultimos tres meses: las que cuentan para
 *                       el bloqueo y las que el panel dibuja como puntitos.
 * @param maxFaltas      El maximo configurado. Viaja aca para que la tabla no
 *                       tenga que pedirlo aparte y para que los puntitos sean
 *                       tantos como el limite real.
 * @param bloqueadoHasta Cuando se restituye sola la cuenta. Null si no esta
 *                       bloqueada.
 */
public record UserResponseDto(
        UUID id,
        String nombre,
        String documento,
        String email,
        String telefono,
        Set<String> authorities,
        Boolean isActive,
        long faltasVigentes,
        int maxFaltas,
        LocalDateTime bloqueadoHasta
) {
}
