package com.aparcar.api.component.impl;

import com.aparcar.api.component.IEmailSender;
import com.aparcar.api.dto.email.PlainEmailData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringEmailSender implements IEmailSender {
    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Override
    public void sendPlainTextEmail(PlainEmailData emailData) {
        SimpleMailMessage message = new SimpleMailMessage();

        String[] recipients = emailData.getRecipients().toArray(new String[0]);
        message.setTo(recipients);
        message.setSubject(emailData.getSubject());
        message.setText(emailData.getBody());
        message.setFrom(mailFrom);

        try {
            javaMailSender.send(message);
            log.info("Contact email sent: {}", emailData.getSubject());
        } catch (Exception e) {
            log.error("Error sending plain text email: {}", e.getMessage());
            throw new RuntimeException(e.getClass().getName(), e);
        }
    }
}
