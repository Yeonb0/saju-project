package com.sajuppugi.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentOperationsSchedulerPropertiesTest {
    @Test void acceptsAConservativeBoundedPool() {
        assertThat(new PaymentOperationsSchedulerProperties(3).poolSize()).isEqualTo(3);
    }

    @Test void rejectsUnboundedOrZeroConcurrency() {
        assertThatThrownBy(() -> new PaymentOperationsSchedulerProperties(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentOperationsSchedulerProperties(9)).isInstanceOf(IllegalArgumentException.class);
    }
}
