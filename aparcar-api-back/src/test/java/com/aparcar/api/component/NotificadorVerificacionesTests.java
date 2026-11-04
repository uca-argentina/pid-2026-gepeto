package com.aparcar.api.component;

import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.email.PlainEmailData;
import com.aparcar.api.entity.discapacidad.VerificacionEstado;
import com.aparcar.api.repository.VisitanteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Los avisos por mail: a quien van y que nunca llevan datos de salud. */
@UnitTests
class NotificadorVerificacionesTests {
    @Mock
    private IEmailSender emailSender;

    @Mock
    private VisitanteRepository visitanteRepository;

    private NotificadorVerificaciones notificador(boolean activo) {
        return new NotificadorVerificaciones(emailSender, visitanteRepository,
                new DiscapacidadProperties(5, 30, 30, "x", 5, activo));
    }

    private PlainEmailData enviado() {
        ArgumentCaptor<PlainEmailData> captor = ArgumentCaptor.forClass(PlainEmailData.class);
        verify(emailSender).sendPlainTextEmail(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("una solicitud nueva avisa a los admins activos, sin nombre ni datos del solicitante")
    void avisaALosAdmins() {
        when(visitanteRepository.findEmailsDeAdminsActivos()).thenReturn(List.of("a@test.com", "b@test.com"));

        notificador(true).solicitudNueva();

        PlainEmailData mail = enviado();
        assertEquals(List.of("a@test.com", "b@test.com"), mail.getRecipients());
        assertTrue(mail.getBody().contains("Verificaciones"));
        assertFalse(mail.getBody().toLowerCase().contains("certificado adjunto"));
    }

    @Test
    @DisplayName("el rechazo le llega al visitante con el motivo")
    void rechazoConMotivo() {
        notificador(true).resultado("juan@test.com", VerificacionEstado.RECHAZADA, "No se lee el número.");

        PlainEmailData mail = enviado();
        assertEquals(List.of("juan@test.com"), mail.getRecipients());
        assertTrue(mail.getBody().contains("No se lee el número."));
    }

    @Test
    @DisplayName("con los avisos desactivados no se manda nada")
    void desactivado() {
        notificador(false).solicitudNueva();
        notificador(false).resultado("juan@test.com", VerificacionEstado.VERIFICADA, null);

        verify(emailSender, never()).sendPlainTextEmail(any());
    }

    @Test
    @DisplayName("si el correo falla, la operacion no se entera")
    void fallaElCorreo() {
        doThrow(new RuntimeException("smtp")).when(emailSender).sendPlainTextEmail(any());

        assertDoesNotThrow(() -> notificador(true).resultado("juan@test.com", VerificacionEstado.VERIFICADA, null));
    }

    @Test
    @DisplayName("sin admins activos, no hay a quien avisar")
    void sinAdmins() {
        when(visitanteRepository.findEmailsDeAdminsActivos()).thenReturn(List.of());

        notificador(true).solicitudNueva();

        verify(emailSender, never()).sendPlainTextEmail(any());
    }
}
