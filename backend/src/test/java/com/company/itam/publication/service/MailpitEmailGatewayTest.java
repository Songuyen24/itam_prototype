package com.company.itam.publication.service;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailpitEmailGatewayTest {
    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private final MailpitEmailGateway gateway = new MailpitEmailGateway(sender, "itam@itam.example");

    @Test
    void sendsUtf8BilingualBodyAndPdfAttachment() throws Exception {
        when(sender.createMimeMessage()).thenReturn(message);
        byte[] pdf = "%PDF-1.7 nội dung".getBytes(StandardCharsets.UTF_8);

        gateway.send("user@itam.example", "Hoàn tất / Completed", "Biên bản / Report",
                new EmailGateway.Attachment("report.pdf", "application/pdf", "reports/report.pdf",
                        pdf.length, "checksum", pdf));

        verify(sender).send(message);
        message.saveChanges();
        assertThat(message.getFrom()[0].toString()).isEqualTo("itam@itam.example");
        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("user@itam.example");
        assertThat(message.getSubject()).isEqualTo("Hoàn tất / Completed");
        var multipart = (MimeMultipart) message.getContent();
        assertThat(multipart.getCount()).isEqualTo(2);
        var body = (MimeMultipart) multipart.getBodyPart(0).getContent();
        assertThat(body.getBodyPart(0).getContent().toString()).contains("Biên bản / Report");
        assertThat(multipart.getBodyPart(1).getFileName()).isEqualTo("report.pdf");
        assertThat(multipart.getBodyPart(1).getContentType()).startsWith("application/pdf");
        assertThat(multipart.getBodyPart(1).getInputStream().readAllBytes()).isEqualTo(pdf);
    }

    @Test
    void rejectsInvalidAttachmentBeforeSending() {
        assertThatThrownBy(() -> gateway.send("user@itam.example", "Subject", "Body",
                new EmailGateway.Attachment("report.txt", "text/plain", "report.txt", 3, "checksum", new byte[]{1, 2, 3})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Attachment is invalid");
    }

    @Test
    void propagatesSmtpFailure() {
        when(sender.createMimeMessage()).thenReturn(message);
        var failure = new org.springframework.mail.MailSendException("SMTP unavailable");
        org.mockito.Mockito.doThrow(failure).when(sender).send(message);

        assertThatThrownBy(() -> gateway.send("user@itam.example", "Subject", "Body"))
                .isSameAs(failure);
    }

    @Test
    @EnabledIfSystemProperty(named = "mailpit.live", matches = "true")
    void sendsToLocalMailpit() {
        var liveSender = new JavaMailSenderImpl();
        liveSender.setHost("127.0.0.1");
        liveSender.setPort(1025);
        byte[] pdf = "%PDF-1.7 synthetic test".getBytes(StandardCharsets.UTF_8);

        new MailpitEmailGateway(liveSender, "itam@itam.example").send("mailpit-test@itam.example",
                "Kiểm tra Mailpit / Mailpit test", "Nội dung thử / Synthetic content",
                new EmailGateway.Attachment("mailpit-test.pdf", "application/pdf", "test/mailpit-test.pdf",
                        pdf.length, "synthetic", pdf));
    }
}
