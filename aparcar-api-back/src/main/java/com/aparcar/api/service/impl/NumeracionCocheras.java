package com.aparcar.api.service.impl;

import com.aparcar.api.entity.reserva.CocheraTipo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numeracion automatica de cocheras: {@code {PREFIJO}-{secuencial}}.
 *
 * <ul>
 *     <li>Prefijos fijos por tipo: AUTO "A", MOTO "M", CARGA "C", ACCESIBLE "AC".</li>
 *     <li>El secuencial lleva ceros a la izquierda hasta 2 digitos como minimo,
 *     sin limite superior (A-09, A-10, ..., A-99, A-100).</li>
 *     <li>Se continua desde el maximo secuencial existente de ESE prefijo.</li>
 * </ul>
 *
 * <p>Para calcular el maximo solo cuentan los numeros que matchean exactamente
 * {@code ^PREFIJO-(\d+)$}. El guion tiene que venir justo despues del prefijo
 * completo, asi "AC-05" no cuenta para AUTO ("A") ni "A-05" para ACCESIBLE.
 * Cualquier numero con otro formato (datos viejos, cargados a mano) se ignora:
 * no participa del calculo y no rompe nada.
 *
 * <p>Es puro calculo, sin acceso a la base, para poder testearlo aislado.
 */
public final class NumeracionCocheras {

    private static final Map<CocheraTipo, Pattern> PATRONES = new EnumMap<>(CocheraTipo.class);

    static {
        for (CocheraTipo tipo : CocheraTipo.values()) {
            PATRONES.put(tipo, Pattern.compile("^" + Pattern.quote(prefijo(tipo)) + "-(\\d+)$"));
        }
    }

    private NumeracionCocheras() {
    }

    /**
     * El switch es exhaustivo a proposito: si mañana se agrega un tipo nuevo
     * al enum, esto deja de compilar hasta que se le asigne su prefijo.
     */
    public static String prefijo(CocheraTipo tipo) {
        return switch (tipo) {
            case AUTO -> "A";
            case MOTO -> "M";
            case CARGA -> "C";
            case ACCESIBLE -> "AC";
        };
    }

    /**
     * Mayor secuencial usado por ese tipo entre los numeros existentes, o 0 si
     * no hay ninguno con el formato del tipo.
     */
    public static long maximoExistente(CocheraTipo tipo, Collection<String> numerosExistentes) {
        Pattern patron = PATRONES.get(tipo);
        long maximo = 0;

        for (String numero : numerosExistentes) {
            if (numero == null) {
                continue;
            }
            Matcher matcher = patron.matcher(numero);
            if (!matcher.matches()) {
                continue;
            }
            try {
                maximo = Math.max(maximo, Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignorado) {
                // Un secuencial de mas de 18 digitos: no es un dato real,
                // se trata igual que cualquier otro formato desconocido.
            }
        }
        return maximo;
    }

    /**
     * Los proximos {@code cantidad} numeros para ese tipo, continuando desde el
     * maximo existente. Con cantidad 0 (o negativa) devuelve una lista vacia.
     */
    public static List<String> siguientes(CocheraTipo tipo, int cantidad, Collection<String> numerosExistentes) {
        long ultimo = maximoExistente(tipo, numerosExistentes);
        List<String> numeros = new ArrayList<>(Math.max(cantidad, 0));
        for (int i = 1; i <= cantidad; i++) {
            numeros.add(formatear(tipo, ultimo + i));
        }
        return numeros;
    }

    public static String formatear(CocheraTipo tipo, long secuencial) {
        return "%s-%02d".formatted(prefijo(tipo), secuencial);
    }
}