package com.aparcar.api.service;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.dto.dashboard.DashboardResponseDto;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.repository.DashboardRepository;
import com.aparcar.api.repository.DashboardRepository.CocheraFila;
import com.aparcar.api.repository.DashboardRepository.ReservaFila;
import com.aparcar.api.service.impl.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTests {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 11, 12, 0);
    private final DashboardRepository repository = mock(DashboardRepository.class);
    private final List<CocheraFila> cocheras = new ArrayList<>();
    private final List<ReservaFila> reservas = new ArrayList<>();
    private DashboardService service;

    @BeforeEach
    void setUp() {
        when(repository.cocheras()).thenReturn(cocheras);
        when(repository.reservasRelevantes(any(), any(), any(), any())).thenReturn(reservas);
        service = conReloj(AHORA);
    }

    @Test
    void predioVacioDevuelveCerosYLasVeinticuatroHoras() {
        DashboardResponseDto dto = service.obtener();
        assertEquals(0, dto.ocupacion().total());
        assertEquals(0, dto.ocupacion().porcentaje());
        assertEquals(0, dto.reservasHoy().total());
        assertEquals(0, dto.operacion().ingresosHoy());
        assertEquals(24, dto.actividadPorHora().size());
        assertTrue(dto.actividadPorHora().stream().allMatch(hora -> hora.ingresos() == 0 && hora.egresos() == 0));
        assertTrue(dto.sectores().isEmpty());
        assertTrue(dto.proximasReservas().isEmpty());
        assertEquals(AHORA.toLocalDate(), dto.fecha());
        assertEquals(AccesoDePrueba.ZONA.getId(), dto.zonaHoraria());
        verify(repository).reservasRelevantes(AHORA.toLocalDate().atStartOfDay(),
                AHORA.toLocalDate().plusDays(1).atStartOfDay(), AHORA, AHORA.plusHours(2));
    }

    @Test
    void ocupacionIncluyeEstadiasAnterioresYCocherasDeshabilitadasSinInflarLibres() {
        CocheraFila habilitada = cochera("PB-01", "Planta Baja", true);
        cochera("PB-02", "Planta Baja", true);
        cochera("PB-03", "Planta Baja", true);
        CocheraFila deshabilitada = cochera("PB-04", "Planta Baja", false);
        reserva(habilitada, ReservaEstado.EN_CURSO, AHORA.minusDays(1), AHORA.minusHours(1));
        // Incluso ante datos inconsistentes, una cochera se cuenta una sola vez.
        reserva(habilitada, ReservaEstado.EN_CURSO, AHORA.minusHours(3), AHORA.plusHours(1));
        reserva(deshabilitada, ReservaEstado.EN_CURSO, AHORA.minusDays(2), AHORA.minusDays(1));

        var dto = service.obtener();
        assertEquals(2, dto.ocupacion().ocupadas());
        assertEquals(1, dto.ocupacion().ocupadasHabilitadas());
        assertEquals(1, dto.ocupacion().ocupadasFueraServicio());
        assertEquals(2, dto.ocupacion().libres());
        assertEquals(33.3, dto.ocupacion().porcentaje());
        assertEquals(2, dto.operacion().permanenciasExcedidas());
        assertEquals(1, dto.reservasHoy().total());
        assertEquals(1, dto.sectores().getFirst().ocupadasFueraServicio());
    }

    @Test
    void noPresentadosUsaToleranciaEmisionDelCodigoYFinExclusivoSinModificarEstado() {
        CocheraFila cochera = cochera("PB-01", "Planta Baja", true);
        ReservaFila vencida = reserva(cochera, ReservaEstado.CONFIRMADA, AHORA.minusMinutes(31), AHORA.plusHours(1));
        reserva(cochera, ReservaEstado.CONFIRMADA, AHORA.minusMinutes(30), AHORA.plusHours(1));
        reserva(cochera, ReservaEstado.CONFIRMADA, AHORA.minusMinutes(10), AHORA);
        ReservaFila emitidaTarde = reserva(cochera, ReservaEstado.CONFIRMADA, AHORA.minusHours(2), AHORA.plusHours(1));
        when(emitidaTarde.getCodigoEmitidoAt()).thenReturn(AHORA.minusMinutes(10).atZone(AccesoDePrueba.ZONA).toInstant());
        reserva(cochera, ReservaEstado.VENCIDA, AHORA.minusHours(3), AHORA.minusHours(1));
        reserva(cochera, ReservaEstado.CANCELADA, AHORA.minusHours(3), AHORA.minusHours(1));
        reserva(cochera, ReservaEstado.FINALIZADA, AHORA.minusHours(3), AHORA.minusHours(1));
        reserva(cochera, ReservaEstado.EN_CURSO, AHORA.minusHours(1), AHORA.plusHours(1));
        reserva(cochera, ReservaEstado.VENCIDA, AHORA.minusDays(1), AHORA.minusDays(1).plusHours(1));

        var dto = service.obtener();
        assertEquals(8, dto.reservasHoy().total());
        assertEquals(3, dto.reservasHoy().noPresentados());
        assertEquals(2, dto.reservasHoy().pendientes());
        assertEquals(1, dto.reservasHoy().enCurso());
        assertEquals(1, dto.reservasHoy().canceladas());
        assertEquals(1, dto.reservasHoy().finalizadas());
        assertEquals(ReservaEstado.CONFIRMADA, vencida.getEstado());
        assertEquals(0, dto.ocupacion().libres());
    }

    @Test
    void permanenciaExcedidaRespetaElLimiteExactoDeGracia() {
        CocheraFila cochera = cochera("PB-01", "Planta Baja", true);
        reserva(cochera, ReservaEstado.EN_CURSO, AHORA.minusHours(2), AHORA.minusMinutes(15));
        reserva(cochera, ReservaEstado.EN_CURSO, AHORA.minusHours(2), AHORA.minusMinutes(15).minusSeconds(1));
        reserva(cochera, ReservaEstado.FINALIZADA, AHORA.minusHours(3), AHORA.minusHours(2));

        assertEquals(1, service.obtener().operacion().permanenciasExcedidas());
    }

    @Test
    void actividadCuentaMovimientosRealesDelDiaYNoLaFechaReservada() {
        LocalDateTime medianoche = AHORA.toLocalDate().atStartOfDay();
        CocheraFila cochera = cochera("PB-01", "Planta Baja", true);
        ReservaFila anterior = reserva(cochera, ReservaEstado.FINALIZADA, AHORA.minusDays(1), AHORA);
        when(anterior.getIngresoAt()).thenReturn(medianoche.minusSeconds(1));
        when(anterior.getEgresoAt()).thenReturn(medianoche);
        ReservaFila hoy = reserva(cochera, ReservaEstado.FINALIZADA, medianoche, AHORA);
        when(hoy.getIngresoAt()).thenReturn(medianoche);
        when(hoy.getEgresoAt()).thenReturn(AHORA);
        ReservaFila futuro = reserva(cochera, ReservaEstado.EN_CURSO, AHORA, AHORA.plusHours(1));
        when(futuro.getIngresoAt()).thenReturn(AHORA.plusSeconds(1));

        var dto = service.obtener();
        assertEquals(1, dto.operacion().ingresosHoy());
        assertEquals(2, dto.operacion().egresosHoy());
        assertEquals(1, dto.actividadPorHora().getFirst().ingresos());
        assertEquals(1, dto.actividadPorHora().getFirst().egresos());
        assertEquals(1, dto.actividadPorHora().get(12).egresos());
        assertEquals(1, dto.sectores().getFirst().ingresosHoy());
    }

    @Test
    void proximosIngresosCruzanMedianocheYLimitanListaSinPerderElTotal() {
        LocalDateTime ahora = AHORA.withHour(23);
        service = conReloj(ahora);
        CocheraFila cochera = cochera("PB-01", "Planta Baja", true);
        for (int i = 6; i >= 0; i--) {
            reserva(cochera, ReservaEstado.CONFIRMADA, ahora.plusMinutes(i * 15), ahora.plusHours(3));
        }
        reserva(cochera, ReservaEstado.CONFIRMADA, ahora.minusMinutes(1), ahora.plusHours(1));
        reserva(cochera, ReservaEstado.CONFIRMADA, ahora.plusHours(2), ahora.plusHours(3));
        reserva(cochera, ReservaEstado.CANCELADA, ahora.plusHours(1), ahora.plusHours(2));

        var dto = service.obtener();
        assertEquals(7, dto.operacion().proximosIngresos());
        assertEquals(5, dto.proximasReservas().size());
        assertEquals(ahora, dto.proximasReservas().getFirst().desde());
        assertEquals(ahora.plusHours(1), dto.proximasReservas().getLast().desde());
        assertEquals(5, dto.reservasHoy().total());
        assertEquals(ahora.toLocalDate(), dto.fecha());
    }

    @Test
    void sectoresSeOrdenanPorIngresosYConservanLosQueNoTienenActividad() {
        CocheraFila norte = cochera("N-01", "Norte", true);
        CocheraFila sur = cochera("S-01", "Sur", true);
        cochera("O-01", "Oeste", false);
        reserva(norte, ReservaEstado.EN_CURSO, AHORA.minusDays(1), AHORA.plusHours(1));
        ReservaFila ingreso = reserva(sur, ReservaEstado.FINALIZADA, AHORA.minusHours(2), AHORA.minusHours(1));
        when(ingreso.getIngresoAt()).thenReturn(AHORA.minusHours(2));

        var sectores = service.obtener().sectores();
        assertEquals(List.of("Sur", "Norte", "Oeste"), sectores.stream().map(DashboardResponseDto.Sector::sector).toList());
        assertEquals(0, sectores.getLast().porcentajeOcupacion());
        assertEquals(0, sectores.getLast().libres());
    }

    private DashboardService conReloj(LocalDateTime ahora) {
        Clock clock = AccesoDePrueba.relojEn(ahora);
        return new DashboardService(repository, AccesoDePrueba.reglas(clock), clock);
    }

    private CocheraFila cochera(String numero, String sector, boolean habilitada) {
        CocheraFila fila = mock(CocheraFila.class);
        when(fila.getId()).thenReturn(UUID.randomUUID());
        when(fila.getNumero()).thenReturn(numero);
        when(fila.getSector()).thenReturn(sector);
        when(fila.getEstado()).thenReturn(habilitada ? CocheraEstado.HABILITADA : CocheraEstado.DESHABILITADA);
        cocheras.add(fila);
        return fila;
    }

    private ReservaFila reserva(CocheraFila cochera, ReservaEstado estado, LocalDateTime desde, LocalDateTime hasta) {
        UUID cocheraId = cochera.getId();
        ReservaFila fila = mock(ReservaFila.class);
        when(fila.getId()).thenReturn(UUID.randomUUID());
        when(fila.getCocheraId()).thenReturn(cocheraId);
        when(fila.getEstado()).thenReturn(estado);
        when(fila.getDesde()).thenReturn(desde);
        when(fila.getHasta()).thenReturn(hasta);
        reservas.add(fila);
        return fila;
    }
}
