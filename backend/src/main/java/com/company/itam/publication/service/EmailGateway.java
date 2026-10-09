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
            if (fileName == null || !fileName.endsWith(".pdf")
                    || !"application/pdf".equals(mimeType)
                    || storagePath == null || storagePath.isBlank()
                    || content == null || content.length != fileSize
                    || content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                    || content[3] != 'F' || content[4] != '-') {
                throw new IllegalArgumentException("Attachment is invalid");
            }
        }

        @Override public byte[] content() { return content == null ? null : content.clone(); }
    }
}
