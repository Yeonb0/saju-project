package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import com.sajuppugi.wallet.application.RemainingTopUpRefund;
import org.junit.jupiter.api.Test;

class RemainingRefundAmountTest {
    @Test void roundsUpToWonUsesOriginalSnapshotAndCannotOverflow() {
        assertThat(RemainingTopUpRefund.proratedAmount(5000, 500, 400)).isEqualTo(4000);
        assertThat(RemainingTopUpRefund.proratedAmount(1000, 3, 1)).isEqualTo(334);
        assertThat(RemainingTopUpRefund.proratedAmount(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> RemainingTopUpRefund.proratedAmount(1000, 100, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RemainingTopUpRefund.proratedAmount(1000, 100, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
