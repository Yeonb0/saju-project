package com.sajuppugi.infrastructure;

import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Fail closed when a first shared deployment accidentally enables money movement. */
@Component
@Profile("prelaunch")
public class PrelaunchDeploymentGuard {
    private static final List<String> DISABLED_FLAGS = List.of(
            "app.toss.refunds-enabled", "app.toss.webhooks-enabled", "app.toss.operations-enabled",
            "app.toss.recovery.enabled", "app.wallet.operations-enabled", "app.wallet.purchases-enabled",
            "app.wallet.recovery.enabled", "app.gift.orders-enabled", "app.gift.purchases-enabled",
            "app.gift.recovery-enabled", "app.gift.delivery-enabled", "app.gift.delivery-worker-enabled",
            "app.gift.phone-cleanup-enabled", "app.gift.fulfillment-review-enabled",
            "app.gift.retention-enabled", "app.gift.payload-reencryption-enabled",
            "app.admin.enabled", "app.admin.recovery-enabled", "app.operations.alerts.enabled",
            "app.reading.generation-recovery.enabled", "app.reading.generation-context-reencryption.enabled",
            "app.talisman.render.enabled", "springdoc.api-docs.enabled", "springdoc.swagger-ui.enabled");

    public PrelaunchDeploymentGuard(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of("staging"))
                || environment.acceptsProfiles(Profiles.of("local", "test", "production", "prod"))) {
            throw new IllegalStateException("prelaunch requires staging and excludes local/test/production/prod");
        }
        for (String flag : DISABLED_FLAGS) {
            if (environment.getProperty(flag, Boolean.class, false)) {
                throw new IllegalStateException("prelaunch requires disabled setting: " + flag);
            }
        }
        if (!"disabled".equals(environment.getProperty("app.toss.adapter", "disabled"))) {
            throw new IllegalStateException("prelaunch requires the payment adapter to be disabled");
        }
        if (!"api".equals(environment.getProperty("app.liner.adapter"))) {
            throw new IllegalStateException("prelaunch requires the real Liner adapter");
        }
        for (String property : List.of("app.kakao-oauth.rest-api-key", "app.kakao-oauth.client-secret",
                "app.liner.api-key", "app.person.encryption-key",
                "app.reading.generation-context-protection.encryption-key",
                "spring.datasource.username", "spring.datasource.password")) {
            if (environment.getProperty(property, "").isBlank()) {
                throw new IllegalStateException("prelaunch requires setting: " + property);
            }
        }
        if (!environment.getProperty("spring.datasource.url", "").startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("prelaunch requires PostgreSQL");
        }
        URI frontend = httpsUri(environment, "app.kakao-oauth.frontend-base-url");
        URI callback = httpsUri(environment, "app.kakao-oauth.redirect-uri");
        if (!(frontend.getPath().isEmpty() || frontend.getPath().equals("/"))
                || !frontend.getAuthority().equals(callback.getAuthority())
                || !"/api/v1/auth/kakao/callback".equals(callback.getPath())) {
            throw new IllegalStateException("prelaunch requires the Kakao callback on the frontend API proxy origin");
        }
    }

    private static URI httpsUri(Environment environment, String property) {
        try {
            URI uri = URI.create(environment.getProperty(property, ""));
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || "localhost".equalsIgnoreCase(uri.getHost())) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (IllegalArgumentException invalid) {
            // Never include the configured value or parser exception (which can contain it).
            throw new IllegalStateException("prelaunch requires a valid HTTPS URL: " + property);
        }
    }
}
