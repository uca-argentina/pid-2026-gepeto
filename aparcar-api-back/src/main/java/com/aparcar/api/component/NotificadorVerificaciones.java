package com.aparcar.api.component;

import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.dto.email.PlainEmailData;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.repository.VisitanteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Avisos por mail de la verificacion de discapacidad, con el mismo
 * {@link IEmailSender} que la recuperacion de cuenta.
 *
 * <p>Los mails nunca llevan datos de salud ni el certificado: al admin solo le
 * avisan que hay una solicitud para revisar en el panel, y al visitante el
 * resultado (y el motivo, si fue rechazada o revocada). Corren en segundo
 * plano y despues de confirmar la operacion: si el correo no esta configurado
 * o falla, se registra y la operacion sigue valida. La pantalla del admin y el
 * perfil muestran lo mismo sin depender del mail.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificadorVerificaciones {
    private final IEmailSender emailSender;
    private final VisitanteRepository visitanteRepository;
    private final DiscapacidadProperties properties;

    @Async
    public void solicitudNueva() {
        if (!properties.notificarPorMail()) return;
        List<String> admins = visitanteRepository.findEmailsDeAdminsActivos();
        if (admins.isEmpty()) return;
        enviar(new PlainEmailData("""
                Hay una solicitud nueva de verificación de discapacidad pendiente de revisión.

                Revisala en el panel de administración, en "Verificaciones".
                """, "AparcAR - Verificación pendiente", admins));
    }

    @Async
    public void resultado(String email, VerificacionEstado estado, String motivo) {
        if (!properties.notificarPorMail()) return;
        String cuerpo = switch (estado) {
            case VERIFICADA -> """
                    Tu certificado fue verificado. Ya podés reservar cocheras accesibles.
                    """;
            case RECHAZADA -> """
                    Tu solicitud de verificación fue rechazada.

                    Motivo: %s

                    Podés volver a enviar el certificado desde "Mis datos".
                    """.formatted(motivo);
            case REVOCADA -> """
                    Se revocó tu verificación para usar cocheras accesibles.

                    Motivo: %s
                    """.formatted(motivo);
            case VENCIDA -> """
                    Venció la verificación de tu certificado. Para seguir usando cocheras
                    accesibles, subí uno vigente desde "Mis datos".
                    """;
            default -> null;
        };
        if (cuerpo == null) return;
        enviar(new PlainEmailData(cuerpo, "AparcAR - Verificación de discapacidad", List.of(email)));
    }

    private void enviar(PlainEmailData mail) {
        try {
            emailSender.sendPlainTextEmail(mail);
        } catch (RuntimeException e) {
            log.warn("No se pudo enviar el aviso de verificacion: {}", e.getMessage());
        }
    }
}
