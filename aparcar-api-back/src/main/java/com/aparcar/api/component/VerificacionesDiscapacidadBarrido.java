package com.aparcar.api.component;

import com.aparcar.api.service.IVerificacionDiscapacidadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

/**
 * Barrido de la verificacion de discapacidad, una vez por hora:
 *
 * <ul>
 *   <li>pasa a VENCIDA las verificaciones cuyo certificado vencio, y les quita
 *   el acceso a las cocheras ACCESIBLE;</li>
 *   <li>quita el acceso provisorio a las cuentas migradas cuyo plazo de gracia
 *   termino sin una solicitud en revision;</li>
 *   <li>borra los certificados que ya no hacen falta: los rechazados y
 *   cancelados enseguida, el resto pasada la retencion.</li>
 * </ul>
 *
 * <p>Cada paso es su propia transaccion: si uno falla, los otros corren igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificacionesDiscapacidadBarrido {
    private final IVerificacionDiscapacidadService service;

    @Scheduled(cron = "0 7 * * * *", zone = "${app.zona-horaria:America/Argentina/Buenos_Aires}")
    public void barrer() {
        paso("vencer verificaciones", service::vencerVerificaciones);
        paso("terminar accesos provisorios", service::terminarAccesosProvisorios);
        paso("retirar certificados", service::retirarDocumentos);
    }

    private void paso(String nombre, IntSupplier accion) {
        try {
            int cambios = accion.getAsInt();
            if (cambios > 0) log.info("Verificaciones de discapacidad: {} ({}).", nombre, cambios);
        } catch (RuntimeException e) {
            log.error("Fallo el barrido de verificaciones al {}: {}", nombre, e.getMessage());
        }
    }
}
