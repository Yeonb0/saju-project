package com.sajuppugi.payment.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.toss")
public record TossPaymentProperties(String baseUrl, String secretKey, Duration connectTimeout, Duration readTimeout) {
    @Override
    public String toString() { return "TossPaymentProperties[secretKey=REDACTED]"; }
}
