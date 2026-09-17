package com.company.itam.publication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LocalEmailGateway implements EmailGateway {
    private static final Logger log = LoggerFactory.getLogger(LocalEmailGateway.class);

    @Override
    public void send(String recipient, String subject, String content) {
        if (recipient == null || recipient.isBlank()) throw new IllegalArgumentException("Recipient is missing");
        log.info("Simulated email to {} with subject {}", recipient, subject);
    }

    @Override
    public void send(String recipient, String subject, String content, Attachment attachment) {
        send(recipient, subject, content);
        if (attachment == null) return;
        byte[] bytes = attachment.content();
        if (attachment.fileName() == null || !attachment.fileName().endsWith(".pdf")
                || !"application/pdf".equals(attachment.mimeType())
                || attachment.storagePath() == null || attachment.storagePath().isBlank()
                || bytes == null || bytes.length != attachment.fileSize()
                || bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new IllegalArgumentException("Attachment is invalid");
        }
        log.info("Simulated attachment {} ({} bytes) from {}", attachment.fileName(), bytes.length,
                attachment.storagePath());
    }
}
