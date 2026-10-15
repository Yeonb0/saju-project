package com.sajuppugi.fortune.reading.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PersonalizedConcernTest {
    @Test
    void normalizesWhitespaceAndRedactsCommonDirectIdentifiers() {
        assertThat(PersonalizedConcern.normalize("  이직이  걱정돼요.  test@example.com / 010-1234-5678  "))
                .isEqualTo("이직이 걱정돼요. [이메일] / [전화번호]");
        assertThat(PersonalizedConcern.normalize("\n\t ")).isNull();
    }

    @Test
    void limitsTheConcernToOneHundredCharacters() {
        assertThatThrownBy(() -> PersonalizedConcern.normalize("가".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Personalized concern exceeds 100 characters");
    }
}
