package com.agentic.sdlc.shortener.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWindowRateLimiterTest {

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new FixedWindowRateLimiter(0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsUpToTheConfiguredLimitThenRejects() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isFalse();
    }

    @Test
    void tracksDifferentKeysIndependently() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMinutes(1));

        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isFalse();
        assertThat(limiter.tryConsume("client-b")).isTrue(); // unaffected by client-a's quota
    }

    @Test
    void resetsFullQuotaOnceTheWindowElapses() throws InterruptedException {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, Duration.ofMillis(50));

        assertThat(limiter.tryConsume("client-a")).isTrue();
        assertThat(limiter.tryConsume("client-a")).isFalse();

        Thread.sleep(80);

        assertThat(limiter.tryConsume("client-a")).isTrue();
    }
}
