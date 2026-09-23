package com.aparcar.api.component;

import com.aparcar.api.dto.email.PlainEmailData;

/**
 * Interface for sending email notifications.
 */
public interface IEmailSender {
    /**
     * Sends a plain text email.
     *
     * @param plainEmailData The email data including recipients, subject, and body.
     */
    void sendPlainTextEmail(PlainEmailData plainEmailData);
}
