package com.aparcar.api.service.impl;

import com.aparcar.api.component.IDocumentoStorage;
import com.aparcar.api.component.NotificadorVerificaciones;
import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.dto.discapacidad.MiVerificacionResponseDto;
import com.aparcar.api.dto.discapacidad.VerificacionResponseDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.discapacidad.EstadoDiscapacidad;
import com.aparcar.api.entity.discapacidad.VerificacionDiscapacidad;
import com.aparcar.api.entity.discapacidad.VerificacionDocumentoAcceso;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.VerificacionDiscapacidadRepository;
import com.aparcar.api.repository.VerificacionDocumentoAccesoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IVerificacionDiscapacidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificacionDiscapacidadService implements IVerificacionDiscapacidadService {
    private static final int MAX_LISTADO = 200;

    /** Sin decision que auditar sobre el archivo: se borra en el siguiente barrido. */
    private static final List<VerificacionEstado> SE_BORRAN_YA =
            List.of(VerificacionEstado.RECHAZADA, VerificacionEstado.CANCELADA);
    /** Aprobadas, revocadas o vencidas: el archivo se guarda la retencion configurada. */
    private static final List<VerificacionEstado> SE_BORRAN_CON_RETENCION =
            List.of(VerificacionEstado.VERIFICADA, VerificacionEstado.REVOCADA, VerificacionEstado.VENCIDA);

    private final VerificacionDiscapacidadRepository verificacionRepository;
    private final VerificacionDocumentoAccesoRepository accesoRepository;
    private final VisitanteRepository visitanteRepository;
    private final IDocumentoStorage storage;
    private final ValidadorDocumento validador;
    private final NotificadorVerificaciones notificador;
    private final DiscapacidadProperties properties;
    private final Clock clock;

    // ---- Visitante ----

    @Override
    @Transactional
    public MiVerificacionResponseDto solicitar(String email, MultipartFile archivo) {
        // Con la cuenta bloqueada, dos subidas simultaneas de la misma persona
        // se ordenan: la segunda ya ve la primera EN_REVISION.
        Visitante visitante = visitanteRepository.findByEmailParaActualizar(email)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
        VerificacionDiscapacidad ultima = ultima(visitante);
        if (ultima != null && ultima.getEstado() == VerificacionEstado.EN_REVISION) {
            throw new ConflictoException("Ya tenés una solicitud en revisión. Esperá la respuesta antes de enviar otra.");
        }
        if (ultima != null && ultima.getEstado() == VerificacionEstado.VERIFICADA) {
            throw new ConflictoException("Tu verificación está vigente: no hace falta enviar otro certificado.");
        }

        if (archivo == null || archivo.isEmpty()) {
            throw new ValidationException("El archivo está vacío.");
        }
        validador.validarTamano(archivo.getSize());
        ValidadorDocumento.DocumentoValidado documento =
                validador.validar(archivo.getOriginalFilename(), archivo.getContentType(), leer(archivo));

        String clave = storage.guardar(documento.contenido(), documento.extension());
        // Si la transaccion no se confirma, el archivo no queda huerfano.
        alTerminar(() -> {}, () -> storage.eliminar(clave));

        VerificacionDiscapacidad solicitud = new VerificacionDiscapacidad();
        solicitud.setVisitante(visitante);
        solicitud.setEstado(VerificacionEstado.EN_REVISION);
        solicitud.setFechaCreacion(clock.instant());
        solicitud.setDocumentoClave(clave);
        solicitud.setDocumentoNombre(documento.nombre());
        solicitud.setDocumentoTipo(documento.tipo());
        solicitud.setDocumentoTamano((long) documento.contenido().length);
        solicitud.setDocumentoSha256(documento.sha256());
        try {
            verificacionRepository.saveAndFlush(solicitud);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("Ya tenés una solicitud en revisión. Esperá la respuesta antes de enviar otra.");
        }

        alTerminar(notificador::solicitudNueva, () -> {});
        return toMiDto(visitante, solicitud);
    }

    @Override
    @Transactional(readOnly = true)
    public MiVerificacionResponseDto miEstado(String email) {
        Visitante visitante = buscarVisitante(email);
        return toMiDto(visitante, ultima(visitante));
    }

    @Override
    @Transactional
    public DocumentoDescarga miDocumento(String email) {
        Visitante visitante = buscarVisitante(email);
        VerificacionDiscapacidad ultima = ultima(visitante);
        if (ultima == null || !ultima.documentoDisponible()) {
            throw new NotFoundException("No tenés un certificado guardado.");
        }
        return descargar(ultima, visitante, AppAuthority.USER);
    }

    @Override
    @Transactional
    public MiVerificacionResponseDto cancelar(String email) {
        Visitante visitante = buscarVisitante(email);
        VerificacionDiscapacidad ultima = ultima(visitante);
        if (ultima == null || ultima.getEstado() != VerificacionEstado.EN_REVISION) {
            throw new ConflictoException("No tenés una solicitud en revisión para cancelar.");
        }
        ultima.setEstado(VerificacionEstado.CANCELADA);
        ultima.setResueltaAt(clock.instant());
        guardar(ultima);
        return toMiDto(visitante, ultima);
    }

    // ---- Administrador ----

    @Override
    @Transactional(readOnly = true)
    public List<VerificacionResponseDto> listar(VerificacionEstado estado) {
        return verificacionRepository.findCola(estado, Limit.of(MAX_LISTADO)).stream()
                .map(v -> toDto(v, true))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public long cantidadPendientes() {
        return verificacionRepository.countByEstado(VerificacionEstado.EN_REVISION);
    }

    @Override
    @Transactional
    public DocumentoDescarga documento(UUID id, String adminEmail) {
        VerificacionDiscapacidad solicitud = buscar(id);
        if (!solicitud.documentoDisponible()) {
            throw new NotFoundException("El certificado de esta solicitud ya se eliminó por la política de retención.");
        }
        return descargar(solicitud, buscarVisitante(adminEmail), AppAuthority.ADMIN);
    }

    @Override
    @Transactional
    public VerificacionResponseDto aprobar(UUID id, LocalDate venceEl, String adminEmail) {
        Visitante admin = buscarVisitante(adminEmail);
        VerificacionDiscapacidad solicitud = buscar(id);
        exigirEnRevision(solicitud);
        if (venceEl != null && venceEl.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("La fecha de vencimiento no puede estar en el pasado.");
        }

        solicitud.setEstado(VerificacionEstado.VERIFICADA);
        solicitud.setVenceEl(venceEl);
        resolver(solicitud, admin);
        Visitante visitante = solicitud.getVisitante();
        visitante.setTieneDiscapacidad(true);
        visitante.setDiscapacidadAccesoProvisorioHasta(null);
        guardar(solicitud);

        avisarResultado(visitante.getEmail(), VerificacionEstado.VERIFICADA, null);
        return toDto(solicitud, true);
    }

    @Override
    @Transactional
    public VerificacionResponseDto rechazar(UUID id, String motivo, String adminEmail) {
        String motivoLimpio = exigirMotivo(motivo);
        Visitante admin = buscarVisitante(adminEmail);
        VerificacionDiscapacidad solicitud = buscar(id);
        exigirEnRevision(solicitud);

        solicitud.setEstado(VerificacionEstado.RECHAZADA);
        solicitud.setMotivo(motivoLimpio);
        resolver(solicitud, admin);
        // Rechazar termina tambien el acceso provisorio de una cuenta migrada.
        quitarAcceso(solicitud.getVisitante());
        guardar(solicitud);

        avisarResultado(solicitud.getVisitante().getEmail(), VerificacionEstado.RECHAZADA, motivoLimpio);
        return toDto(solicitud, true);
    }

    @Override
    @Transactional
    public VerificacionResponseDto revocar(UUID id, String motivo, String adminEmail) {
        String motivoLimpio = exigirMotivo(motivo);
        Visitante admin = buscarVisitante(adminEmail);
        VerificacionDiscapacidad solicitud = buscar(id);
        if (solicitud.getEstado() != VerificacionEstado.VERIFICADA) {
            throw new ConflictoException("Solo se puede revocar una verificación vigente (esta está %s)."
                    .formatted(estadoEnTexto(solicitud.getEstado())));
        }

        solicitud.setEstado(VerificacionEstado.REVOCADA);
        solicitud.setMotivo(motivoLimpio);
        solicitud.setRevocadaAt(clock.instant());
        solicitud.setRevocadaPorId(admin.getId());
        solicitud.setRevocadaPorNombre(admin.getNombre());
        solicitud.setRevocadaPorEmail(admin.getEmail());
        quitarAcceso(solicitud.getVisitante());
        guardar(solicitud);

        avisarResultado(solicitud.getVisitante().getEmail(), VerificacionEstado.REVOCADA, motivoLimpio);
        return toDto(solicitud, true);
    }

    // ---- Barridos y migracion ----

    @Override
    @Transactional
    public int vencerVerificaciones() {
        List<VerificacionDiscapacidad> vencidas = verificacionRepository.findVencidas(LocalDate.now(clock));
        for (VerificacionDiscapacidad verificacion : vencidas) {
            verificacion.setEstado(VerificacionEstado.VENCIDA);
            quitarAcceso(verificacion.getVisitante());
            avisarResultado(verificacion.getVisitante().getEmail(), VerificacionEstado.VENCIDA, null);
        }
        verificacionRepository.saveAll(vencidas);
        return vencidas.size();
    }

    @Override
    @Transactional
    public int terminarAccesosProvisorios() {
        int terminados = 0;
        for (Visitante visitante : visitanteRepository
                .findByTieneDiscapacidadTrueAndDiscapacidadAccesoProvisorioHastaBefore(clock.instant())) {
            // Si subio el certificado a tiempo, conserva el acceso hasta que lo revisen.
            if (verificacionRepository.existsByVisitanteIdAndEstado(visitante.getId(), VerificacionEstado.EN_REVISION)) {
                continue;
            }
            quitarAcceso(visitante);
            visitanteRepository.save(visitante);
            terminados++;
        }
        return terminados;
    }

    @Override
    @Transactional
    public int retirarDocumentos() {
        Instant ahora = clock.instant();
        List<VerificacionDiscapacidad> aRetirar = new ArrayList<>(
                verificacionRepository.findDocumentosARetirar(SE_BORRAN_YA, ahora));
        aRetirar.addAll(verificacionRepository.findDocumentosARetirar(SE_BORRAN_CON_RETENCION,
                ahora.minus(properties.documentoRetencionDias(), ChronoUnit.DAYS)));

        int retirados = 0;
        for (VerificacionDiscapacidad verificacion : aRetirar) {
            try {
                storage.eliminar(verificacion.getDocumentoClave());
            } catch (RuntimeException e) {
                // Queda para el proximo barrido. Sin nombre del archivo en el log.
                log.warn("No se pudo borrar el certificado de la solicitud {}: {}", verificacion.getId(), e.getMessage());
                continue;
            }
            verificacion.setDocumentoEliminadoAt(ahora);
            retirados++;
        }
        verificacionRepository.saveAll(aRetirar);
        return retirados;
    }

    @Override
    @Transactional
    public int migrarAutodeclarados() {
        List<Visitante> autodeclarados = visitanteRepository.findAutodeclaradosSinVerificacion();
        Instant hasta = clock.instant().plus(properties.graciaMigracionDias(), ChronoUnit.DAYS);
        autodeclarados.forEach(visitante -> visitante.setDiscapacidadAccesoProvisorioHasta(hasta));
        visitanteRepository.saveAll(autodeclarados);
        return autodeclarados.size();
    }

    // ---- Auxiliares ----

    private void exigirEnRevision(VerificacionDiscapacidad solicitud) {
        if (solicitud.getEstado() != VerificacionEstado.EN_REVISION) {
            throw new ConflictoException("Esta solicitud ya fue resuelta (está %s). Actualizá la lista."
                    .formatted(estadoEnTexto(solicitud.getEstado())));
        }
    }

    private static String exigirMotivo(String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.isEmpty()) {
            throw new ValidationException("El motivo es obligatorio: el visitante lo va a leer.");
        }
        if (limpio.length() > 500) {
            throw new ValidationException("El motivo no puede superar los 500 caracteres.");
        }
        return limpio;
    }

    private void resolver(VerificacionDiscapacidad solicitud, Visitante admin) {
        solicitud.setResueltaAt(clock.instant());
        solicitud.setResueltaPorId(admin.getId());
        solicitud.setResueltaPorNombre(admin.getNombre());
        solicitud.setResueltaPorEmail(admin.getEmail());
    }

    private static void quitarAcceso(Visitante visitante) {
        visitante.setTieneDiscapacidad(false);
        visitante.setDiscapacidadAccesoProvisorioHasta(null);
    }

    /**
     * Flush adentro del metodo: si otro administrador resolvio la misma
     * solicitud al mismo tiempo, la version choca aca y se traduce a un 409
     * con un mensaje claro, en vez de procesarla dos veces.
     */
    private void guardar(VerificacionDiscapacidad solicitud) {
        try {
            verificacionRepository.saveAndFlush(solicitud);
        } catch (OptimisticLockingFailureException e) {
            throw new ConflictoException("Otro administrador ya resolvió esta solicitud. Actualizá la lista.");
        }
    }

    private DocumentoDescarga descargar(VerificacionDiscapacidad solicitud, Visitante actor, AppAuthority rol) {
        byte[] contenido = storage.leer(solicitud.getDocumentoClave());
        accesoRepository.save(new VerificacionDocumentoAcceso(
                solicitud.getId(), clock.instant(), actor.getId(), actor.getEmail(), rol.name()));
        return new DocumentoDescarga(solicitud.getDocumentoNombre(), solicitud.getDocumentoTipo(), contenido);
    }

    private void avisarResultado(String email, VerificacionEstado estado, String motivo) {
        alTerminar(() -> notificador.resultado(email, estado, motivo), () -> {});
    }

    /** Corre una cosa si la transaccion se confirma y otra si se deshace. */
    private static void alTerminar(Runnable siConfirma, Runnable siDeshace) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            siConfirma.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) siConfirma.run();
                else siDeshace.run();
            }
        });
    }

    private static byte[] leer(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo.", e);
        }
    }

    private VerificacionDiscapacidad ultima(Visitante visitante) {
        return verificacionRepository.findFirstByVisitanteIdOrderByFechaCreacionDescIdDesc(visitante.getId())
                .orElse(null);
    }

    private VerificacionDiscapacidad buscar(UUID id) {
        return verificacionRepository.findConVisitante(id)
                .orElseThrow(() -> new NotFoundException("Solicitud de verificación no encontrada."));
    }

    private Visitante buscarVisitante(String email) {
        return visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }

    private static String estadoEnTexto(VerificacionEstado estado) {
        return switch (estado) {
            case EN_REVISION -> "en revisión";
            case VERIFICADA -> "verificada";
            case RECHAZADA -> "rechazada";
            case REVOCADA -> "revocada";
            case VENCIDA -> "vencida";
            case CANCELADA -> "cancelada";
        };
    }

    private MiVerificacionResponseDto toMiDto(Visitante visitante, VerificacionDiscapacidad ultima) {
        EstadoDiscapacidad estado = EstadoDiscapacidad.de(ultima == null ? null : ultima.getEstado());
        boolean puedeEnviar = estado != EstadoDiscapacidad.EN_REVISION && estado != EstadoDiscapacidad.VERIFICADA;
        return new MiVerificacionResponseDto(
                estado,
                visitante.puedeUsarCocheraAccesible(),
                visitante.getDiscapacidadAccesoProvisorioHasta(),
                puedeEnviar,
                properties.documentoMaxMb(),
                ultima == null ? null : toDto(ultima, false));
    }

    private static VerificacionResponseDto toDto(VerificacionDiscapacidad v, boolean paraAdmin) {
        Visitante solicitante = v.getVisitante();
        return new VerificacionResponseDto(
                v.getId(),
                v.getEstado(),
                v.getFechaCreacion(),
                v.getResueltaAt(),
                v.getRevocadaAt(),
                v.getMotivo(),
                v.getVenceEl(),
                v.documentoDisponible(),
                v.getDocumentoNombre(),
                v.getDocumentoTipo(),
                v.getDocumentoTamano() == null ? 0 : v.getDocumentoTamano(),
                paraAdmin ? new VerificacionResponseDto.SolicitanteDto(
                        solicitante.getId(), solicitante.getNombre(), solicitante.getEmail()) : null,
                paraAdmin ? persona(v.getResueltaPorNombre(), v.getResueltaPorEmail()) : null,
                paraAdmin ? persona(v.getRevocadaPorNombre(), v.getRevocadaPorEmail()) : null);
    }

    private static String persona(String nombre, String email) {
        return nombre == null ? null : "%s (%s)".formatted(nombre, email);
    }
}
