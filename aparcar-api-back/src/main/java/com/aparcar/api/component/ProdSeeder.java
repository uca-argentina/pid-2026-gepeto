package com.aparcar.api.component;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.CocheraTipo;
import com.aparcar.api.entity.reserva.Tarifa;
import com.aparcar.api.repository.TarifaRepository;
import com.aparcar.api.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Set;

import static com.aparcar.api.config.ApplicationConstants.PROD_ENV;

/**
 * Deja una base recien creada en condiciones de usarse.
 *
 * <p>Es el equivalente de {@link DataSeeder} para produccion, y existe porque
 * sin el un despliegue nuevo queda inutilizable: la migracion que unifico
 * visitante y cuenta vacio la tabla de personas, el alta de visitantes la hace
 * un ADMIN, y no hay forma de crear el primer ADMIN desde la aplicacion. El
 * resultado seria una pantalla de login contra una base sin una sola cuenta.
 *
 * <p>Se diferencia de {@link DataSeeder} en dos cosas, y las dos importan en un
 * entorno publico:
 *
 * <ul>
 *   <li>Las credenciales no estan escritas en el codigo: salen de las variables
 *       {@code ADMIN_EMAIL}, {@code ADMIN_PASSWORD} y {@code ADMIN_DOCUMENTO}.
 *       Si falta alguna, no inventa un usuario con clave conocida: no crea nada
 *       y lo avisa en el log.
 *   <li>No carga datos de prueba. Solo el ADMIN y la lista de precios.
 * </ul>
 *
 * <p>Cada parte mira si ya hay algo antes de escribir, asi que un reinicio del
 * servicio no pisa lo que se haya cargado desde el panel. Las cocheras no se
 * tocan: ya vienen cargadas por la migracion 002.
 */
@Slf4j
@Component
@Profile(PROD_ENV)
@RequiredArgsConstructor
public class ProdSeeder implements CommandLineRunner {
    private final VisitanteRepository visitanteRepository;
    private final TarifaRepository tarifaRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment env;

    @Override
    public void run(String... args) {
        sembrarAdmin();
        sembrarTarifas();
    }

    private void sembrarAdmin() {
        if (visitanteRepository.count() > 0) {
            return;
        }

        String email = env.getProperty("ADMIN_EMAIL");
        String password = env.getProperty("ADMIN_PASSWORD");
        String documento = env.getProperty("ADMIN_DOCUMENTO");

        if (esVacio(email) || esVacio(password) || esVacio(documento)) {
            log.warn("No hay ninguna cuenta y faltan ADMIN_EMAIL, ADMIN_PASSWORD o "
                    + "ADMIN_DOCUMENTO: no se creo el administrador inicial, asi que todavia "
                    + "no se puede iniciar sesion. Cargalas en el entorno y reinicia.");
            return;
        }

        visitanteRepository.save(new Visitante(
                env.getProperty("ADMIN_NOMBRE", "Administrador"),
                documento,
                email,
                passwordEncoder.encode(password),
                null,
                Set.of(AppAuthority.ADMIN),
                true));

        log.info("Administrador inicial creado: {}", email);
    }

    /**
     * Carga una lista de precios de arranque.
     *
     * <p>Hace falta porque cotizar busca la tarifa del tipo de cochera con
     * {@code orElseThrow}: con la tabla vacia, toda reserva falla. Los valores
     * son solo un punto de partida razonable; el admin los cambia desde
     * "Gestionar tarifas" y no se vuelven a tocar.
     */
    private void sembrarTarifas() {
        if (tarifaRepository.count() > 0) {
            return;
        }

        guardarTarifa(CocheraTipo.AUTO, "1000", "500", "4000", "7000");
        guardarTarifa(CocheraTipo.MOTO, "600", "300", "2400", "4200");
        guardarTarifa(CocheraTipo.ACCESIBLE, "1000", "500", "4000", "7000");
        guardarTarifa(CocheraTipo.CARGA, "1500", "750", "6000", "10500");

        log.info("Tarifas iniciales cargadas para los {} tipos de cochera.",
                CocheraTipo.values().length);
    }

    private void guardarTarifa(CocheraTipo tipo, String hora, String fraccion,
                               String mediaJornada, String jornadaCompleta) {
        Tarifa tarifa = new Tarifa();
        tarifa.setTipo(tipo);
        tarifa.setHora(new BigDecimal(hora));
        tarifa.setFraccion(new BigDecimal(fraccion));
        tarifa.setMediaJornada(new BigDecimal(mediaJornada));
        tarifa.setJornadaCompleta(new BigDecimal(jornadaCompleta));
        tarifaRepository.save(tarifa);
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
