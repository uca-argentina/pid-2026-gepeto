package com.aparcar.api.service;

import com.aparcar.api.component.IDocumentoStorage;
import com.aparcar.api.component.NotificadorVerificaciones;
import com.aparcar.api.config.AccesoDePrueba;
import com.aparcar.api.config.DiscapacidadDePrueba;
import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.discapacidad.EstadoDiscapacidad;
import com.aparcar.api.entity.discapacidad.VerificacionDiscapacidad;
import com.aparcar.api.entity.discapacidad.VerificacionDocumentoAcceso;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.exception.ArchivoDemasiadoGrandeException;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.TipoArchivoNoPermitidoException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.VerificacionDiscapacidadRepository;
import com.aparcar.api.repository.VerificacionDocumentoAccesoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.ValidadorDocumento;
import com.aparcar.api.service.impl.VerificacionDiscapacidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja blanca de la verificacion de discapacidad, con reloj fijo en el
 * 10/10/2026 a las 10:00 (hora del predio) y las propiedades por defecto:
 * 5 MB, 30 dias de retencion y 30 de gracia.
 */
@UnitTests
class VerificacionDiscapacidadServiceTests {
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final Instant INSTANTE = AHORA.atZone(AccesoDePrueba.ZONA).toInstant();
    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final String USUARIO = "juan@test.com";
    private static final String ADMIN = "admin@test.com";

    @Mock private VerificacionDiscapacidadRepository verificacionRepository;
    @Mock private VerificacionDocumentoAccesoRepository accesoRepository;
    @Mock private VisitanteRepository visitanteRepository;
    @Mock private IDocumentoStorage storage;
    @Mock private NotificadorVerificaciones notificador;

    private final DiscapacidadProperties properties = DiscapacidadDePrueba.propiedades("no-se-usa");
    private Visitante usuario;
    private Visitante admin;

    @BeforeEach
    void setUp() {
        usuario = cuenta("Juan Perez", USUARIO, AppAuthority.USER);
        admin = cuenta("Ana Admin", ADMIN, AppAuthority.ADMIN);
        lenient().when(visitanteRepository.findByEmailParaActualizar(USUARIO)).thenReturn(Optional.of(usuario));
        lenient().when(visitanteRepository.findByEmail(USUARIO)).thenReturn(Optional.of(usuario));
        lenient().when(visitanteRepository.findByEmail(ADMIN)).thenReturn(Optional.of(admin));
        lenient().when(verificacionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(storage.guardar(any(), anyString())).thenReturn("3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.pdf");
    }

    private static Visitante cuenta(String nombre, String email, AppAuthority rol) {
        Visitante visitante = new Visitante(nombre, "doc-" + email, email, "hash", null, java.util.Set.of(rol), true);
        visitante.setId(UUID.randomUUID());
        return visitante;
    }

    private VerificacionDiscapacidadService servicioA(LocalDateTime ahora) {
        return new VerificacionDiscapacidadService(verificacionRepository, accesoRepository, visitanteRepository,
                storage, new ValidadorDocumento(properties), notificador, properties, AccesoDePrueba.relojEn(ahora));
    }

    private VerificacionDiscapacidadService servicio() {
        return servicioA(AHORA);
    }

    private VerificacionDiscapacidad solicitud(VerificacionEstado estado) {
        VerificacionDiscapacidad solicitud = new VerificacionDiscapacidad();
        solicitud.setId(UUID.randomUUID());
        solicitud.setVisitante(usuario);
        solicitud.setEstado(estado);
        solicitud.setFechaCreacion(INSTANTE.minus(1, ChronoUnit.DAYS));
        solicitud.setDocumentoClave("3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.pdf");
        solicitud.setDocumentoNombre("cud.pdf");
        solicitud.setDocumentoTipo("application/pdf");
        solicitud.setDocumentoTamano(100L);
        solicitud.setDocumentoSha256("x".repeat(64));
        lenient().when(verificacionRepository.findConVisitante(solicitud.getId())).thenReturn(Optional.of(solicitud));
        return solicitud;
    }

    private void ultimaDelUsuario(VerificacionDiscapacidad ultima) {
        when(verificacionRepository.findFirstByVisitanteIdOrderByFechaCreacionDescIdDesc(usuario.getId()))
                .thenReturn(Optional.ofNullable(ultima));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("archivo", "CUD.pdf", "application/pdf", DiscapacidadDePrueba.pdf());
    }

    // ---- Subir el certificado ----

    @Nested
    @DisplayName("solicitar")
    class Solicitar {
        @Test
        @DisplayName("crea la solicitud EN_REVISION con los metadatos y avisa a los admins, sin dar acceso todavia")
        void creaEnRevision() {
            ultimaDelUsuario(null);

            var respuesta = servicio().solicitar(USUARIO, pdf());

            ArgumentCaptor<VerificacionDiscapacidad> captor = ArgumentCaptor.forClass(VerificacionDiscapacidad.class);
            verify(verificacionRepository).saveAndFlush(captor.capture());
            VerificacionDiscapacidad guardada = captor.getValue();
            assertEquals(VerificacionEstado.EN_REVISION, guardada.getEstado());
            assertEquals(INSTANTE, guardada.getFechaCreacion());
            assertEquals("3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.pdf", guardada.getDocumentoClave());
            assertEquals("CUD.pdf", guardada.getDocumentoNombre());
            assertEquals("application/pdf", guardada.getDocumentoTipo());
            assertEquals(EstadoDiscapacidad.EN_REVISION, respuesta.estado());
            assertFalse(respuesta.puedeEnviar());
            assertFalse(usuario.getTieneDiscapacidad());
            verify(storage).guardar(DiscapacidadDePrueba.pdf(), "pdf");
            verify(notificador).solicitudNueva();
        }

        @Test
        @DisplayName("una sola solicitud en revision a la vez: la segunda es 409 y no guarda nada")
        void unaSolaEnRevision() {
            ultimaDelUsuario(solicitud(VerificacionEstado.EN_REVISION));

            assertThrows(ConflictoException.class, () -> servicio().solicitar(USUARIO, pdf()));
            verify(storage, never()).guardar(any(), anyString());
        }

        @Test
        @DisplayName("con una verificacion vigente no hace falta otra: 409")
        void conVerificacionVigente() {
            ultimaDelUsuario(solicitud(VerificacionEstado.VERIFICADA));

            assertThrows(ConflictoException.class, () -> servicio().solicitar(USUARIO, pdf()));
        }

        @ParameterizedTest(name = "despues de {0}")
        @EnumSource(value = VerificacionEstado.class, names = {"RECHAZADA", "REVOCADA", "VENCIDA", "CANCELADA"})
        @DisplayName("puede volver a enviar despues de un rechazo, revocacion, vencimiento o cancelacion")
        void reenvio(VerificacionEstado anterior) {
            ultimaDelUsuario(solicitud(anterior));

            assertEquals(EstadoDiscapacidad.EN_REVISION, servicio().solicitar(USUARIO, pdf()).estado());
        }

        @Test
        @DisplayName("si dos subidas llegan a la vez y la base rechaza la segunda, es 409")
        void carreraEnLaBase() {
            ultimaDelUsuario(null);
            when(verificacionRepository.saveAndFlush(any()))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk"));

            assertThrows(ConflictoException.class, () -> servicio().solicitar(USUARIO, pdf()));
        }

        @Test
        @DisplayName("valida el archivo antes de guardarlo: vacio 400, grande 413, firma falsa 415")
        void validaElArchivo() {
            ultimaDelUsuario(null);

            assertThrows(ValidationException.class, () -> servicio().solicitar(USUARIO,
                    new MockMultipartFile("archivo", "c.pdf", "application/pdf", new byte[0])));
            assertThrows(ArchivoDemasiadoGrandeException.class, () -> servicio().solicitar(USUARIO,
                    new MockMultipartFile("archivo", "c.pdf", "application/pdf",
                            DiscapacidadDePrueba.pdfDe(5 * 1024 * 1024 + 1))));
            assertThrows(TipoArchivoNoPermitidoException.class, () -> servicio().solicitar(USUARIO,
                    new MockMultipartFile("archivo", "c.pdf", "application/pdf", DiscapacidadDePrueba.png())));
            verify(storage, never()).guardar(any(), anyString());
        }

        @Test
        @DisplayName("el nombre del cliente nunca es la ruta: con path traversal se guarda saneado")
        void nombreConPathTraversal() {
            ultimaDelUsuario(null);

            servicio().solicitar(USUARIO,
                    new MockMultipartFile("archivo", "../../etc/cud.pdf", "application/pdf", DiscapacidadDePrueba.pdf()));

            ArgumentCaptor<VerificacionDiscapacidad> captor = ArgumentCaptor.forClass(VerificacionDiscapacidad.class);
            verify(verificacionRepository).saveAndFlush(captor.capture());
            assertEquals("cud.pdf", captor.getValue().getDocumentoNombre());
            assertTrue(captor.getValue().getDocumentoClave().matches("[0-9a-f-]{36}\\.pdf"));
        }
    }

    // ---- Resolver ----

    @Nested
    @DisplayName("aprobar")
    class Aprobar {
        @Test
        @DisplayName("pone tieneDiscapacidad en true, guarda quien y cuando, y avisa al visitante")
        void apruebaYHabilita() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            usuario.setDiscapacidadAccesoProvisorioHasta(INSTANTE.plus(5, ChronoUnit.DAYS));

            var respuesta = servicio().aprobar(pendiente.getId(), HOY.plusYears(1), ADMIN);

            assertEquals(VerificacionEstado.VERIFICADA, pendiente.getEstado());
            assertTrue(usuario.getTieneDiscapacidad());
            assertNull(usuario.getDiscapacidadAccesoProvisorioHasta());
            assertEquals(HOY.plusYears(1), pendiente.getVenceEl());
            assertEquals(INSTANTE, pendiente.getResueltaAt());
            assertEquals(admin.getId(), pendiente.getResueltaPorId());
            assertEquals("Ana Admin (admin@test.com)", respuesta.resueltaPor());
            assertEquals("Juan Perez", respuesta.solicitante().nombre());
            verify(notificador).resultado(USUARIO, VerificacionEstado.VERIFICADA, null);
        }

        @Test
        @DisplayName("el vencimiento es opcional, y puede ser hoy mismo; ayer es 400")
        void vencimiento() {
            assertNull(servicio().aprobar(solicitud(VerificacionEstado.EN_REVISION).getId(), null, ADMIN).venceEl());
            assertEquals(HOY, servicio().aprobar(solicitud(VerificacionEstado.EN_REVISION).getId(), HOY, ADMIN).venceEl());
            VerificacionDiscapacidad otra = solicitud(VerificacionEstado.EN_REVISION);
            assertThrows(ValidationException.class, () -> servicio().aprobar(otra.getId(), HOY.minusDays(1), ADMIN));
            assertEquals(VerificacionEstado.EN_REVISION, otra.getEstado());
        }

        @ParameterizedTest(name = "una {0}")
        @EnumSource(value = VerificacionEstado.class, names = "EN_REVISION", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("solo se aprueba una EN_REVISION: resolverla dos veces es 409 y no la procesa de nuevo")
        void soloEnRevision(VerificacionEstado estado) {
            VerificacionDiscapacidad resuelta = solicitud(estado);

            assertThrows(ConflictoException.class, () -> servicio().aprobar(resuelta.getId(), null, ADMIN));
            assertEquals(estado, resuelta.getEstado());
            verify(notificador, never()).resultado(any(), any(), any());
        }

        @Test
        @DisplayName("dos admins a la vez: el segundo choca con la version y recibe 409")
        void dosAdminsALaVez() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            when(verificacionRepository.saveAndFlush(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(VerificacionDiscapacidad.class, pendiente.getId()));

            ConflictoException e = assertThrows(ConflictoException.class,
                    () -> servicio().aprobar(pendiente.getId(), null, ADMIN));
            assertTrue(e.getMessage().contains("Otro administrador"));
        }

        @Test
        @DisplayName("una solicitud inexistente es 404")
        void inexistente() {
            assertThrows(NotFoundException.class, () -> servicio().aprobar(UUID.randomUUID(), null, ADMIN));
        }
    }

    @Nested
    @DisplayName("rechazar y revocar")
    class RechazarYRevocar {
        @Test
        @DisplayName("rechazar deja tieneDiscapacidad en false, guarda el motivo y lo avisa")
        void rechaza() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            usuario.setTieneDiscapacidad(true);
            usuario.setDiscapacidadAccesoProvisorioHasta(INSTANTE.plus(5, ChronoUnit.DAYS));

            servicio().rechazar(pendiente.getId(), "  El certificado está vencido.  ", ADMIN);

            assertEquals(VerificacionEstado.RECHAZADA, pendiente.getEstado());
            assertEquals("El certificado está vencido.", pendiente.getMotivo());
            assertFalse(usuario.getTieneDiscapacidad());
            assertNull(usuario.getDiscapacidadAccesoProvisorioHasta());
            verify(notificador).resultado(USUARIO, VerificacionEstado.RECHAZADA, "El certificado está vencido.");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("el motivo es obligatorio para rechazar y para revocar")
        void motivoObligatorio(String motivo) {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            VerificacionDiscapacidad vigente = solicitud(VerificacionEstado.VERIFICADA);

            assertThrows(ValidationException.class, () -> servicio().rechazar(pendiente.getId(), motivo, ADMIN));
            assertThrows(ValidationException.class, () -> servicio().revocar(vigente.getId(), motivo, ADMIN));
            assertEquals(VerificacionEstado.EN_REVISION, pendiente.getEstado());
            assertEquals(VerificacionEstado.VERIFICADA, vigente.getEstado());
        }

        @Test
        @DisplayName("un motivo de mas de 500 caracteres es 400")
        void motivoLargo() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);

            assertThrows(ValidationException.class, () -> servicio().rechazar(pendiente.getId(), "x".repeat(501), ADMIN));
        }

        @Test
        @DisplayName("rechazar una ya resuelta es 409")
        void rechazarResuelta() {
            VerificacionDiscapacidad aprobada = solicitud(VerificacionEstado.VERIFICADA);

            assertThrows(ConflictoException.class, () -> servicio().rechazar(aprobada.getId(), "motivo", ADMIN));
        }

        @Test
        @DisplayName("revocar una vigente la deja REVOCADA, sin acceso, y conserva quien la habia aprobado")
        void revoca() {
            VerificacionDiscapacidad vigente = solicitud(VerificacionEstado.VERIFICADA);
            vigente.setResueltaPorNombre("Luis Admin");
            vigente.setResueltaPorEmail("luis@test.com");
            usuario.setTieneDiscapacidad(true);

            var respuesta = servicio().revocar(vigente.getId(), "Certificado adulterado.", ADMIN);

            assertEquals(VerificacionEstado.REVOCADA, vigente.getEstado());
            assertFalse(usuario.getTieneDiscapacidad());
            assertEquals(INSTANTE, vigente.getRevocadaAt());
            assertEquals("Luis Admin (luis@test.com)", respuesta.resueltaPor());
            assertEquals("Ana Admin (admin@test.com)", respuesta.revocadaPor());
            verify(notificador).resultado(USUARIO, VerificacionEstado.REVOCADA, "Certificado adulterado.");
        }

        @ParameterizedTest(name = "una {0}")
        @EnumSource(value = VerificacionEstado.class, names = "VERIFICADA", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("solo se revoca una VERIFICADA")
        void soloRevocaVigentes(VerificacionEstado estado) {
            VerificacionDiscapacidad otra = solicitud(estado);

            assertThrows(ConflictoException.class, () -> servicio().revocar(otra.getId(), "motivo", ADMIN));
        }
    }

    // ---- Lo del visitante ----

    @Nested
    @DisplayName("estado y documento del visitante")
    class DelVisitante {
        @Test
        @DisplayName("sin solicitudes: SIN_SOLICITUD, puede enviar")
        void sinSolicitud() {
            ultimaDelUsuario(null);

            var estado = servicio().miEstado(USUARIO);

            assertEquals(EstadoDiscapacidad.SIN_SOLICITUD, estado.estado());
            assertTrue(estado.puedeEnviar());
            assertNull(estado.solicitud());
            assertEquals(5, estado.documentoMaxMb());
        }

        @Test
        @DisplayName("rechazada: muestra el motivo y deja reenviar")
        void rechazadaConMotivo() {
            VerificacionDiscapacidad rechazada = solicitud(VerificacionEstado.RECHAZADA);
            rechazada.setMotivo("No se lee el número de certificado.");
            ultimaDelUsuario(rechazada);

            var estado = servicio().miEstado(USUARIO);

            assertEquals(EstadoDiscapacidad.RECHAZADA, estado.estado());
            assertEquals("No se lee el número de certificado.", estado.solicitud().motivo());
            assertTrue(estado.puedeEnviar());
            // El visitante no ve quien resolvio ni sus propios datos de admin.
            assertNull(estado.solicitud().solicitante());
            assertNull(estado.solicitud().resueltaPor());
        }

        @Test
        @DisplayName("una cuenta migrada ve hasta cuando conserva el acceso provisorio")
        void migradaConGracia() {
            ultimaDelUsuario(null);
            usuario.setTieneDiscapacidad(true);
            usuario.setDiscapacidadAccesoProvisorioHasta(INSTANTE.plus(30, ChronoUnit.DAYS));

            var estado = servicio().miEstado(USUARIO);

            assertTrue(estado.puedeUsarCocherasAccesibles());
            assertEquals(INSTANTE.plus(30, ChronoUnit.DAYS), estado.accesoProvisorioHasta());
            assertTrue(estado.puedeEnviar());
        }

        @Test
        @DisplayName("el dueño ve su certificado y queda auditado; si ya se borro, 404")
        void miDocumento() {
            VerificacionDiscapacidad propia = solicitud(VerificacionEstado.EN_REVISION);
            ultimaDelUsuario(propia);
            when(storage.leer(propia.getDocumentoClave())).thenReturn(DiscapacidadDePrueba.pdf());

            var documento = servicio().miDocumento(USUARIO);

            assertEquals("application/pdf", documento.tipo());
            ArgumentCaptor<VerificacionDocumentoAcceso> captor = ArgumentCaptor.forClass(VerificacionDocumentoAcceso.class);
            verify(accesoRepository).save(captor.capture());
            assertEquals("USER", captor.getValue().getActorRol());
            assertEquals(usuario.getId(), captor.getValue().getActorId());

            propia.setDocumentoEliminadoAt(INSTANTE);
            assertThrows(NotFoundException.class, () -> servicio().miDocumento(USUARIO));
        }

        @Test
        @DisplayName("el admin ve el certificado de cualquier solicitud y queda auditado")
        void documentoDelAdmin() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            when(storage.leer(pendiente.getDocumentoClave())).thenReturn(DiscapacidadDePrueba.pdf());

            servicio().documento(pendiente.getId(), ADMIN);

            ArgumentCaptor<VerificacionDocumentoAcceso> captor = ArgumentCaptor.forClass(VerificacionDocumentoAcceso.class);
            verify(accesoRepository).save(captor.capture());
            assertEquals("ADMIN", captor.getValue().getActorRol());
            assertEquals(INSTANTE, captor.getValue().getFecha());
        }

        @Test
        @DisplayName("cancelar retira la propia solicitud en revision; sin una, 409")
        void cancelar() {
            VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
            ultimaDelUsuario(pendiente);

            assertEquals(EstadoDiscapacidad.SIN_SOLICITUD, servicio().cancelar(USUARIO).estado());
            assertEquals(VerificacionEstado.CANCELADA, pendiente.getEstado());
            assertEquals(INSTANTE, pendiente.getResueltaAt());

            assertThrows(ConflictoException.class, () -> servicio().cancelar(USUARIO));
        }
    }

    // ---- Barridos y migracion ----

    @Nested
    @DisplayName("barridos")
    class Barridos {
        @Test
        @DisplayName("vence una verificacion cuyo ultimo dia fue ayer y le quita el acceso")
        void vence() {
            VerificacionDiscapacidad vencida = solicitud(VerificacionEstado.VERIFICADA);
            vencida.setVenceEl(HOY.minusDays(1));
            usuario.setTieneDiscapacidad(true);
            when(verificacionRepository.findVencidas(HOY)).thenReturn(List.of(vencida));

            assertEquals(1, servicio().vencerVerificaciones());

            assertEquals(VerificacionEstado.VENCIDA, vencida.getEstado());
            assertFalse(usuario.getTieneDiscapacidad());
            verify(notificador).resultado(USUARIO, VerificacionEstado.VENCIDA, null);
        }

        @Test
        @DisplayName("el borde del vencimiento lo marca la consulta: compara contra el dia de hoy del predio")
        void bordeDelVencimiento() {
            // A las 00:30 del 11/10 ya es "hoy" 11/10: una que vence el 10/10 entra.
            servicioA(LocalDateTime.of(2026, 10, 11, 0, 30)).vencerVerificaciones();
            verify(verificacionRepository).findVencidas(LocalDate.of(2026, 10, 11));
            // A las 23:59 del 10/10 todavia es el 10/10: la que vence ese dia sigue vigente.
            servicioA(LocalDateTime.of(2026, 10, 10, 23, 59)).vencerVerificaciones();
            verify(verificacionRepository).findVencidas(LocalDate.of(2026, 10, 10));
        }

        @Test
        @DisplayName("termina la gracia de una cuenta migrada sin solicitud; con una en revision, la conserva")
        void finDeLaGracia() {
            Visitante otra = cuenta("Eva", "eva@test.com", AppAuthority.USER);
            for (Visitante v : List.of(usuario, otra)) {
                v.setTieneDiscapacidad(true);
                v.setDiscapacidadAccesoProvisorioHasta(INSTANTE.minusSeconds(1));
            }
            when(visitanteRepository.findByTieneDiscapacidadTrueAndDiscapacidadAccesoProvisorioHastaBefore(INSTANTE))
                    .thenReturn(List.of(usuario, otra));
            when(verificacionRepository.existsByVisitanteIdAndEstado(usuario.getId(), VerificacionEstado.EN_REVISION))
                    .thenReturn(false);
            when(verificacionRepository.existsByVisitanteIdAndEstado(otra.getId(), VerificacionEstado.EN_REVISION))
                    .thenReturn(true);

            assertEquals(1, servicio().terminarAccesosProvisorios());

            assertFalse(usuario.getTieneDiscapacidad());
            assertNull(usuario.getDiscapacidadAccesoProvisorioHasta());
            assertTrue(otra.getTieneDiscapacidad());
        }

        @Test
        @DisplayName("la gracia termina estrictamente despues del plazo: la consulta usa 'antes de ahora'")
        void bordeDeLaGracia() {
            servicio().terminarAccesosProvisorios();

            verify(visitanteRepository).findByTieneDiscapacidadTrueAndDiscapacidadAccesoProvisorioHastaBefore(INSTANTE);
        }

        @Test
        @DisplayName("retencion: rechazadas y canceladas enseguida, el resto despues de 30 dias")
        void retencion() {
            VerificacionDiscapacidad rechazada = solicitud(VerificacionEstado.RECHAZADA);
            VerificacionDiscapacidad aprobada = solicitud(VerificacionEstado.VERIFICADA);
            aprobada.setDocumentoClave("9a8b7c6d-1e2f-4a3b-8c9d-0e1f2a3b4c5d.png");
            when(verificacionRepository.findDocumentosARetirar(
                    List.of(VerificacionEstado.RECHAZADA, VerificacionEstado.CANCELADA), INSTANTE))
                    .thenReturn(List.of(rechazada));
            when(verificacionRepository.findDocumentosARetirar(
                    List.of(VerificacionEstado.VERIFICADA, VerificacionEstado.REVOCADA, VerificacionEstado.VENCIDA),
                    INSTANTE.minus(30, ChronoUnit.DAYS)))
                    .thenReturn(List.of(aprobada));

            assertEquals(2, servicio().retirarDocumentos());

            verify(storage).eliminar(rechazada.getDocumentoClave());
            verify(storage).eliminar(aprobada.getDocumentoClave());
            assertEquals(INSTANTE, rechazada.getDocumentoEliminadoAt());
            assertEquals(INSTANTE, aprobada.getDocumentoEliminadoAt());
            // El registro de la decision queda.
            assertEquals(VerificacionEstado.VERIFICADA, aprobada.getEstado());
        }

        @Test
        @DisplayName("si un archivo no se pudo borrar, queda para el proximo barrido")
        void retencionReintenta() {
            VerificacionDiscapacidad rechazada = solicitud(VerificacionEstado.RECHAZADA);
            when(verificacionRepository.findDocumentosARetirar(eq(List.of(VerificacionEstado.RECHAZADA,
                    VerificacionEstado.CANCELADA)), any())).thenReturn(List.of(rechazada));
            doThrow(new RuntimeException("disco")).when(storage).eliminar(any());

            assertEquals(0, servicio().retirarDocumentos());
            assertTrue(rechazada.documentoDisponible());
        }

        @Test
        @DisplayName("migracion: las autodeclaradas reciben 30 dias de gracia desde ahora")
        void migracion() {
            usuario.setTieneDiscapacidad(true);
            when(visitanteRepository.findAutodeclaradosSinVerificacion()).thenReturn(List.of(usuario));

            assertEquals(1, servicio().migrarAutodeclarados());

            assertEquals(INSTANTE.plus(30, ChronoUnit.DAYS), usuario.getDiscapacidadAccesoProvisorioHasta());
            assertTrue(usuario.getTieneDiscapacidad());
        }
    }

    @Test
    @DisplayName("listar y contar delegan en la cola por estado")
    void listarYContar() {
        VerificacionDiscapacidad pendiente = solicitud(VerificacionEstado.EN_REVISION);
        when(verificacionRepository.findCola(eq(VerificacionEstado.EN_REVISION), any())).thenReturn(List.of(pendiente));
        when(verificacionRepository.countByEstado(VerificacionEstado.EN_REVISION)).thenReturn(3L);

        var lista = servicio().listar(VerificacionEstado.EN_REVISION);

        assertEquals(1, lista.size());
        assertNotNull(lista.getFirst().solicitante());
        assertEquals(USUARIO, lista.getFirst().solicitante().email());
        assertEquals(3L, servicio().cantidadPendientes());
    }
}
