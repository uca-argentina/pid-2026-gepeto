package com.aparcar.api.service.impl;

import com.aparcar.api.entity.sancion.ConfiguracionSanciones;
import com.aparcar.api.repository.ConfiguracionSancionesRepository;
import com.aparcar.api.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee y guarda la fila única de configuración del régimen de faltas.
 *
 * <p>La fila la siembra la migración 016, así que en condiciones normales
 * siempre existe. El {@code orElseGet} está para que una base a la que se le
 * borró la fila a mano siga funcionando con los valores por defecto, en vez de
 * romper todas las pantallas que dependen de leerla.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionSancionesService {

    private final ConfiguracionSancionesRepository repository;

    @Transactional(readOnly = true)
    public ConfiguracionSanciones obtener() {
        return repository.findById(ConfiguracionSanciones.ID_UNICA)
                .orElseGet(ConfiguracionSanciones::porDefecto);
    }

    @Transactional
    public ConfiguracionSanciones guardar(int maxFaltas, int diasBloqueo, int mesesVentana,
                                          int toleranciaIngresoMin, int toleranciaEgresoMin) {
        // Los límites de arriba no son arbitrarios: evitan que un cero o un
        // negativo dejen el sistema en un estado absurdo, como bloquear a
        // cualquiera con su primera falta o un bloqueo que no termina nunca.
        exigirEntre(maxFaltas, 1, 50, "El máximo de faltas");
        exigirEntre(diasBloqueo, 1, 3650, "La duración del bloqueo en días");
        exigirEntre(mesesVentana, 1, 120, "La ventana en meses");
        exigirEntre(toleranciaIngresoMin, 0, 1440, "La tolerancia de ingreso en minutos");
        exigirEntre(toleranciaEgresoMin, 0, 1440, "La tolerancia de egreso en minutos");

        ConfiguracionSanciones config = repository
                .findById(ConfiguracionSanciones.ID_UNICA)
                .orElseGet(ConfiguracionSanciones::porDefecto);

        config.setMaxFaltas(maxFaltas);
        config.setDiasBloqueo(diasBloqueo);
        config.setMesesVentana(mesesVentana);
        config.setToleranciaIngresoMin(toleranciaIngresoMin);
        config.setToleranciaEgresoMin(toleranciaEgresoMin);
        return repository.save(config);
    }

    private static void exigirEntre(int valor, int minimo, int maximo, String nombre) {
        if (valor < minimo || valor > maximo) {
            throw new ValidationException(
                    "%s tiene que estar entre %d y %d.".formatted(nombre, minimo, maximo));
        }
    }
}
