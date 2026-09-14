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
}
