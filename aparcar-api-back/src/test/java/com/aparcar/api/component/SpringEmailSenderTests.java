package com.aparcar.api.component;

import com.aparcar.api.component.impl.SpringEmailSender;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.email.PlainEmailData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@UnitTests
public class SpringEmailSenderTests {

    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private SpringEmailSender springEmailSender;

    @Test
    @DisplayName("sendPlainTextEmail should send email successfully")
    void sendPlainTextEmailShouldSendEmailSuccessfully() {
        // Arrange
        var emailData = new PlainEmailData("Test body", "Test Subject",
                List.of("john.doe@mail.com"));
        doNothing().when(javaMailSender).send(any(SimpleMailMessage.class));

        // Act
        springEmailSender.sendPlainTextEmail(emailData);

        // Assert
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("Test Subject", sentMessage.getSubject());
        assertEquals("Test body", sentMessage.getText());
        Assertions.assertNotNull(sentMessage.getTo());
        assertEquals(1, sentMessage.getTo().length);
        assertEquals("john.doe@mail.com", sentMessage.getTo()[0]);
    }

    @Test
    @DisplayName("sendPlainTextEmail should throw RuntimeException on error")
    void sendPlainTextEmailShouldThrowRuntimeExceptionOnError() {
        // Arrange
        var emailData = new PlainEmailData("Test body", "Test Subject",
                List.of("john.doe@mail.com"));
        doThrow(new MailSendException("boom")).when(javaMailSender).send(any(SimpleMailMessage.class));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> springEmailSender.sendPlainTextEmail(emailData));
        assertEquals(MailSendException.class.getName(), ex.getMessage());
    }
}
