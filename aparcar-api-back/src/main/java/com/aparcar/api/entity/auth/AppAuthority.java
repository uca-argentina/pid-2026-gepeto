package com.aparcar.api.entity.auth;

/**
 * Los tres roles del sistema.
 *
 * <p>Se guardan como texto en la tabla {@code visitante_authorities}, asi que
 * sumar un valor no necesita migracion.
 *
 * <ul>
 *   <li>{@code USER}: reserva para si mismo y ve solo lo suyo.
 *   <li>{@code EMPLEADO}: opera el estacionamiento. Ve y gestiona las reservas
 *       de todos y controla el ingreso y egreso en la puerta, pero no toca la
 *       configuracion: ni tarifas, ni cuentas, ni el estado de las cocheras.
 *   <li>{@code ADMIN}: todo lo anterior mas la configuracion.
 * </ul>
 */
public enum AppAuthority {
    USER,
    EMPLEADO,
    ADMIN
}
