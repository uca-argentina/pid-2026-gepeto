package com.aparcar.api.service;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.sancion.Amonestacion;
import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.entity.sancion.OrigenAmonestacion;
import com.aparcar.api.repository.AmonestacionRepository;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.AmonestacionService;
import com.aparcar.api.service.impl.ConfiguracionSancionesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca sobre la regla que decide bloquear una cuenta.
 *
 * <p>Es la parte del régimen de faltas que no se puede verificar mirando la
 * pantalla: el bloqueo ocurre como efecto de registrar una falta, y lo que
 * importa es exactamente en cuál se dispara. Un error de uno acá significa o
 * bloquear a alguien que todavía estaba en su derecho, o no bloquear nunca.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmonestacionServiceTests {

    @Mock private AmonestacionRepository amonestacionRepository;
    @Mock private VisitanteRepository visitanteRepository;
    @Mock private ReservaRepository reservaRepository;
    @Mock private ConfiguracionSancionesService configuracion;

    @InjectMocks private AmonestacionService service;

    private Visitante visitante;

    @BeforeEach
    void init() {
        visitante = new Visitante("Juan Perez", "30111222", "juan@test.com", "x", null,
                Set.of(AppAuthority.USER), true);
        visitante.setId(UUID.randomUUID());

        ConfiguracionSanciones config = ConfiguracionSanciones.porDefecto();
        when(configuracion.obtener()).thenReturn(config);
        when(amonestacionRepository.save(any(Amonestacion.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    /** Hace que el repositorio diga que ya hay {@code n} faltas vigentes. */
    private void conFaltasVigentes(long n) {
        when(amonestacionRepository.countByVisitanteIdAndFechaAfter(eq(visitante.getId()), any()))
                .thenReturn(n);
    }

    private void registrarUna() {
        service.registrar(visitante, null, "ABC123", OrigenAmonestacion.MANUAL, "porque sí", null);
    }

    @Nested
    @DisplayName("Cuándo se bloquea la cuenta")
    class Bloqueo {

        @Test
        @DisplayName("Con el máximo justo todavía no se bloquea: el límite es lo tolerado, no lo prohibido")
        void conElMaximoJustoNoBloquea() {
            conFaltasVigentes(3);   // máximo configurado: 3

            registrarUna();

            assertThat(visitante.getBloqueadoHasta()).isNull();
            verify(visitanteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Al superar el máximo sí bloquea")
        void alSuperarElMaximoBloquea() {
            conFaltasVigentes(4);

            registrarUna();

            assertThat(visitante.getBloqueadoHasta()).isNotNull();
            verify(visitanteRepository).save(visitante);
        }

        @Test
        @DisplayName("El bloqueo dura los días configurados")
        void elBloqueoDuraLoConfigurado() {
            conFaltasVigentes(4);
            LocalDateTime antes = LocalDateTime.now();

            registrarUna();

            // 30 días por defecto, con holgura para no depender del reloj.
            assertThat(visitante.getBloqueadoHasta())
                    .isAfter(antes.plusDays(29))
                    .isBefore(antes.plusDays(31));
        }

        @Test
        @DisplayName("Una falta más sobre una cuenta ya bloqueada no corre la fecha")
        void noExtiendeUnBloqueoVigente() {
            LocalDateTime hasta = LocalDateTime.now().plusDays(10);
            visitante.setBloqueadoHasta(hasta);
            conFaltasVigentes(8);

            registrarUna();

            // Si cada falta extendiera el bloqueo, una tarea automática que
            // insista podría dejar una cuenta inhabilitada para siempre.
            assertThat(visitante.getBloqueadoHasta()).isEqualTo(hasta);
        }
    }

    @Nested
    @DisplayName("Qué queda registrado")
    class Registro {

        @Test
        @DisplayName("Una falta del sistema queda sin autor y marcada como automática")
        void laAutomaticaNoTieneAutor() {
            conFaltasVigentes(0);
            ArgumentCaptor<Amonestacion> captor = ArgumentCaptor.forClass(Amonestacion.class);

            service.registrar(visitante, null, "ABC123",
                    OrigenAmonestacion.SIN_INGRESO, "no registró el ingreso", null);

            verify(amonestacionRepository).save(captor.capture());
            assertThat(captor.getValue().getAutor()).isNull();
            assertThat(captor.getValue().esAutomatica()).isTrue();
        }

        @Test
        @DisplayName("Una falta manual guarda el nombre de quien la aplicó, no solo su id")
        void laManualGuardaElNombreDelAutor() {
            conFaltasVigentes(0);
            Visitante operador = new Visitante("Ana Admin", "1", "ana@test.com", "x", null,
                    Set.of(AppAuthority.ADMIN), true);
            ArgumentCaptor<Amonestacion> captor = ArgumentCaptor.forClass(Amonestacion.class);

            service.registrar(visitante, null, "ABC123",
                    OrigenAmonestacion.MANUAL, "mal estacionado", operador);

            verify(amonestacionRepository).save(captor.capture());
            // El nombre va copiado para que el historial siga siendo legible si
            // esa cuenta de operador se elimina.
            assertThat(captor.getValue().getAutorNombre()).isEqualTo("Ana Admin");
        }
    }

    @Nested
    @DisplayName("Restitución")
    class Restitucion {

        @Test
        @DisplayName("Levanta el bloqueo y no borra las faltas")
        void levantaElBloqueoSinBorrarFaltas() {
            visitante.setBloqueadoHasta(LocalDateTime.now().plusDays(10));
            when(visitanteRepository.findById(visitante.getId()))
                    .thenReturn(java.util.Optional.of(visitante));

            service.restituir(visitante.getId());

            assertThat(visitante.getBloqueadoHasta()).isNull();
            // Si además se borraran, la próxima falta empezaría de cero y el
            // límite dejaría de significar algo.
            verify(amonestacionRepository, never()).deleteAll(any());
        }
    }

    @Nested
    @DisplayName("Cuenta bloqueada")
    class CuentaBloqueada {

        @Test
        @DisplayName("Una fecha futura bloquea; una pasada ya no")
        void elBloqueoSeVenceSolo() {
            LocalDateTime ahora = LocalDateTime.now();

            visitante.setBloqueadoHasta(ahora.plusDays(1));
            assertThat(visitante.estaBloqueado(ahora)).isTrue();

            // Sin que nadie ejecute nada: el vencimiento no depende de que la
            // tarea de restitución haya corrido.
            visitante.setBloqueadoHasta(ahora.minusSeconds(1));
            assertThat(visitante.estaBloqueado(ahora)).isFalse();
        }

        @Test
        @DisplayName("Sin fecha no está bloqueada")
        void sinFechaNoEstaBloqueada() {
            visitante.setBloqueadoHasta(null);
            assertThat(visitante.estaBloqueado(LocalDateTime.now())).isFalse();
        }
    }

    @Nested
    @DisplayName("Guardas del alta manual")
    class AltaManual {

        private com.aparcar.api.entity.reserva.Reserva reservaDe(Visitante duenio) {
            var reserva = new com.aparcar.api.entity.reserva.Reserva();
            reserva.setId(UUID.randomUUID());
            reserva.setVisitante(duenio);
            when(reservaRepository.findById(reserva.getId()))
                    .thenReturn(java.util.Optional.of(reserva));
            return reserva;
        }

        @Test
        @DisplayName("Nadie puede amonestarse a sí mismo")
        void nadieSeAmonestaASiMismo() {
            var reserva = reservaDe(visitante);
            when(visitanteRepository.findByEmail(visitante.getEmail()))
                    .thenReturn(java.util.Optional.of(visitante));

            // Sin esta guarda, cualquiera del personal podría inflar el
            // contador de otro o limpiar el propio cargándose faltas a mano.
            assertThatThrownBy(() -> service.aplicarManual(
                    reserva.getId(), "me equivoqué", visitante.getEmail()))
                    .hasMessageContaining("vos mismo");

            verify(amonestacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Una causa vacía se rechaza antes de tocar la base")
        void exigeUnaCausa() {
            assertThatThrownBy(() -> service.aplicarManual(UUID.randomUUID(), "   ", "ana@test.com"))
                    .hasMessageContaining("causa");

            // El motivo es lo único que la persona sancionada va a leer en su
            // historial: una falta sin causa no se puede explicar después.
            verify(reservaRepository, never()).findById(any());
            verify(amonestacionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Con causa y sobre una reserva ajena, se registra")
        void registraLaFaltaSobreOtro() {
            Visitante operador = new Visitante("Ana Admin", "1", "ana@test.com", "x", null,
                    Set.of(AppAuthority.ADMIN), true);
            operador.setId(UUID.randomUUID());
            var reserva = reservaDe(visitante);
            when(visitanteRepository.findByEmail("ana@test.com"))
                    .thenReturn(java.util.Optional.of(operador));
            conFaltasVigentes(0);

            service.aplicarManual(reserva.getId(), "  mal estacionado  ", "ana@test.com");

            ArgumentCaptor<Amonestacion> captor = ArgumentCaptor.forClass(Amonestacion.class);
            verify(amonestacionRepository).save(captor.capture());
            // El texto se guarda recortado: los espacios de los extremos son
            // ruido del formulario, no parte de la causa.
            assertThat(captor.getValue().getMotivo()).isEqualTo("mal estacionado");
        }
    }

    @Nested
    @DisplayName("La ventana de conteo")
    class Ventana {

        /** Deja ver con qué fecha de corte se consultó el repositorio. */
        private LocalDateTime corteUsado() {
            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(amonestacionRepository)
                    .countByVisitanteIdAndFechaAfter(eq(visitante.getId()), captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("Por omisión mira tres meses hacia atrás")
        void porOmisionSonTresMeses() {
            LocalDateTime ahora = LocalDateTime.now();

            service.contarVigentes(visitante.getId(), ahora);

            assertThat(corteUsado())
                    .isBefore(ahora.minusMonths(3).plusMinutes(1))
                    .isAfter(ahora.minusMonths(3).minusMinutes(1));
        }

        @Test
        @DisplayName("Respeta la ventana configurada en vez de un número fijo")
        void respetaLaVentanaConfigurada() {
            ConfiguracionSanciones config = ConfiguracionSanciones.porDefecto();
            config.setMesesVentana(12);
            when(configuracion.obtener()).thenReturn(config);
            LocalDateTime ahora = LocalDateTime.now();

            service.contarVigentes(visitante.getId(), ahora);

            // Si el corte siguiera clavado en tres meses, cambiar la ventana
            // desde el panel no tendría ningún efecto y nadie se enteraría:
            // el conteo simplemente seguiría dando otro número.
            assertThat(corteUsado())
                    .isBefore(ahora.minusMonths(12).plusMinutes(1))
                    .isAfter(ahora.minusMonths(12).minusMinutes(1));
        }

        @Test
        @DisplayName("La ventana no toca el historial, que es permanente")
        void elHistorialNoDependeDeLaVentana() {
            service.historial(visitante.getId());

            // El historial usa la consulta sin recorte de fecha: achicar la
            // ventana perdona faltas para el bloqueo, pero no borra nada de lo
            // que la persona ve en su perfil.
            verify(amonestacionRepository).findByVisitanteIdOrderByFechaDesc(visitante.getId());
            verify(amonestacionRepository, never())
                    .countByVisitanteIdAndFechaAfter(any(), any());
        }
    }
}
