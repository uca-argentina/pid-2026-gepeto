package com.aparcar.api.service;

import com.aparcar.api.dto.reserva.ReservaRequestDto;
import com.aparcar.api.dto.reserva.ReservaResponseDto;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

import java.util.List;
import java.util.UUID;

public interface IReservaService {
    /**
     * Crea una reserva CONFIRMADA validando:
     * <ul>
     *     <li>Que visitante, vehiculo y cochera existan.</li>
     *     <li>Que el vehiculo pertenezca al visitante indicado.</li>
     *     <li>Que el tipo de la cochera sea compatible con el tipo del vehiculo
     *     (ACCESIBLE acepta cualquier tipo; el resto debe coincidir exactamente).</li>
     *     <li>Que la cochera no tenga ya otra reserva CONFIRMADA en la misma fecha.</li>
     * </ul>
     *
     * <p>Un visitante solo puede reservar a su nombre: si {@code requesterIsAdmin}
     * es false se ignora el visitante del dto y se usa la cuenta autenticada.
     *
     * @param requesterEmail   Email de la cuenta autenticada.
     * @param requesterIsAdmin Si quien reserva tiene rol ADMIN.
     * @throws NotFoundException   Si visitante, vehiculo o cochera no existen.
     * @throws ValidationException Si no se cumple alguna regla de negocio.
     */
    ReservaResponseDto crear(ReservaRequestDto dto, String requesterEmail, boolean requesterIsAdmin);

    /**
     * @throws NotFoundException Si no existe una reserva con ese id.
     * @throws org.springframework.security.access.AccessDeniedException Si quien pide no es ADMIN
     *                                                                   ni el dueño de la reserva.
     */
    ReservaResponseDto obtenerPorId(UUID id, String requesterEmail, boolean requesterIsAdmin);

    /**
     * El ADMIN ve todas las reservas del sistema; un visitante solo las suyas.
     */
    List<ReservaResponseDto> listar(String requesterEmail, boolean requesterIsAdmin);

    /**
     * Da de baja una reserva pasandola a CANCELADA, lo que libera la cochera
     * para esa fecha.
     *
     * <p>No borra la fila: el sistema ya cancela en vez de borrar cuando se
     * deshabilita una cochera, y conservar la reserva deja ver que existio y
     * quien la habia hecho.
     *
     * <p>Un visitante solo puede cancelar las suyas; el ADMIN, cualquiera.
     *
     * @throws NotFoundException   Si no existe una reserva con ese id.
     * @throws ValidationException Si la reserva ya estaba cancelada.
     * @throws org.springframework.security.access.AccessDeniedException Si quien pide no es ADMIN
     *                                                                   ni el dueño de la reserva.
     */
    ReservaResponseDto cancelar(UUID id, String requesterEmail, boolean requesterIsAdmin);
}
