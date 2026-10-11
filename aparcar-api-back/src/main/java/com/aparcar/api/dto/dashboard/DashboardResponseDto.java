package com.aparcar.api.dto.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Resumen operativo del predio, sin datos personales ni credenciales de acceso. */
public record DashboardResponseDto(
        LocalDate fecha,
        LocalDateTime actualizadoAt,
        String zonaHoraria,
        Ocupacion ocupacion,
        ReservasHoy reservasHoy,
        Operacion operacion,
        List<ActividadHora> actividadPorHora,
        List<Sector> sectores,
        List<ProximaReserva> proximasReservas
) {
    /** Libres y porcentaje se calculan sobre las habilitadas, sin prometer disponibilidad para reservar. */
    public record Ocupacion(int total, int habilitadas, int fueraServicio, int ocupadas,
                            int ocupadasHabilitadas, int ocupadasFueraServicio, int libres, double porcentaje) {
    }

    /** Reservas cuyo inicio programado cae hoy; los cinco estados suman el total. */
    public record ReservasHoy(int total, int pendientes, int enCurso, int finalizadas,
                              int canceladas, int noPresentados) {
    }

    /** Proximos ingresos: inicios programados en [ahora, ahora + 2 horas). */
    public record Operacion(int ingresosHoy, int egresosHoy, int permanenciasExcedidas, int proximosIngresos) {
    }

    public record ActividadHora(int hora, int ingresos, int egresos) {
    }

    public record Sector(String sector, int total, int habilitadas, int ocupadas,
                          int ocupadasHabilitadas, int ocupadasFueraServicio, int libres,
                          int ingresosHoy, double porcentajeOcupacion) {
    }

    public record ProximaReserva(UUID id, LocalDateTime desde, LocalDateTime hasta,
                                  String cocheraNumero, String sector) {
    }
}
