package com.aparcar.api.service;

import com.aparcar.api.dto.auth.ChangePasswordDto;
import com.aparcar.api.dto.reserva.VisitanteAltaDto;
import com.aparcar.api.dto.reserva.VisitanteAltaResponseDto;
import com.aparcar.api.dto.reserva.VisitanteResponseDto;
import com.aparcar.api.dto.reserva.VisitanteUpdateDto;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

import java.util.List;
import java.util.UUID;

public interface IVisitanteService {
    /**
     * Alta operativa desde el panel del admin: crea la cuenta del visitante, su
     * vehiculo y la reserva de hoy para la cochera indicada, todo en la misma
     * transaccion. La contraseña inicial es el documento, y la cuenta nace
     * activa y con rol USER.
     *
     * <p>Es todo o nada a proposito: si la cochera esta ocupada o la patente ya
     * existe, no queda ninguna cuenta a medio crear.
     *
     * @throws ValidationException Si el documento, el email o la patente ya estan en uso, si la
     *                             cochera no es compatible con el vehiculo o si ya esta reservada
     *                             para hoy.
     * @throws NotFoundException   Si la cochera indicada no existe.
     */
    VisitanteAltaResponseDto altaConReserva(VisitanteAltaDto dto);

    /**
     * @throws NotFoundException Si no existe un visitante con ese id.
     */
    VisitanteResponseDto obtenerPorId(UUID id);

    List<VisitanteResponseDto> listar();

    /**
     * @throws NotFoundException Si no existe una cuenta con ese email.
     */
    VisitanteResponseDto obtenerPropio(String email);

    /**
     * Actualiza los campos que el visitante puede cambiar de si mismo (telefono
     * y email). No permite modificar nombre ni documento.
     *
     * @throws NotFoundException   Si no existe una cuenta con ese email.
     * @throws ValidationException Si el email nuevo ya lo usa otra cuenta.
     */
    VisitanteResponseDto actualizarPropio(String email, VisitanteUpdateDto dto);

    /**
     * Cambia la contraseña de la cuenta autenticada, validando primero la
     * actual.
     *
     * <p>Sirve sobre todo para que un visitante dado de alta por un admin deje
     * de usar su documento como contraseña.
     *
     * @throws NotFoundException   Si no existe una cuenta con ese email.
     * @throws ValidationException Si la contraseña actual no coincide, o si la
     *                             nueva es igual a la que ya tenia.
     */
    void cambiarPasswordPropia(String email, ChangePasswordDto dto);
}
