package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import com.sajuppugi.infrastructure.PrelaunchDeploymentGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PrelaunchDeploymentGuardTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PrelaunchDeploymentGuard.class)
            .withPropertyValues("spring.profiles.active=staging,prelaunch",
                    "DB_URL=jdbc:postgresql://db.example.test/saju", "DB_USERNAME=synthetic",
                    "DB_PASSWORD=synthetic", "KAKAO_REST_API_KEY=synthetic", "KAKAO_CLIENT_SECRET=synthetic",
                    "LINER_API_KEY=synthetic", "PERSON_ENCRYPTION_KEY=synthetic",
                    "GENERATION_CONTEXT_ENCRYPTION_KEY=synthetic",
                    "FRONTEND_BASE_URL=https://frontend.example.test",
                    "KAKAO_REDIRECT_URI=https://frontend.example.test/api/v1/auth/kakao/callback");

    @Test
    void realProfileDefaultsAllowNonPaymentDeploymentWithoutPgCredentials() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PrelaunchDeploymentGuard.class);
            assertThat(context.getEnvironment().getProperty("server.shutdown")).isEqualTo("graceful");
            assertThat(context.getEnvironment().getProperty("app.toss.adapter")).isEqualTo("disabled");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOSS_RECOVERY_ENABLED", "TOSS_REFUNDS_ENABLED", "TOSS_OPERATIONS_ENABLED",
            "TOSS_WEBHOOKS_ENABLED", "WALLET_PURCHASES_ENABLED", "WALLET_RECOVERY_ENABLED",
            "GIFT_ORDERS_ENABLED", "GIFT_PURCHASES_ENABLED", "ADMIN_OPERATIONS_ENABLED",
            "READING_GENERATION_RECOVERY_ENABLED", "TALISMAN_RENDER_ENABLED"})
    void environmentOverridesCannotEnableMoneyMovementOrIncompleteFeatures(String flag) {
        runner.withPropertyValues(flag + "=true").run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prelaunch", "local,staging,prelaunch", "test,staging,prelaunch",
            "production,staging,prelaunch", "prod,staging,prelaunch"})
    void rejectsWrongOrMixedProfiles(String profiles) {
        runner.withPropertyValues("spring.profiles.active=" + profiles)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOSS_ADAPTER=api", "LINER_ADAPTER=fake", "LINER_API_KEY=",
            "KAKAO_CLIENT_SECRET=", "DB_URL=jdbc:h2:mem:wrong",
            "FRONTEND_BASE_URL=http://frontend.example.test",
            "KAKAO_REDIRECT_URI=https://backend.example.test/api/v1/auth/kakao/callback",
            "KAKAO_REDIRECT_URI=https://frontend.example.test/wrong",
            "FRONTEND_BASE_URL=https://frontend.example.test/subpath"})
    void rejectsUnsafeOrIncompleteSettings(String setting) {
        runner.withPropertyValues(setting).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidUrlFailureDoesNotExposeItsValue() {
        runner.withPropertyValues("FRONTEND_BASE_URL=synthetic-private-value [bad]").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("valid HTTPS URL")
                    .hasStackTraceContaining("app.kakao-oauth.frontend-base-url");
            assertThat(context.getStartupFailure().getCause().getMessage())
                    .doesNotContain("synthetic-private-value");
        });
    }
}
