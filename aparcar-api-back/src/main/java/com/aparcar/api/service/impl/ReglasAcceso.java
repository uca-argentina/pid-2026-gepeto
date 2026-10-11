package com.aparcar.api.service.impl;

import com.aparcar.api.config.AccesoProperties;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ventanas de tiempo del control de acceso, en un solo lugar: las usan la
 * validacion del codigo, el barrido de vencidas y el DTO que ve el visitante.
 *
 * <p>Todo se calcula contra el {@link Clock} inyectado, en hora del predio
 * (las reservas son {@link LocalDateTime}).
 */
@Component
@RequiredArgsConstructor
public class ReglasAcceso {
    private final AccesoProperties properties;
    private final Clock clock;

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    public Instant instante() {
        return clock.instant();
    }

    /** El comienzo del dia de hoy, en hora del predio. */
    public Instant inicioDelDia() {
        return LocalDate.now(clock).atStartOfDay(clock.getZone()).toInstant();
    }

    /** Desde cuando se acepta el ingreso, inclusive. */
    public LocalDateTime inicioIngreso(Reserva reserva) {
        return reserva.getDesde().minusMinutes(properties.ingresoAnticipadoMin());
    }

    /**
     * Ultimo instante, inclusive, de la tolerancia de ingreso tardio. Se cuenta
     * desde el mayor entre {@code desde} y la emision del codigo, asi una
     * reserva hecha sobre la hora (o una anterior a la feature, que recibe su
     * codigo en la migracion) igual tiene su margen completo.
     */
    public LocalDateTime limiteTolerancia(Reserva reserva) {
        LocalDateTime base = reserva.getDesde();
        if (reserva.getCodigoEmitidoAt() != null) {
            LocalDateTime emitido = LocalDateTime.ofInstant(reserva.getCodigoEmitidoAt(), clock.getZone());
            if (emitido.isAfter(base)) base = emitido;
        }
        return base.plusMinutes(properties.ingresoToleranciaMin());
    }

    /** Fin de la ventana de ingreso para mostrar: la tolerancia, sin pasar de {@code hasta}. */
    public LocalDateTime finIngreso(Reserva reserva) {
        LocalDateTime limite = limiteTolerancia(reserva);
        return limite.isBefore(reserva.getHasta()) ? limite : reserva.getHasta();
    }

    public boolean ingresoAnticipado(Reserva reserva, LocalDateTime ahora) {
        return ahora.isBefore(inicioIngreso(reserva));
    }

    /**
     * Paso la tolerancia o termino la franja. {@code hasta} es exclusivo, igual
     * que en el solapamiento: en ese instante la reserva ya no vale.
     */
    public boolean ingresoVencido(Reserva reserva, LocalDateTime ahora) {
        return ahora.isAfter(limiteTolerancia(reserva)) || !ahora.isBefore(reserva.getHasta());
    }

    /**
     * Evaluacion perezosa del no-show: si la reserva sigue CONFIRMADA pero ya
     * se le cerro la ventana, la pasa a VENCIDA. El barrido programado hace lo
     * mismo cada minuto; esto cubre lo que pase entre dos barridos.
     *
     * @return true si cambio el estado.
     */
    public boolean marcarSiVencio(Reserva reserva, LocalDateTime ahora) {
        if (reserva.getEstado() != ReservaEstado.CONFIRMADA || !ingresoVencido(reserva, ahora)) {
            return false;
        }
        reserva.setEstado(ReservaEstado.VENCIDA);
        return true;
    }

    /** True si todavia no paso la permanencia minima desde el ingreso. */
    public boolean egresoPrematuro(Reserva reserva, LocalDateTime ahora) {
        return reserva.getIngresoAt() != null
                && ahora.isBefore(reserva.getIngresoAt().plusSeconds(properties.permanenciaMinimaSeg()));
    }

    /** Segundos que faltan para poder registrar el egreso. */
    public long segundosParaEgreso(Reserva reserva, LocalDateTime ahora) {
        LocalDateTime habilitado = reserva.getIngresoAt().plusSeconds(properties.permanenciaMinimaSeg());
        return Math.max(1, Duration.between(ahora, habilitado).toSeconds());
    }

    /**
     * Minutos de mas, contados desde {@code hasta}, si el egreso cae despues
     * del periodo de gracia; null si salio a tiempo.
     */
    public Integer minutosExceso(Reserva reserva, LocalDateTime egreso) {
        if (!egreso.isAfter(reserva.getHasta().plusMinutes(properties.egresoGraciaMin()))) {
            return null;
        }
        return (int) Duration.between(reserva.getHasta(), egreso).toMinutes();
    }

    /** True si {@code registrado} cae dentro de la ventana de idempotencia. */
    public boolean recienRegistrado(LocalDateTime registrado, LocalDateTime ahora) {
        return registrado != null
                && !ahora.isAfter(registrado.plusSeconds(properties.idempotenciaSeg()));
    }

    public int permanenciaMinimaSeg() {
        return properties.permanenciaMinimaSeg();
    }
}
