package com.aparcar.api.dto.puntos;

/**
 * Para el administrador: como se mueve la gamificacion.
 *
 * @param emitidos       Puntos acreditados por franja y por racha.
 * @param canjeados      Puntos gastados en descuentos, netos de reintegros.
 * @param enCirculacion  Saldo total de las billeteras.
 * @param usuariosConRacha Cuentas con una racha vigente (ultimo dia hoy o ayer).
 */
public record ResumenPuntosResponseDto(
        long emitidos,
        long canjeados,
        long enCirculacion,
        long usuariosConRacha,
        int mejorRacha
) {
}
