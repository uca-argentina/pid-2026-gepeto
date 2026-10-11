package com.aparcar.api.service;

import com.aparcar.api.dto.acceso.AccesoAccion;
import com.aparcar.api.dto.acceso.AccesoConsultaResponseDto;
import com.aparcar.api.dto.acceso.AccesoMovimientoResponseDto;
import com.aparcar.api.dto.acceso.AccesoRegistroResponseDto;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

import java.util.List;

/**
 * Control de acceso por codigo/QR. Escanear el QR y tipear el codigo corto son
 * la misma operacion: el QR lleva {@code APARCAR:} + el codigo, y la entrada
 * se normaliza antes de buscarla.
 *
 * <p>Cada intento, valido o no, queda registrado con quien lo hizo.
 */
public interface IAccesoService {
    /**
     * Sin efectos sobre el ingreso o el egreso: devuelve los datos de la
     * reserva, la accion que corresponde por su estado y, si no se puede, el
     * motivo. Si la reserva ya estaba vencida sin que nadie lo hubiera
     * asentado, la deja VENCIDA (lo mismo que haria el barrido).
     *
     * @throws ValidationException Si el codigo no tiene un formato valido.
     * @throws NotFoundException   Si no hay ninguna reserva con ese codigo.
     */
    AccesoConsultaResponseDto consultar(String codigo, String operadorEmail);

    /**
     * Registra el ingreso (CONFIRMADA → EN_CURSO) o el egreso
     * (EN_CURSO → FINALIZADA), revalidando todo. Repetir la misma accion
     * dentro de la ventana de idempotencia devuelve lo ya registrado con
     * {@code yaRegistrado = true}, sin registrar dos veces.
     *
     * @throws ValidationException Si el formato es invalido o la accion no corresponde ahora.
     * @throws NotFoundException   Si no hay ninguna reserva con ese codigo.
     * @throws org.springframework.dao.OptimisticLockingFailureException Si otro operador registro
     *                                                                   lo mismo al mismo tiempo.
     */
    AccesoRegistroResponseDto registrar(String codigo, AccesoAccion accion, String operadorEmail);

    /** Ingresos y egresos de hoy, los mas recientes primero. */
    List<AccesoMovimientoResponseDto> movimientosDelDia();
}
