package com.aparcar.api.dto.discapacidad;

import com.aparcar.api.entity.discapacidad.EstadoDiscapacidad;

import java.time.Instant;

/**
 * Lo que ve el visitante en su perfil.
 *
 * @param puedeUsarCocherasAccesibles Si hoy tiene acceso a las ACCESIBLE (verificada o en gracia).
 * @param accesoProvisorioHasta       Para cuentas autodeclaradas antes de la verificacion: hasta
 *                                    cuando conservan el acceso sin certificado. Null para el resto.
 * @param puedeEnviar                 Si puede subir un certificado ahora (no hay uno en revision ni
 *                                    una verificacion vigente).
 * @param documentoMaxMb              Tamaño maximo del archivo, para validarlo antes de subirlo.
 * @param solicitud                   La ultima solicitud, o null si nunca pidio.
 */
public record MiVerificacionResponseDto(
        EstadoDiscapacidad estado,
        boolean puedeUsarCocherasAccesibles,
        Instant accesoProvisorioHasta,
        boolean puedeEnviar,
        int documentoMaxMb,
        VerificacionResponseDto solicitud
) {
}
