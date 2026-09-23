package com.aparcar.api.entity.reserva;

/**
 * Estado operativo de la cochera (independiente de la disponibilidad por
 * fecha, que se calcula a partir de las reservas confirmadas).
 */
public enum CocheraEstado {
    HABILITADA,
    DESHABILITADA
}
