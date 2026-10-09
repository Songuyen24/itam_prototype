package com.company.itam.publication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(prefix = "itam.email", name = "mode", havingValue = "mailpit")
public class MailpitEmailGateway implements EmailGateway {
    private final JavaMailSender sender;
    private final String from;

    public MailpitEmailGateway(JavaMailSender sender, @Value("${itam.email.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String recipient, String subject, String content) {
        send(recipient, subject, content, null);
    }

    @Override
    public void send(String recipient, String subject, String content, Attachment attachment) {
        if (recipient == null || recipient.isBlank()) throw new IllegalArgumentException("Recipient is missing");
        var message = sender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(message, attachment != null, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(content, false);
            if (attachment != null) {
                helper.addAttachment(attachment.fileName(), new ByteArrayResource(attachment.content()), attachment.mimeType());
            }
        } catch (jakarta.mail.MessagingException ex) {
            throw new org.springframework.mail.MailPreparationException(ex);
        }
        sender.send(message);
    }
}
