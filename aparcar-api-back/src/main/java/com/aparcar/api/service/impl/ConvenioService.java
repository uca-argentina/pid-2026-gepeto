package com.aparcar.api.service.impl;

import com.aparcar.api.dto.convenio.*;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.convenio.Convenio;
import com.aparcar.api.entity.convenio.CredencialConvenio;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ConvenioRepository;
import com.aparcar.api.repository.CredencialConvenioRepository;
import com.aparcar.api.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConvenioService {
    private final ConvenioRepository convenios;
    private final CredencialConvenioRepository credenciales;
    private final VisitanteRepository visitantes;

    public List<ConvenioResponseDto> listar() {
        return convenios.findAllByOrderByNombreAscIdAsc().stream().map(ConvenioService::dto).toList();
    }

    @Transactional
    public ConvenioResponseDto guardar(UUID id, ConvenioRequestDto dto) {
        Convenio convenio = id == null ? new Convenio() : buscar(id);
        String formato = normalizar(dto.formatoCredencial());
        // XXX es un marcador literal, nunca una expresion regular elegida por el administrador.
        if (formato.length() > 100 || !formato.matches("[A-Z0-9._/-]+XXX")
                || formato.indexOf("XXX") != formato.length() - 3) {
            throw new ValidationException("El formato debe tener un prefijo y terminar en XXX, por ejemplo UCA-XXX.");
        }
        String nombre = dto.nombre() == null ? "" : dto.nombre().trim();
        if (nombre.isEmpty() || nombre.length() > 100) {
            throw new ValidationException("El nombre del convenio debe tener entre 1 y 100 caracteres.");
        }
        if (dto.descuentoPct() == null || dto.descuentoPct().signum() <= 0
                || dto.descuentoPct().compareTo(new BigDecimal("100")) > 0
                || dto.descuentoPct().stripTrailingZeros().scale() > 2) {
            throw new ValidationException("El descuento debe ser mayor a 0 y hasta 100 %, con hasta dos decimales.");
        }
        if (id != null && !formato.equals(convenio.getFormatoCredencial())) {
            credenciales.deleteByConvenioId(id);
        }
        convenio.setNombre(nombre);
        convenio.setFormatoCredencial(formato);
        convenio.setDescuentoPct(dto.descuentoPct().setScale(2));
        return dto(convenios.save(convenio));
    }

    @Transactional
    public void eliminar(UUID id) {
        // Las credenciales desaparecen por FK; las reservas conservan una copia sin FK.
        convenios.delete(buscar(id));
    }

    public List<CredencialResponseDto> listarCredenciales(String email, boolean personal, UUID visitanteId) {
        Visitante titular = resolverTitular(email, personal, visitanteId, null);
        return credenciales.findByVisitanteId(titular.getId()).stream()
                .filter(c -> coincide(c.getConvenio(), c.getCredencial(), titular.getDocumento()))
                .sorted(Comparator.comparing(c -> c.getConvenio().getNombre()))
                .map(c -> credencialDto(c.getConvenio(), c.getCredencial())).toList();
    }

    public CredencialResponseDto verificar(CredencialRequestDto dto, String email, boolean personal) {
        Visitante titular = resolverTitular(email, personal, dto.visitanteId(), dto.documento());
        Convenio convenio = buscar(dto.convenioId());
        return credencialDto(convenio, validar(convenio, dto.credencial(), titular.getDocumento()));
    }

    @Transactional
    public CredencialResponseDto guardarCredencial(CredencialRequestDto dto, String email, boolean personal) {
        if (dto.documento() != null) {
            throw new ValidationException("Para guardar una credencial selecciona una cuenta existente.");
        }
        Visitante titular = resolverTitular(email, personal, dto.visitanteId(), null);
        Convenio convenio = buscar(dto.convenioId());
        String valor = validar(convenio, dto.credencial(), titular.getDocumento());
        CredencialConvenio credencial = credenciales.findByVisitanteIdAndConvenioId(titular.getId(), convenio.getId())
                .orElseGet(CredencialConvenio::new);
        credencial.setVisitante(titular);
        credencial.setConvenio(convenio);
        credencial.setCredencial(valor);
        credenciales.saveAndFlush(credencial);
        return credencialDto(convenio, valor);
    }

    @Transactional
    public void eliminarCredencial(UUID convenioId, String email, boolean personal, UUID visitanteId) {
        Visitante titular = resolverTitular(email, personal, visitanteId, null);
        credenciales.deleteByVisitanteIdAndConvenioId(titular.getId(), convenioId);
    }

    /** El documento libre solo sirve a personal para cotizar/verificar antes del alta de una cuenta. */
    public Visitante resolverTitular(String email, boolean personal, UUID visitanteId, String documento) {
        Visitante propio = visitantes.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
        if (!personal && ((visitanteId != null && !visitanteId.equals(propio.getId())) || documento != null)) {
            throw new AccessDeniedException("Solo podes gestionar tus propias credenciales.");
        }
        if (documento != null) {
            if (visitanteId != null) throw new ValidationException("Indica un visitante o un documento, no ambos.");
            Visitante nuevo = new Visitante();
            nuevo.setDocumento(documento);
            return nuevo;
        }
        return visitanteId == null || visitanteId.equals(propio.getId()) ? propio : visitantes.findById(visitanteId)
                .orElseThrow(() -> new NotFoundException("Visitante no encontrado."));
    }

    /** Revalida aun las guardadas: cambiar documento/formato nunca conserva una habilitacion antigua. */
    public DescuentoConvenioDto mejorDescuento(Visitante titular, List<CredencialConvenioDto> ingresadas,
                                              BigDecimal subtotal) {
        Map<UUID, Convenio> validos = new HashMap<>();
        if (titular.getId() != null) {
            for (CredencialConvenio guardada : credenciales.findByVisitanteId(titular.getId())) {
                if (coincide(guardada.getConvenio(), guardada.getCredencial(), titular.getDocumento())) {
                    validos.put(guardada.getConvenio().getId(), guardada.getConvenio());
                }
            }
        }
        if (ingresadas != null) {
            if (ingresadas.size() > 100) throw new ValidationException("Demasiadas credenciales.");
            Set<UUID> recibidas = new HashSet<>();
            for (CredencialConvenioDto ingresada : ingresadas) {
                if (ingresada == null || ingresada.convenioId() == null
                        || !recibidas.add(ingresada.convenioId())) {
                    throw new ValidationException("Indica una sola credencial por convenio.");
                }
                Convenio convenio = buscar(ingresada.convenioId());
                validar(convenio, ingresada.credencial(), titular.getDocumento());
                validos.put(convenio.getId(), convenio);
            }
        }
        // Desempate estable entre convenios del mismo porcentaje.
        Convenio elegido = validos.values().stream().sorted(Comparator
                .comparing(Convenio::getDescuentoPct).reversed().thenComparing(Convenio::getId)).findFirst().orElse(null);
        return elegido == null ? null : new DescuentoConvenioDto(elegido.getId(), elegido.getNombre(),
                elegido.getDescuentoPct(), subtotal.multiply(elegido.getDescuentoPct())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
    }

    public static DescuentoConvenioDto deReserva(Reserva reserva) {
        return reserva.getConvenioId() == null ? null : new DescuentoConvenioDto(reserva.getConvenioId(),
                reserva.getConvenioNombre(), reserva.getConvenioDescuentoPct(), reserva.getConvenioDescuentoMonto());
    }

    private Convenio buscar(UUID id) {
        if (id == null) throw new ValidationException("El convenio es obligatorio.");
        return convenios.findById(id).orElseThrow(() -> new NotFoundException("Convenio no encontrado."));
    }

    private static String validar(Convenio convenio, String valor, String documento) {
        if (!coincide(convenio, valor, documento)) {
            throw new ValidationException("La credencial de %s debe respetar el formato %s y coincidir con los ultimos tres digitos del documento."
                    .formatted(convenio.getNombre(), convenio.getFormatoCredencial()));
        }
        return normalizar(valor);
    }

    private static boolean coincide(Convenio convenio, String valor, String documento) {
        String numeros = documento == null ? "" : documento.replaceAll("[^0-9]", "");
        if (numeros.length() < 3) return false;
        String esperado = convenio.getFormatoCredencial().substring(0, convenio.getFormatoCredencial().length() - 3)
                + numeros.substring(numeros.length() - 3);
        return esperado.equals(normalizar(valor));
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.trim().toUpperCase(Locale.ROOT);
    }

    private static ConvenioResponseDto dto(Convenio convenio) {
        return new ConvenioResponseDto(convenio.getId(), convenio.getNombre(), convenio.getFormatoCredencial(), convenio.getDescuentoPct());
    }

    private static CredencialResponseDto credencialDto(Convenio convenio, String valor) {
        return new CredencialResponseDto(convenio.getId(), convenio.getNombre(), convenio.getFormatoCredencial(),
                convenio.getDescuentoPct(), valor);
    }
}
