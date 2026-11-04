package com.aparcar.api.service.impl;

import com.aparcar.api.entity.puntos.BilleteraPuntos;
import com.aparcar.api.repository.BilleteraPuntosRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Crea la billetera la primera vez que hace falta, en su propia transaccion.
 *
 * <p>Asi dos ingresos simultaneos de la misma persona no se pisan: si los dos
 * intentan crearla, uno gana y el otro choca con la restriccion unica, que se
 * ignora porque la billetera ya existe. Despues, la operacion la bloquea.
 */
@Component
public class CreadorBilleteras {
    private final BilleteraPuntosRepository billeteraRepository;
    private final TransactionTemplate nuevaTransaccion;

    public CreadorBilleteras(BilleteraPuntosRepository billeteraRepository, PlatformTransactionManager transactionManager) {
        this.billeteraRepository = billeteraRepository;
        this.nuevaTransaccion = new TransactionTemplate(transactionManager);
        this.nuevaTransaccion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void crearSiFalta(UUID visitanteId) {
        try {
            nuevaTransaccion.executeWithoutResult(status -> {
                if (!billeteraRepository.existsByVisitanteId(visitanteId)) {
                    billeteraRepository.saveAndFlush(new BilleteraPuntos(visitanteId));
                }
            });
        } catch (DataIntegrityViolationException e) {
            // Otra transaccion la creo primero: la que importa ya existe. Se
            // atrapa afuera para que la transaccion fallida ya este cerrada.
        }
    }
}
