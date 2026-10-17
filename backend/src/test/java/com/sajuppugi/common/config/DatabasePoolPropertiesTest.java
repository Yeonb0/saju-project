package com.sajuppugi.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabasePoolPropertiesTest {
    @Test void acceptsAConservativePoolAndBoundedConnectionWait() {
        var properties = new DatabasePoolProperties(10, 2, 5000, 1000);
        assertThat(properties.maxSize()).isEqualTo(10);
        assertThat(properties.minIdle()).isEqualTo(2);
    }

    @Test void rejectsPoolBudgetsThatCanOverrunOrCannotBeServed() {
        assertThatThrownBy(() -> new DatabasePoolProperties(0, 0, 5000, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabasePoolProperties(21, 2, 5000, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabasePoolProperties(4, 5, 5000, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabasePoolProperties(4, 1, 10_001, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DatabasePoolProperties(4, 1, 500, 1000)).isInstanceOf(IllegalArgumentException.class);
    }
}
