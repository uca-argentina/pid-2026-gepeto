package com.aparcar.api.service;

import com.aparcar.api.dto.reserva.CocheraRequestDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ICocheraService {
    /**
     * @throws ValidationException Si ya existe una cochera con el mismo numero.
     */
    CocheraResponseDto crear(CocheraRequestDto dto);

    /**
     * Alta en lote: todo-o-nada. Si cualquier elemento falla (numero
     * repetido dentro del lote, o ya existente en la base), no se crea
     * ninguna cochera del lote.
     *
     * @throws ValidationException Si la lista esta vacia, si hay un numero
     *                              repetido dentro del propio lote, o si
     *                              algun numero ya existe en la base.
     */
    List<CocheraResponseDto> crearEnLote(List<CocheraRequestDto> dtos);

    /**
     * Valores distintos de "sector" ya usados en cocheras existentes,
     * ordenados alfabeticamente. Pensado para que el frontend arme un
     * dropdown con sectores reales en vez de texto libre.
     */
    List<String> listarSectores();

    /**
     * Todos los parametros son opcionales (pasar null los ignora). El filtro
     * de sector es parcial e insensible a mayusculas/minusculas.
     *
     * <p>Si se indica fecha, cada cochera devuelta trae "disponibleEnFecha"
     * calculado (sin reserva CONFIRMADA para esa fecha); si no se indica
     * fecha, ese campo queda en null.
     */
    List<CocheraResponseDto> listar(String sector, CocheraTipo tipo, CocheraEstado estado, LocalDate fecha);

    /**
     * @throws NotFoundException Si no existe una cochera con ese id.
     */
    CocheraResponseDto obtenerPorId(UUID id);

    /**
     * @throws NotFoundException   Si no existe una cochera con ese id.
     * @throws ValidationException Si el nuevo numero ya está en uso por otra cochera.
     */
    CocheraResponseDto editar(UUID id, CocheraRequestDto dto);

    /**
     * @throws NotFoundException   Si no existe una cochera con ese id.
     * @throws ValidationException Si la cochera tiene reservas asociadas.
     */
    void eliminar(UUID id);

    /**
     * Cocheras habilitadas sin una reserva CONFIRMADA en esa fecha. Si se
     * indica tipoVehiculo, solo devuelve las compatibles (ver regla de
     * compatibilidad en {@link IReservaService}).
     */
    List<CocheraResponseDto> listarDisponibles(LocalDateTime desde, LocalDateTime hasta,
                                               VehiculoTipo tipoVehiculo);
}