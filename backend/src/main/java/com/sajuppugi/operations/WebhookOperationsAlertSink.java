package com.sajuppugi.operations;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.operations.alerts.adapter", havingValue = "webhook")
public class WebhookOperationsAlertSink implements OperationsAlertSink {
    private final RestClient client;
    private final URI endpoint;
    public WebhookOperationsAlertSink(RestClient.Builder builder, OperationsAlertProperties properties, Environment environment) {
        try {
            endpoint = URI.create(properties.webhookUrl());
            boolean secure = "https".equals(endpoint.getScheme()) && endpoint.getHost() != null;
            boolean test = environment.acceptsProfiles(Profiles.of("test")) && !environment.acceptsProfiles(Profiles.of("staging", "production", "prod"))
                    && "http".equals(endpoint.getScheme()) && "127.0.0.1".equals(endpoint.getHost()) && endpoint.getPort() > 0;
            if ((!secure && !test) || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null
                    || properties.webhookToken() == null || properties.webhookToken().isBlank()) throw new IllegalStateException();
        } catch (RuntimeException failure) { throw new IllegalStateException("Operations alert webhook configuration is invalid"); }
        var factory = new SimpleClientHttpRequestFactory() {
            @Override protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method); connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(Duration.ofSeconds(3)); factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.requestFactory(factory).defaultHeader("Authorization", "Bearer " + properties.webhookToken())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).build();
    }
    @Override public boolean send(Alert alert) {
        try {
            return client.post().uri(endpoint).header("Idempotency-Key", alert.alertId().toString()).body(alert)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        response.getBody().readNBytes(1024);
                        return status == 200 || status == 201 || status == 202 || status == 204;
                    });
        } catch (RuntimeException failure) { return false; }
    }
}
