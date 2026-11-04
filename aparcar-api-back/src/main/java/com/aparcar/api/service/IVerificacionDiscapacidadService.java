package com.aparcar.api.service;

import com.aparcar.api.dto.discapacidad.MiVerificacionResponseDto;
import com.aparcar.api.dto.discapacidad.VerificacionResponseDto;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.exception.ArchivoDemasiadoGrandeException;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.TipoArchivoNoPermitidoException;
import com.aparcar.api.exception.ValidationException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Verificacion de discapacidad con certificado. Es lo unico que decide si una
 * persona puede usar las cocheras ACCESIBLE: aprobar habilita, rechazar,
 * revocar o vencer deshabilitan. Ningun otro servicio cambia ese dato.
 */
public interface IVerificacionDiscapacidadService {
    /** El certificado, para servirlo por un endpoint autenticado. */
    record DocumentoDescarga(String nombre, String tipo, byte[] contenido) {
    }

    /**
     * El visitante sube su certificado: queda una solicitud EN_REVISION.
     *
     * @throws ConflictoException              Si ya tiene una en revision o una verificacion vigente.
     * @throws ValidationException             Si el archivo esta vacio.
     * @throws ArchivoDemasiadoGrandeException Si supera el tamaño maximo.
     * @throws TipoArchivoNoPermitidoException Si no es PDF, JPG o PNG de verdad.
     */
    MiVerificacionResponseDto solicitar(String email, MultipartFile archivo);

    MiVerificacionResponseDto miEstado(String email);

    /**
     * El certificado de la ultima solicitud de quien pide, mientras exista.
     *
     * @throws NotFoundException Si no tiene ninguno guardado.
     */
    DocumentoDescarga miDocumento(String email);

    /**
     * Retira la propia solicitud en revision.
     *
     * @throws ConflictoException Si no tiene una en revision.
     */
    MiVerificacionResponseDto cancelar(String email);

    /** La cola del admin, mas viejas primero. */
    List<VerificacionResponseDto> listar(VerificacionEstado estado);

    long cantidadPendientes();

    /** @throws NotFoundException Si no existe la solicitud o su archivo ya se elimino. */
    DocumentoDescarga documento(UUID id, String adminEmail);

    /**
     * @param venceEl Opcional: ultimo dia de vigencia del certificado.
     * @throws ConflictoException  Si la solicitud ya fue resuelta (por otro admin, o dos veces).
     * @throws ValidationException Si el vencimiento ya paso.
     */
    VerificacionResponseDto aprobar(UUID id, LocalDate venceEl, String adminEmail);

    /** @throws ValidationException Si falta el motivo. */
    VerificacionResponseDto rechazar(UUID id, String motivo, String adminEmail);

    /**
     * Quita una verificacion vigente.
     *
     * @throws ConflictoException Si la solicitud no esta VERIFICADA.
     */
    VerificacionResponseDto revocar(UUID id, String motivo, String adminEmail);

    // ---- Barridos y migracion ----

    /** Pasa a VENCIDA las verificaciones cuya vigencia termino. */
    int vencerVerificaciones();

    /** Quita el acceso provisorio a las cuentas migradas cuyo plazo termino sin verificacion. */
    int terminarAccesosProvisorios();

    /** Borra los archivos que ya no hacen falta y conserva el registro de la decision. */
    int retirarDocumentos();

    /** Da plazo de gracia a las cuentas que se habian autodeclarado antes de la verificacion. */
    int migrarAutodeclarados();
}
