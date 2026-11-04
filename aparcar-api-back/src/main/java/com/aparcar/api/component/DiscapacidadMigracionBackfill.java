package com.aparcar.api.component;

import com.aparcar.api.service.IVerificacionDiscapacidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, les da un plazo de gracia a las cuentas que se habian
 * autodeclarado con discapacidad antes de la verificacion con certificado.
 *
 * <p>No pierden el acceso de golpe ni lo conservan para siempre: durante
 * {@code aparcar.discapacidad.gracia-migracion-dias} siguen pudiendo reservar
 * cocheras ACCESIBLE y ven en su perfil que tienen que subir el certificado.
 * Pasado el plazo sin una solicitud en revision, el barrido les quita el
 * acceso. Es idempotente: una cuenta que ya tiene plazo no se vuelve a tocar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscapacidadMigracionBackfill implements ApplicationRunner {
    private final IVerificacionDiscapacidadService service;

    @Override
    public void run(ApplicationArguments args) {
        int migradas = service.migrarAutodeclarados();
        if (migradas > 0) {
            log.info("Se dio plazo de gracia a {} cuentas con discapacidad autodeclarada.", migradas);
        }
    }
}
