package com.aparcar.api.entity.sancion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Los números con los que funciona el régimen de faltas, editables por el
 * administrador desde el panel.
 *
 * <p>Es una tabla de una sola fila. Podría haber sido un archivo de
 * configuración, como {@code aparcar.acceso.*}, pero eso obliga a redesplegar
 * para cambiar un número: acá hacía falta que el administrador los ajuste en
 * caliente, sin tocar el servidor.
 *
 * <p>La fila se identifica con {@link #ID_UNICA}, una constante. Es lo que
 * garantiza que no pueda haber dos configuraciones distintas conviviendo: no
 * depende de que nadie se acuerde de no insertar una segunda.
 *
 * <p>Incluye la ventana en la que se cuentan las faltas. Es el parámetro más
 * delicado de los cinco: acortarla perdona faltas que hoy cuentan, y alargarla
 * puede bloquear cuentas de inmediato, porque vuelve a traer al conteo faltas
 * que ya habían salido. No reescribe nada —el historial es el mismo— pero
 * cambia qué parte de él pesa.
 */
@Entity
@Table(name = "configuracion_sanciones")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionSanciones {

    /** La fila única. Cualquier lectura o escritura usa esta clave. */
    public static final int ID_UNICA = 1;


    @Id
    private Integer id = ID_UNICA;

    /**
     * Cuántas faltas se toleran dentro de la ventana.
     *
     * <p>Con el valor 3, la cuarta falta bloquea: el bloqueo ocurre al
     * <i>superar</i> este número, no al alcanzarlo.
     */
    @Column(nullable = false)
    private Integer maxFaltas;

    /** Cuántos días dura la inhabilitación de la cuenta. */
    @Column(nullable = false)
    private Integer diasBloqueo;

    /**
     * Meses hacia atrás en los que una falta todavía suma para el bloqueo.
     *
     * <p>No afecta al historial, que es permanente y muestra todo: solo decide
     * cuáles de esas faltas cuentan hoy.
     */
    @Column(nullable = false)
    private Integer mesesVentana;

    /**
     * Minutos después del inicio de la reserva antes de amonestar por no haber
     * registrado el ingreso.
     *
     * <p>No se confunde con {@code aparcar.acceso.ingresoToleranciaMin}, que
     * responde otra pregunta: hasta cuándo el QR sigue siendo válido para
     * entrar. Una cosa es hasta cuándo te dejan pasar y otra a partir de cuándo
     * te sancionan.
     */
    @Column(nullable = false)
    private Integer toleranciaIngresoMin;

    /** Lo mismo, contado desde el fin de la reserva, para el egreso. */
    @Column(nullable = false)
    private Integer toleranciaEgresoMin;

    @Version
    private Long version;

    /** Los valores con los que arranca una instalación nueva. */
    public static ConfiguracionSanciones porDefecto() {
        ConfiguracionSanciones config = new ConfiguracionSanciones();
        config.setId(ID_UNICA);
        config.setMaxFaltas(3);
        config.setDiasBloqueo(30);
        config.setMesesVentana(3);
        config.setToleranciaIngresoMin(15);
        config.setToleranciaEgresoMin(15);
        return config;
    }
}
