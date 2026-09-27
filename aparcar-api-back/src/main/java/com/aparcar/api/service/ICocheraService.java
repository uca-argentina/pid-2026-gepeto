package com.aparcar.api.service;

import com.aparcar.api.dto.reserva.CocheraAltaPorPlantaDto;
import com.aparcar.api.dto.reserva.CocheraRequestDto;
import com.aparcar.api.dto.reserva.CocheraResponseDto;
import com.aparcar.api.entity.reserva.CocheraEstado;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.VehiculoTipo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;

import java.time.LocalDateTime;
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
     * Alta por planta: crea, en un mismo sector, la cantidad pedida de cada
     * tipo, con numeros generados automaticamente (ver
     * {@link com.aparcar.api.service.impl.NumeracionCocheras}). Cada tipo
     * continua desde el mayor numero existente de su prefijo.
     *
     * <p>Todo-o-nada: o se crean todas, o ninguna.
     *
     * <p>Si otra alta concurrente llega a generar el mismo numero, la
     * restriccion UNIQUE de {@code cocheras.numero} hace fallar a la segunda,
     * que no crea nada y avisa para reintentar.
     *
     * @return Las cocheras creadas, en orden de tipo (AUTO, MOTO, ACCESIBLE,
     *         CARGA) y de numero dentro de cada tipo.
     * @throws ValidationException Si el sector esta vacio, si alguna cantidad
     *                              es negativa, si ningun tipo tiene cantidad
     *                              mayor a 0, o si el alta choco con otra
     *                              concurrente.
     */
    List<CocheraResponseDto> crearPorPlanta(CocheraAltaPorPlantaDto dto);

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
    List<CocheraResponseDto> listar(String sector, CocheraTipo tipo, CocheraEstado estado,
                                    LocalDateTime desde, LocalDateTime hasta);

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
     *
     * <p>Version sin datos de quien pide: no filtra las ACCESIBLE. Equivale a
     * la consulta de un anonimo.
     */
    default List<CocheraResponseDto> listarDisponibles(LocalDateTime desde, LocalDateTime hasta,
                                                       VehiculoTipo tipoVehiculo) {
        return listarDisponibles(desde, hasta, tipoVehiculo, null, false);
    }

    /**
     * Igual que {@link #listarDisponibles(LocalDateTime, LocalDateTime, VehiculoTipo)},
     * pero ademas saca las cocheras ACCESIBLE cuando quien pide es un visitante
     * que no tiene declarada una discapacidad: no tiene sentido ofrecerle una
     * cochera que despues la reserva le va a rechazar.
     *
     * <p>No se filtra cuando:
     * <ul>
     *     <li>quien pide es anonimo ({@code requesterEmail} null): el endpoint
     *     es publico y no hay persona contra la cual comparar;</li>
     *     <li>quien pide es ADMIN: suele estar reservando para otra persona,
     *     cuya declaracion se valida al crear la reserva.</li>
     * </ul>
     *
     * <p>Esto es solo una comodidad de la pantalla: la regla real la aplica
     * igual la creacion de la reserva.
     */
    List<CocheraResponseDto> listarDisponibles(LocalDateTime desde, LocalDateTime hasta,
                                               VehiculoTipo tipoVehiculo,
                                               String requesterEmail, boolean requesterIsAdmin);
}