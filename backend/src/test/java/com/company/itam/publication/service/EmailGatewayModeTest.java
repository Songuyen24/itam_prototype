package com.company.itam.publication.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmailGatewayModeTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(GatewayConfiguration.class)
            .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
            .withPropertyValues("itam.email.from=itam@itam.example");

    @Test
    void usesLocalGatewayByDefault() {
        context.run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).hasSingleBean(EmailGateway.class);
            assertThat(result).hasSingleBean(LocalEmailGateway.class);
        });
    }

    @Test
    void usesMailpitGatewayWhenSelected() {
        context.withPropertyValues("itam.email.mode=mailpit").run(result -> {
            assertThat(result).hasNotFailed();
            assertThat(result).hasSingleBean(EmailGateway.class);
            assertThat(result).hasSingleBean(MailpitEmailGateway.class);
        });
    }

    @Test
    void rejectsUnknownMode() {
        context.withPropertyValues("itam.email.mode=unknown").run(result -> assertThat(result).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({LocalEmailGateway.class, MailpitEmailGateway.class})
    static class GatewayConfiguration {
        @Bean String gatewayConsumer(EmailGateway gateway) { return gateway.getClass().getName(); }
    }
}
