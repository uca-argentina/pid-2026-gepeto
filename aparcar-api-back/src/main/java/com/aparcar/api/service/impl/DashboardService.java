package com.aparcar.api.service.impl;

import com.aparcar.api.dto.dashboard.DashboardResponseDto;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.ActividadHora;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.Ocupacion;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.Operacion;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.ProximaReserva;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.ReservasHoy;
import com.aparcar.api.dto.dashboard.DashboardResponseDto.Sector;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.DashboardRepository;
import com.aparcar.api.repository.DashboardRepository.CocheraFila;
import com.aparcar.api.repository.DashboardRepository.ReservaFila;
import com.aparcar.api.service.IDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class DashboardService implements IDashboardService {
    private final DashboardRepository dashboardRepository;
    private final ReglasAcceso reglasAcceso;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DashboardResponseDto obtener() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        LocalDateTime inicioDia = ahora.toLocalDate().atStartOfDay();
        LocalDateTime finDia = inicioDia.plusDays(1);
        LocalDateTime horizonte = ahora.plusHours(2);
        List<CocheraFila> cocheras = dashboardRepository.cocheras();
        List<ReservaFila> reservas = dashboardRepository.reservasRelevantes(inicioDia, finDia, ahora, horizonte);

        Map<UUID, CocheraFila> porId = new HashMap<>();
        Map<String, List<CocheraFila>> porSector = new HashMap<>();
        for (CocheraFila cochera : cocheras) {
            porId.put(cochera.getId(), cochera);
            porSector.computeIfAbsent(cochera.getSector(), key -> new ArrayList<>()).add(cochera);
        }

        Set<UUID> ocupadas = new HashSet<>();
        Map<ReservaEstado, Integer> estadosHoy = new EnumMap<>(ReservaEstado.class);
        Map<String, Integer> ingresosPorSector = new HashMap<>();
        int[] ingresos = new int[24];
        int[] egresos = new int[24];
        int excedidas = 0;
        List<ReservaFila> proximas = new ArrayList<>();

        for (ReservaFila fila : reservas) {
            // Se reutilizan las mismas ventanas que el acceso y el barrido, sin cambiar entidades.
            Reserva reserva = paraReglas(fila);
            ReservaEstado estado = fila.getEstado();
            if (estado == ReservaEstado.CONFIRMADA && reglasAcceso.ingresoVencido(reserva, ahora)) {
                estado = ReservaEstado.VENCIDA;
            }
            if (enIntervalo(fila.getDesde(), inicioDia, finDia)) {
                estadosHoy.merge(estado, 1, Integer::sum);
            }
            if (estado == ReservaEstado.EN_CURSO) {
                ocupadas.add(fila.getCocheraId());
                if (reglasAcceso.minutosExceso(reserva, ahora) != null) excedidas++;
            }
            if (ocurrioHoy(fila.getIngresoAt(), inicioDia, ahora)) {
                ingresos[fila.getIngresoAt().getHour()]++;
                CocheraFila cochera = porId.get(fila.getCocheraId());
                if (cochera != null) ingresosPorSector.merge(cochera.getSector(), 1, Integer::sum);
            }
            if (ocurrioHoy(fila.getEgresoAt(), inicioDia, ahora)) {
                egresos[fila.getEgresoAt().getHour()]++;
            }
            if (estado == ReservaEstado.CONFIRMADA && enIntervalo(fila.getDesde(), ahora, horizonte)) {
                proximas.add(fila);
            }
        }

        Ocupacion ocupacion = ocupacion(cocheras, ocupadas);
        List<Sector> sectores = porSector.entrySet().stream().map(entry -> {
            Ocupacion valores = ocupacion(entry.getValue(), ocupadas);
            return new Sector(entry.getKey(), valores.total(), valores.habilitadas(), valores.ocupadas(),
                    valores.ocupadasHabilitadas(), valores.ocupadasFueraServicio(), valores.libres(),
                    ingresosPorSector.getOrDefault(entry.getKey(), 0), valores.porcentaje());
        }).sorted(Comparator.comparingInt(Sector::ingresosHoy).reversed()
                .thenComparing(Comparator.comparingInt(Sector::ocupadas).reversed())
                .thenComparing(Sector::sector, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Sector::sector)).toList();

        List<ProximaReserva> proximasReservas = proximas.stream()
                .sorted(Comparator.comparing(ReservaFila::getDesde).thenComparing(ReservaFila::getId))
                .limit(5)
                .map(fila -> {
                    CocheraFila cochera = porId.get(fila.getCocheraId());
                    return new ProximaReserva(fila.getId(), fila.getDesde(), fila.getHasta(),
                            cochera.getNumero(), cochera.getSector());
                }).toList();

        return new DashboardResponseDto(ahora.toLocalDate(), ahora, clock.getZone().getId(), ocupacion,
                new ReservasHoy(estadosHoy.values().stream().mapToInt(Integer::intValue).sum(),
                        estadosHoy.getOrDefault(ReservaEstado.CONFIRMADA, 0),
                        estadosHoy.getOrDefault(ReservaEstado.EN_CURSO, 0),
                        estadosHoy.getOrDefault(ReservaEstado.FINALIZADA, 0),
                        estadosHoy.getOrDefault(ReservaEstado.CANCELADA, 0),
                        estadosHoy.getOrDefault(ReservaEstado.VENCIDA, 0)),
                new Operacion(IntStream.of(ingresos).sum(), IntStream.of(egresos).sum(), excedidas, proximas.size()),
                IntStream.range(0, 24).mapToObj(hora -> new ActividadHora(hora, ingresos[hora], egresos[hora])).toList(),
                sectores, proximasReservas);
    }

    private static Ocupacion ocupacion(List<CocheraFila> cocheras, Set<UUID> ocupadas) {
        int habilitadas = 0;
        int ocupadasHabilitadas = 0;
        int ocupadasFueraServicio = 0;
        for (CocheraFila cochera : cocheras) {
            boolean ocupada = ocupadas.contains(cochera.getId());
            if (cochera.getEstado() == CocheraEstado.HABILITADA) {
                habilitadas++;
                if (ocupada) ocupadasHabilitadas++;
            } else if (ocupada) {
                ocupadasFueraServicio++;
            }
        }
        double porcentaje = habilitadas == 0 ? 0 : Math.round(ocupadasHabilitadas * 1000.0 / habilitadas) / 10.0;
        return new Ocupacion(cocheras.size(), habilitadas, cocheras.size() - habilitadas,
                ocupadasHabilitadas + ocupadasFueraServicio, ocupadasHabilitadas, ocupadasFueraServicio,
                habilitadas - ocupadasHabilitadas, porcentaje);
    }

    private static Reserva paraReglas(ReservaFila fila) {
        Reserva reserva = new Reserva();
        reserva.setDesde(fila.getDesde());
        reserva.setHasta(fila.getHasta());
        reserva.setCodigoEmitidoAt(fila.getCodigoEmitidoAt());
        return reserva;
    }

    private static boolean enIntervalo(LocalDateTime momento, LocalDateTime desde, LocalDateTime hasta) {
        return momento != null && !momento.isBefore(desde) && momento.isBefore(hasta);
    }

    private static boolean ocurrioHoy(LocalDateTime momento, LocalDateTime inicioDia, LocalDateTime ahora) {
        return momento != null && !momento.isBefore(inicioDia) && !momento.isAfter(ahora);
    }
}
