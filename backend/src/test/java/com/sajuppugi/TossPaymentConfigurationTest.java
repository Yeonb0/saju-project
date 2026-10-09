package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.payment.infrastructure.TossPaymentProperties;
import com.sajuppugi.payment.infrastructure.TossPaymentProvider;
import com.sajuppugi.payment.port.PaymentProviderPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

class TossPaymentConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues("app.toss.base-url=https://api.tosspayments.com",
                    "app.toss.connect-timeout=3s", "app.toss.read-timeout=10s");

    @Test
    void noAdapterByDefaultOrWhenDisabled() {
        runner.run(context -> assertThat(context).doesNotHaveBean(PaymentProviderPort.class));
        runner.withPropertyValues("app.toss.adapter=disabled")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentProviderPort.class));
    }

    @Test
    void explicitTestConfigurationCreatesAdapterWithoutCallingProvider() {
        runner.withPropertyValues("app.toss.adapter=api", "app.toss.secret-key=test_gsk_synthetic")
                .run(context -> assertThat(context).hasSingleBean(TossPaymentProvider.class));
    }

    @Test
    void enablingWithoutSecretFailsClosed() {
        runner.withPropertyValues("app.toss.adapter=api").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Toss test API configuration is invalid");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TossPaymentProperties.class)
    @Import(TossPaymentProvider.class)
    static class Config {
        @Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }
        @Bean ObjectMapper mapper() { return new ObjectMapper(); }
    }
}
