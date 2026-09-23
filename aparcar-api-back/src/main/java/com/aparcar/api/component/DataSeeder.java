package com.aparcar.api.component;

import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

import static com.aparcar.api.config.ApplicationConstants.DEV_ENV;

/**
 * Siembra las cuentas de arranque en desarrollo.
 *
 * <p>Hace falta porque la migracion que unifico visitante y cuenta vacio la
 * tabla: sin esto no quedaria ningun ADMIN con el que entrar al panel, y el
 * alta de visitantes solo la puede hacer un ADMIN.
 *
 * <p>Solo corre si la tabla esta vacia, asi que no pisa nada de lo que se cargue
 * despues.
 */
@Slf4j
@Component
@Profile(DEV_ENV)
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final VisitanteRepository visitanteRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (visitanteRepository.count() > 0) {
            return;
        }

        visitanteRepository.save(new Visitante(
                "Mateo",
                "44563913",
                "mateo@test.com",
                passwordEncoder.encode("mateoocho"),
                null,
                Set.of(AppAuthority.ADMIN),
                true));

        // Visitante de prueba para poder abrir el dashboard-user sin tener que
        // darlo de alta a mano. Sin vehiculo: los carga el mismo desde su panel.
        visitanteRepository.save(new Visitante(
                "Visitante de prueba",
                "12345678",
                "visitante@test.com",
                passwordEncoder.encode("12345678"),
                null,
                Set.of(AppAuthority.USER),
                true));

        log.info("Cuentas de arranque sembradas: mateo@test.com (ADMIN) y visitante@test.com (USER).");
    }
}
