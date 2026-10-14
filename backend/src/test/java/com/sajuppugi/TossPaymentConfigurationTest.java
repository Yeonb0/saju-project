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
            assertThat(context.getStartupFailure()).hasRootCauseMessage("Toss API environment, credentials or timeouts are invalid");
        });
    }

    @Test void bothOfficialTestSecretKeyTypesWorkInStagingWithoutNetworkCalls() {
        for (String key : java.util.List.of("test_gsk_synthetic", "test_sk_synthetic")) {
            runner.withPropertyValues("spring.profiles.active=staging", "app.toss.adapter=api", "app.toss.secret-key=" + key)
                    .run(context -> assertThat(context).hasSingleBean(TossPaymentProvider.class));
        }
    }

    @Test void productionNeedsLiveKeyOfficialHostAndExplicitOptIn() {
        for (String key : java.util.List.of("live_gsk_synthetic", "live_sk_synthetic")) {
            runner.withPropertyValues("spring.profiles.active=production", "app.toss.adapter=api", "app.toss.secret-key=" + key)
                    .run(context -> assertThat(context).hasFailed());
            runner.withPropertyValues("spring.profiles.active=production", "app.toss.adapter=api", "app.toss.live-enabled=true", "app.toss.secret-key=" + key)
                    .run(context -> assertThat(context).hasSingleBean(TossPaymentProvider.class));
        }
    }

    @Test void sharedEnvironmentsRejectMixedModesAndLiveKeysNeverReachLoopback() {
        for (String profile : java.util.List.of("staging", "local", "test")) {
            runner.withPropertyValues("spring.profiles.active=" + profile, "app.toss.adapter=api", "app.toss.secret-key=live_gsk_synthetic")
                    .run(context -> assertThat(context).hasFailed());
            runner.withPropertyValues("spring.profiles.active=" + profile, "app.toss.adapter=api", "app.toss.live-enabled=true", "app.toss.secret-key=test_gsk_synthetic")
                    .run(context -> assertThat(context).hasFailed());
        }
        for (String profile : java.util.List.of("production", "production,test", "production,local", "production,staging")) {
            runner.withPropertyValues("spring.profiles.active=" + profile, "app.toss.adapter=api", "app.toss.live-enabled=true", "app.toss.secret-key=test_gsk_synthetic")
                    .run(context -> assertThat(context).hasFailed());
        }
        for (String profile : java.util.List.of("production,test", "production,local", "production,staging")) {
            runner.withPropertyValues("spring.profiles.active=" + profile, "app.toss.adapter=api", "app.toss.live-enabled=true", "app.toss.secret-key=live_gsk_synthetic")
                    .run(context -> assertThat(context).hasFailed());
        }
        runner.withPropertyValues("spring.profiles.active=production,test", "app.toss.adapter=api", "app.toss.live-enabled=true", "app.toss.secret-key=live_gsk_synthetic", "app.toss.base-url=http://127.0.0.1:12345")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test void invalidConfigurationDoesNotEchoSecretOrConfiguredUrl() {
        runner.withPropertyValues("app.toss.adapter=api", "app.toss.secret-key=synthetic-private-marker", "app.toss.base-url=https://synthetic-private-host.invalid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseMessage("Toss API environment, credentials or timeouts are invalid");
                    assertThat(context.getStartupFailure().getCause().getMessage()).doesNotContain("synthetic-private-marker", "synthetic-private-host");
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
