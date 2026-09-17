package com.company.itam.publication.service;

public interface EmailGateway {
    void send(String recipient, String subject, String content);

    default void send(String recipient, String subject, String content, Attachment attachment) {
        send(recipient, subject, content);
    }

    record Attachment(String fileName, String mimeType, String storagePath, long fileSize,
                      String checksum, byte[] content) {
        public Attachment {
            content = content == null ? null : content.clone();
        }

        @Override public byte[] content() { return content == null ? null : content.clone(); }
    }
}
