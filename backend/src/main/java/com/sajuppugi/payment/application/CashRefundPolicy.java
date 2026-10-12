package com.sajuppugi.payment.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CashRefundPolicy {
    private final boolean enabled;
    public CashRefundPolicy(@Value("${app.toss.refunds-enabled:false}") boolean enabled) { this.enabled = enabled; }
    public boolean enabled() { return enabled; }
    // Deliberately narrower than unresolved partial-refund/fee policy. Approval is an explicit deployment gate.
    public boolean allowsFullUnused(Instant created, Instant now) {
        return enabled && !now.isBefore(created) && !now.isAfter(created.plus(Duration.ofDays(7)));
    }
}
