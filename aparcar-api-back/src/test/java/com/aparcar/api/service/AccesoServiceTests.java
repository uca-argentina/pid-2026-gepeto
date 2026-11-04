package com.aparcar.api.service;

import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.acceso.AccesoAccion;
import com.aparcar.api.dto.acceso.AccesoConsultaResponseDto;
import com.aparcar.api.dto.acceso.AccesoMotivo;
import com.aparcar.api.dto.acceso.AccesoMovimientoResponseDto;
import com.aparcar.api.dto.acceso.AccesoRegistroResponseDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.AccesoOperacion;
import com.aparcar.api.entity.reserva.Cochera;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.entity.reserva.ReservaAccion;
import com.aparcar.api.entity.reserva.ReservaEstado;
import com.aparcar.api.entity.reserva.ReservaMovimiento;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.CocheraRepository;
import com.aparcar.api.repository.ReservaMovimientoRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.AccesoService;
import com.aparcar.api.service.impl.RegistroIntentosAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.springframework.data.domain.Limit;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca del control de acceso, con reloj fijo y los valores por
 * defecto de las reglas. Reserva de referencia: de 15:00 a 17:00 del
 * 10/10/2026, codigo emitido a las 14:00, asi que la ventana de ingreso va de
 * 14:45 a 15:30.
 */
@UnitTests
class AccesoServiceTests {
    private static final String CODIGO = "ABCDE12345";
    private static final String OPERADOR = "operador@test.com";
    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 10, 10, 15, 0);
    private static final LocalDateTime HASTA = DESDE.plusHours(2);

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private com.aparcar.api.service.impl.PlanificacionReservas planificacion;

    @Mock
    private ReservaMovimientoRepository reservaMovimientoRepository;

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private VehiculoRepository vehiculoRepository;

    @Mock
    private CocheraRepository cocheraRepository;

    @Mock
    private RegistroIntentosAcceso registroIntentos;

    @Mock
    private com.aparcar.api.service.IPuntosService puntosService;

    private Reserva reserva;
    private Visitante operador;

    @BeforeEach
    void setUp() {
        Visitante visitante = new Visitante();
        visitante.setId(UUID.randomUUID());
        visitante.setNombre("Juan Perez");
        visitante.setEmail("juan@test.com");

        Vehiculo vehiculo = new Vehiculo();
        vehiculo.setId(UUID.randomUUID());
        vehiculo.setPatente("AB123CD");
        vehiculo.setTipo(VehiculoTipo.AUTO);

        Cochera cochera = new Cochera();
        cochera.setId(UUID.randomUUID());
        cochera.setNumero("A-01");
        cochera.setSector("Planta Baja");
        cochera.setTipo(CocheraTipo.AUTO);
        cochera.setEstado(CocheraEstado.HABILITADA);

        reserva = new Reserva();
        reserva.setId(UUID.randomUUID());
        reserva.setCodigo(CODIGO);
        reserva.setCodigoEmitidoAt(DESDE.minusHours(1).atZone(AccesoDePrueba.ZONA).toInstant());
        reserva.setDesde(DESDE);
        reserva.setHasta(HASTA);
        reserva.setVisitante(visitante);
        reserva.setVehiculo(vehiculo);
        reserva.setCochera(cochera);
        reserva.setEstado(ReservaEstado.CONFIRMADA);

        operador = new Visitante();
        operador.setId(UUID.randomUUID());
        operador.setNombre("Ana Operadora");
        operador.setEmail(OPERADOR);

        lenient().when(reservaRepository.findByCodigo(CODIGO)).thenReturn(Optional.of(reserva));
        lenient().when(visitanteRepository.findByEmail(OPERADOR)).thenReturn(Optional.of(operador));
        lenient().when(vehiculoRepository.findByIdForUpdate(vehiculo.getId())).thenReturn(Optional.of(vehiculo));
        lenient().when(cocheraRepository.findByIdForUpdate(cochera.getId())).thenReturn(Optional.of(cochera));
        lenient().when(reservaRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    private AccesoService servicioA(LocalDateTime ahora) {
        return new AccesoService(reservaRepository, reservaMovimientoRepository, visitanteRepository,
                vehiculoRepository, cocheraRepository,
                AccesoDePrueba.reglas(AccesoDePrueba.relojEn(ahora)), registroIntentos, planificacion, puntosService);
    }

    private void enCurso(LocalDateTime ingreso) {
        reserva.setEstado(ReservaEstado.EN_CURSO);
        reserva.setIngresoAt(ingreso);
    }

    private static List<String> motivo(ValidationException e) {
        return e.getErrors();
    }

    // ---- Consulta ----

    @Nested
    @DisplayName("consulta")
    class Consulta {
        @Test
        @DisplayName("dentro de la ventana devuelve INGRESO valido con los datos, sin tocar la reserva")
        void confirmadaEnVentanaEsIngreso() {
            AccesoConsultaResponseDto r = servicioA(DESDE.plusMinutes(5)).consultar(CODIGO, OPERADOR);

            assertTrue(r.valido());
            assertEquals(AccesoAccion.INGRESO, r.accion());
            assertNull(r.motivo());
            assertEquals("ABCDE-12345", r.codigo());
            assertEquals("Juan Perez", r.reserva().visitanteNombre());
            assertEquals("AB123CD", r.reserva().patente());
            assertEquals("A-01", r.reserva().cocheraNumero());
            assertEquals("Planta Baja", r.reserva().cocheraSector());
            assertEquals(DESDE.minusMinutes(15), r.reserva().ingresoDesde());
            assertEquals(DESDE.plusMinutes(30), r.reserva().ingresoHasta());
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
            verify(reservaRepository, never()).save(any());
            verify(reservaRepository, never()).saveAndFlush(any());
            verify(registroIntentos).registrar(AccesoOperacion.CONSULTA, CODIGO, reserva.getId(), "OK", OPERADOR);
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"APARCAR:ABCDE12345", "abcde-12345", " ABCDE 12345 "})
        @DisplayName("escanear el QR y tipear el codigo llegan a la misma reserva")
        void qrYTipeoSonLoMismo(String entrada) {
            assertTrue(servicioA(DESDE).consultar(entrada, OPERADOR).valido());
        }

        @Test
        @DisplayName("antes de la ventana informa ANTICIPADO y a que hora abre")
        void anticipada() {
            AccesoConsultaResponseDto r = servicioA(DESDE.minusMinutes(15).minusSeconds(1)).consultar(CODIGO, OPERADOR);

            assertFalse(r.valido());
            assertEquals(AccesoAccion.INGRESO, r.accion());
            assertEquals(AccesoMotivo.ANTICIPADO, r.motivo());
            assertTrue(r.mensaje().contains("14:45"), r.mensaje());
        }

        @Test
        @DisplayName("pasada la tolerancia la deja VENCIDA (evaluacion perezosa) y lo informa")
        void vencidaPerezosa() {
            AccesoConsultaResponseDto r = servicioA(DESDE.plusMinutes(30).plusSeconds(1)).consultar(CODIGO, OPERADOR);

            assertFalse(r.valido());
            assertNull(r.accion());
            assertEquals(AccesoMotivo.VENCIDA, r.motivo());
            assertEquals(ReservaEstado.VENCIDA, reserva.getEstado());
            verify(reservaRepository).save(reserva);
            verify(registroIntentos).registrar(AccesoOperacion.CONSULTA, CODIGO, reserva.getId(), "VENCIDA", OPERADOR);
        }

        @Test
        @DisplayName("con el vehiculo adentro devuelve EGRESO")
        void enCursoEsEgreso() {
            enCurso(DESDE);

            AccesoConsultaResponseDto r = servicioA(DESDE.plusHours(1)).consultar(CODIGO, OPERADOR);

            assertTrue(r.valido());
            assertEquals(AccesoAccion.EGRESO, r.accion());
            assertNull(r.reserva().minutosExceso());
        }

        @Test
        @DisplayName("si el vehiculo se paso de hora, la consulta muestra cuanto")
        void enCursoExcedido() {
            enCurso(DESDE);

            AccesoConsultaResponseDto r = servicioA(HASTA.plusMinutes(25)).consultar(CODIGO, OPERADOR);

            assertTrue(r.valido());
            assertEquals(25, r.reserva().minutosExceso());
            assertTrue(r.mensaje().contains("25 minutos"), r.mensaje());
        }

        @Test
        @DisplayName("un segundo escaneo enseguida del ingreso no habilita el egreso")
        void permanenciaMinima() {
            enCurso(DESDE);

            AccesoConsultaResponseDto r = servicioA(DESDE.plusSeconds(10)).consultar(CODIGO, OPERADOR);

            assertFalse(r.valido());
            assertEquals(AccesoAccion.EGRESO, r.accion());
            assertEquals(AccesoMotivo.PERMANENCIA_MINIMA, r.motivo());
        }

        @Test
        @DisplayName("una reserva FINALIZADA informa que el codigo ya se uso")
        void finalizada() {
            reserva.setEstado(ReservaEstado.FINALIZADA);
            reserva.setIngresoAt(DESDE);
            reserva.setEgresoAt(DESDE.plusHours(1));

            AccesoConsultaResponseDto r = servicioA(DESDE.plusHours(1).plusMinutes(10)).consultar(CODIGO, OPERADOR);

            assertFalse(r.valido());
            assertNull(r.accion());
            assertEquals(AccesoMotivo.YA_UTILIZADA, r.motivo());
            assertTrue(r.mensaje().contains("15:00") && r.mensaje().contains("16:00"), r.mensaje());
        }

        @Test
        @DisplayName("una reserva CANCELADA tiene el codigo invalido")
        void cancelada() {
            reserva.setEstado(ReservaEstado.CANCELADA);

            AccesoConsultaResponseDto r = servicioA(DESDE).consultar(CODIGO, OPERADOR);

            assertFalse(r.valido());
            assertEquals(AccesoMotivo.CANCELADA, r.motivo());
        }

        @Test
        @DisplayName("un formato invalido es 400 y queda registrado")
        void formatoInvalido() {
            assertThrows(ValidationException.class, () -> servicioA(DESDE).consultar("ABC", OPERADOR));

            verify(registroIntentos).registrar(AccesoOperacion.CONSULTA, "ABC", null, "FORMATO_INVALIDO", OPERADOR);
            verify(reservaRepository, never()).findByCodigo(anyString());
        }

        @Test
        @DisplayName("un codigo inexistente es 404 y queda registrado")
        void inexistente() {
            when(reservaRepository.findByCodigo("ZZZZZ99999")).thenReturn(Optional.empty());

            NotFoundException e = assertThrows(NotFoundException.class,
                    () -> servicioA(DESDE).consultar("zzzzz-99999", OPERADOR));

            assertTrue(e.getMessage().contains("ZZZZZ-99999"));
            verify(registroIntentos).registrar(AccesoOperacion.CONSULTA, "ZZZZZ99999", null, "INEXISTENTE", OPERADOR);
        }
    }

    // ---- Ingreso ----

    @Nested
    @DisplayName("registro de ingreso")
    class Ingreso {
        @Test
        @DisplayName("pasa a EN_CURSO, guarda la hora y deja el movimiento con el operador")
        void registraElIngreso() {
            LocalDateTime ahora = DESDE.plusMinutes(10);

            AccesoRegistroResponseDto r = servicioA(ahora).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR);

            assertEquals(AccesoAccion.INGRESO, r.accion());
            assertFalse(r.yaRegistrado());
            assertEquals(ahora, r.fecha());
            assertEquals(ReservaEstado.EN_CURSO, reserva.getEstado());
            assertEquals(ahora, reserva.getIngresoAt());
            ReservaMovimiento movimiento = reserva.getHistorial().getLast();
            assertEquals(ReservaAccion.INGRESO, movimiento.getAccion());
            assertEquals("Ana Operadora", movimiento.getActorNombre());
            assertEquals(AppAuthority.ADMIN, movimiento.getActorRol());
            assertEquals(ahora.atZone(AccesoDePrueba.ZONA).toInstant(), movimiento.getFecha());
            verify(reservaRepository).saveAndFlush(reserva);
            verify(registroIntentos).registrar(AccesoOperacion.INGRESO, CODIGO, reserva.getId(), "OK", OPERADOR);
        }

        @Test
        @DisplayName("justo en la apertura de la ventana (desde - 15) se acepta")
        void justoEnLaApertura() {
            servicioA(DESDE.minusMinutes(15)).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR);

            assertEquals(ReservaEstado.EN_CURSO, reserva.getEstado());
        }

        @Test
        @DisplayName("justo en el limite de la tolerancia (desde + 30) se acepta")
        void justoEnElLimite() {
            servicioA(DESDE.plusMinutes(30)).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR);

            assertEquals(ReservaEstado.EN_CURSO, reserva.getEstado());
        }

        @Test
        @DisplayName("un segundo antes de la ventana se rechaza por ANTICIPADO, sin cambios")
        void anticipado() {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE.minusMinutes(15).minusSeconds(1))
                            .registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            assertEquals(List.of("ANTICIPADO"), motivo(e));
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
            verify(reservaRepository, never()).saveAndFlush(any());
            verify(registroIntentos).registrar(AccesoOperacion.INGRESO, CODIGO, reserva.getId(), "ANTICIPADO", OPERADOR);
        }

        @Test
        @DisplayName("un segundo despues de la tolerancia se rechaza y la reserva queda VENCIDA")
        void despuesDeLaTolerancia() {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE.plusMinutes(30).plusSeconds(1))
                            .registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            assertEquals(List.of("VENCIDA"), motivo(e));
            assertEquals(ReservaEstado.VENCIDA, reserva.getEstado());
            assertNull(reserva.getIngresoAt());
            verify(reservaRepository).save(reserva);
        }

        @Test
        @DisplayName("repetirlo dentro de 2 minutos (doble clic) devuelve lo registrado, sin registrar dos veces")
        void dobleClic() {
            enCurso(DESDE);
            int movimientos = reserva.getHistorial().size();

            AccesoRegistroResponseDto r = servicioA(DESDE.plusSeconds(90))
                    .registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR);

            assertTrue(r.yaRegistrado());
            assertEquals(DESDE, r.fecha());
            assertEquals(movimientos, reserva.getHistorial().size());
            verify(reservaRepository, never()).saveAndFlush(any());
            verify(registroIntentos).registrar(AccesoOperacion.INGRESO, CODIGO, reserva.getId(), "YA_REGISTRADO", OPERADOR);
        }

        @Test
        @DisplayName("repetirlo mas tarde se rechaza: el codigo ahora solo sirve para el egreso")
        void ingresoYaRegistrado() {
            enCurso(DESDE);

            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE.plusMinutes(5)).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            assertEquals(List.of("INGRESO_YA_REGISTRADO"), motivo(e));
        }

        @Test
        @DisplayName("una reserva cancelada no puede ingresar")
        void cancelada() {
            reserva.setEstado(ReservaEstado.CANCELADA);

            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            assertEquals(List.of("CANCELADA"), motivo(e));
        }

        @Test
        @DisplayName("una reserva VENCIDA por el barrido no puede ingresar")
        void vencidaPorBarrido() {
            reserva.setEstado(ReservaEstado.VENCIDA);

            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            assertEquals(List.of("VENCIDA"), motivo(e));
        }

        @Test
        @DisplayName("un conflicto de version (otro operador a la vez) se registra y se propaga para el 409")
        void conflicto() {
            when(reservaRepository.saveAndFlush(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Reserva.class, reserva.getId()));

            assertThrows(ObjectOptimisticLockingFailureException.class,
                    () -> servicioA(DESDE).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));

            verify(registroIntentos).registrar(AccesoOperacion.INGRESO, CODIGO, reserva.getId(), "CONFLICTO", OPERADOR);
        }
    }

    // ---- Egreso ----

    @Nested
    @DisplayName("registro de egreso")
    class Egreso {
        @Test
        @DisplayName("sin ingreso previo se rechaza")
        void sinIngreso() {
            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE.plusMinutes(5)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR));

            assertEquals(List.of("SIN_INGRESO"), motivo(e));
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
        }

        @Test
        @DisplayName("antes de la permanencia minima (59 s) se rechaza")
        void antesDeLaPermanenciaMinima() {
            enCurso(DESDE);

            ValidationException e = assertThrows(ValidationException.class,
                    () -> servicioA(DESDE.plusSeconds(59)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR));

            assertEquals(List.of("PERMANENCIA_MINIMA"), motivo(e));
            assertEquals(ReservaEstado.EN_CURSO, reserva.getEstado());
        }

        @Test
        @DisplayName("cumplida la permanencia minima pasa a FINALIZADA y libera la cochera")
        void registraElEgreso() {
            enCurso(DESDE);
            LocalDateTime ahora = DESDE.plusSeconds(60);

            AccesoRegistroResponseDto r = servicioA(ahora).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);

            assertEquals(AccesoAccion.EGRESO, r.accion());
            assertFalse(r.excedido());
            assertNull(r.minutosExceso());
            assertEquals(ReservaEstado.FINALIZADA, reserva.getEstado());
            assertEquals(ahora, reserva.getEgresoAt());
            assertEquals(ReservaAccion.EGRESO, reserva.getHistorial().getLast().getAccion());
            verify(reservaRepository).saveAndFlush(reserva);
        }

        @Test
        @DisplayName("dentro de la gracia (hasta + 15) no se marca exceso")
        void dentroDeLaGracia() {
            enCurso(DESDE);

            AccesoRegistroResponseDto r = servicioA(HASTA.plusMinutes(15)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);

            assertFalse(r.excedido());
            assertNull(reserva.getMinutosExceso());
        }

        @Test
        @DisplayName("despues de la gracia se registra igual, marcado como excedido con los minutos")
        void excedido() {
            enCurso(DESDE);

            AccesoRegistroResponseDto r = servicioA(HASTA.plusMinutes(40)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);

            assertTrue(r.excedido());
            assertEquals(40, r.minutosExceso());
            assertEquals(40, reserva.getMinutosExceso());
            assertEquals(ReservaEstado.FINALIZADA, reserva.getEstado());
        }

        @Test
        @DisplayName("repetirlo dentro de 2 minutos devuelve lo registrado")
        void dobleClic() {
            enCurso(DESDE);
            servicioA(HASTA).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);

            AccesoRegistroResponseDto r = servicioA(HASTA.plusSeconds(30)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);

            assertTrue(r.yaRegistrado());
            assertEquals(HASTA, r.fecha());
            verify(reservaRepository).saveAndFlush(reserva);
        }

        @Test
        @DisplayName("uso unico: ingreso, egreso, y despues el codigo no sirve para nada")
        void usoUnico() {
            servicioA(DESDE).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR);
            servicioA(DESDE.plusHours(1)).registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR);
            AccesoService despues = servicioA(DESDE.plusHours(1).plusMinutes(5));

            ValidationException otroIngreso = assertThrows(ValidationException.class,
                    () -> despues.registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));
            ValidationException otroEgreso = assertThrows(ValidationException.class,
                    () -> despues.registrar(CODIGO, AccesoAccion.EGRESO, OPERADOR));

            assertEquals(List.of("YA_UTILIZADA"), motivo(otroIngreso));
            assertEquals(List.of("YA_UTILIZADA"), motivo(otroEgreso));
            assertEquals(2, reserva.getHistorial().size());
        }

        @Test
        @DisplayName("un operador sin cuenta no puede registrar")
        void operadorInexistente() {
            assertThrows(NotFoundException.class,
                    () -> servicioA(DESDE).registrar(CODIGO, AccesoAccion.INGRESO, "nadie@test.com"));
            assertEquals(ReservaEstado.CONFIRMADA, reserva.getEstado());
        }
    }

    // ---- Movimientos ----

    @Test
    @DisplayName("movimientos del dia pide ingresos y egresos desde la medianoche del predio")
    void movimientosDelDia() {
        reserva.setMinutosExceso(12);
        reserva.registrarAccion(ReservaAccion.EGRESO, operador, true, DESDE.atZone(AccesoDePrueba.ZONA).toInstant());
        ReservaMovimiento egreso = reserva.getHistorial().getLast();
        when(reservaMovimientoRepository.findRecientes(eq(List.of(ReservaAccion.INGRESO, ReservaAccion.EGRESO)),
                eq(LocalDateTime.of(2026, 10, 10, 0, 0).atZone(AccesoDePrueba.ZONA).toInstant()), any(Limit.class)))
                .thenReturn(List.of(egreso));

        List<AccesoMovimientoResponseDto> movimientos = servicioA(DESDE).movimientosDelDia();

        assertEquals(1, movimientos.size());
        AccesoMovimientoResponseDto m = movimientos.getFirst();
        assertEquals(AccesoAccion.EGRESO, m.accion());
        assertEquals("AB123CD", m.patente());
        assertEquals("A-01", m.cocheraNumero());
        assertEquals("Ana Operadora", m.operador());
        assertEquals(12, m.minutosExceso());
        verify(registroIntentos, never()).registrar(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("un rechazo no confia en una consulta previa: la regla se evalua al registrar")
    void noConfiaEnLaConsulta() {
        // La consulta a las 15:30 dice que se puede; el registro llega 1 s tarde.
        assertTrue(servicioA(DESDE.plusMinutes(30)).consultar(CODIGO, OPERADOR).valido());

        assertThrows(ValidationException.class,
                () -> servicioA(DESDE.plusMinutes(30).plusSeconds(1)).registrar(CODIGO, AccesoAccion.INGRESO, OPERADOR));
        verify(registroIntentos).registrar(eq(AccesoOperacion.INGRESO), eq(CODIGO), eq(reserva.getId()),
                eq("VENCIDA"), eq(OPERADOR));
        verify(registroIntentos, never()).registrar(any(), any(), isNull(), eq("OK"), any());
    }
}
