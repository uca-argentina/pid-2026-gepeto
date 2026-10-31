package com.aparcar.api.service;

import com.aparcar.api.dto.puntos.BilleteraResponseDto;
import com.aparcar.api.dto.puntos.MovimientoPuntosResponseDto;
import com.aparcar.api.dto.puntos.ResumenPuntosResponseDto;
import com.aparcar.api.dto.reserva.CotizacionResponseDto;
import com.aparcar.api.dto.reserva.ReservaPuntosDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Reserva;
import com.aparcar.api.exception.ConflictoException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.service.impl.ReglasPuntos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Gamificacion: puntos por franja horaria, racha y canje como descuento.
 *
 * <p>Ningun endpoint acredita puntos: se ganan solo al registrar el ingreso de
 * una reserva ({@link #acreditarIngreso}), dentro de la misma transaccion.
 */
public interface IPuntosService {
    BilleteraResponseDto billetera(String email);

    /** Los propios, mas recientes primero (hasta 100). */
    List<MovimientoPuntosResponseDto> movimientos(String email);

    ResumenPuntosResponseDto resumen();

    /**
     * Agrega a una cotizacion el descuento por puntos de quien cotiza y lo que
     * sumaria al ingresar. Sin puntos pedidos, el precio no cambia.
     *
     * @param puntos    Puntos a usar, como llegaron (tienen que ser un entero positivo), o null.
     * @throws ValidationException Si los puntos no son un entero positivo, superan el saldo o el tope.
     */
    CotizacionResponseDto cotizar(CotizacionResponseDto base, String email, LocalDateTime desde, LocalDateTime hasta,
                                  BigDecimal puntos, boolean usarTodos);

    /**
     * Valida un canje y bloquea la billetera hasta el fin de la transaccion,
     * para que nadie gaste esos mismos puntos en paralelo.
     *
     * @throws ValidationException Si no son un entero positivo, superan el saldo o el tope.
     */
    ReglasPuntos.Descuento prepararCanje(Visitante titular, BigDecimal subtotal, BigDecimal puntos, boolean usarTodos);

    /** Descuenta los puntos ya validados, con la reserva guardada (necesita su id). */
    void confirmarCanje(Visitante titular, Reserva reserva, ReglasPuntos.Descuento descuento);

    /** Devuelve los puntos canjeados de una reserva cancelada antes de usarla. Una sola vez. */
    void reintegrar(Reserva reserva);

    /**
     * Suma los puntos de franja y cuenta el dia en la racha, al registrar el
     * ingreso. Idempotente: una reserva acredita una sola vez.
     *
     * @throws ConflictoException Si otra operacion cambio la billetera al mismo tiempo.
     */
    void acreditarIngreso(Reserva reserva, LocalDateTime ingreso);

    /** Lo que se muestra de los puntos de una reserva. Sin tocar la base. */
    ReservaPuntosDto deReserva(Reserva reserva);
}
