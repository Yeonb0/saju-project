package com.sajuppugi.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.infrastructure.JdbcPaymentReconciliation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "app.toss.webhooks-enabled", havingValue = "true")
public class TossWebhookController {
    private final JdbcPaymentReconciliation jobs;
    private final ObjectMapper mapper;
    private final Clock clock;
    public TossWebhookController(JdbcPaymentReconciliation jobs, ObjectMapper mapper, Clock clock) { this.jobs = jobs; this.mapper = mapper; this.clock = clock; }
    @PostMapping(value = "/api/v1/webhooks/toss", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(HttpServletRequest request) {
        UUID order;
        try {
            byte[] body = request.getInputStream().readNBytes(16385);
            if (body.length > 16384) throw new ApiException(ErrorCode.INVALID_REQUEST);
            JsonNode event = mapper.readTree(body);
            if (!"PAYMENT_STATUS_CHANGED".equals(event.path("eventType").asText())) return ResponseEntity.ok().build();
            order = UUID.fromString(event.path("data").path("orderId").asText());
        } catch (Exception failure) { throw new ApiException(ErrorCode.INVALID_REQUEST); }
        // No paymentKey, payload, signature assumption or provider status is persisted/trusted.
        jobs.signal(order, clock.instant());
        return ResponseEntity.ok().build();
    }
}
