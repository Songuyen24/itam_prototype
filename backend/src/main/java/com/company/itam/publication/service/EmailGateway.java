package com.company.itam.publication.service;

public interface EmailGateway {
    void send(String recipient, String subject, String content);
}
