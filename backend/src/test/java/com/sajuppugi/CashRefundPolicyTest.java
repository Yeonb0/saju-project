package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import com.sajuppugi.payment.application.CashRefundPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CashRefundPolicyTest {
    @Test void requiresExplicitApprovalAndNeverAllowsFutureOrLateOrders() {
        Instant now = Instant.parse("2026-10-12T00:00:00Z");
        assertThat(new CashRefundPolicy(false).allowsFullUnused(now, now)).isFalse();
        var enabled = new CashRefundPolicy(true);
        assertThat(enabled.allowsFullUnused(now, now)).isTrue();
        assertThat(enabled.allowsFullUnused(now, now.plusSeconds(7L * 86400))).isTrue();
        assertThat(enabled.allowsFullUnused(now, now.plusSeconds(7L * 86400 + 1))).isFalse();
        assertThat(enabled.allowsFullUnused(now.plusSeconds(1), now)).isFalse();
    }
}
