package com.agentic.sdlc.orchestrator.governance;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void noneAllowsExactlyOneAttemptWithZeroBackoff() {
        RetryPolicy policy = RetryPolicy.none();
        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.backoffAfterAttempt(1)).isEqualTo(Duration.ZERO);
    }

    @Test
    void boundedBacksOffExponentially() {
        RetryPolicy policy = RetryPolicy.bounded(4, Duration.ofMillis(100));
        assertThat(policy.backoffAfterAttempt(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.backoffAfterAttempt(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoffAfterAttempt(3)).isEqualTo(Duration.ofMillis(400));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RetryPolicy(0, Duration.ZERO, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ZERO, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(1, Duration.ofMillis(-1), 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
