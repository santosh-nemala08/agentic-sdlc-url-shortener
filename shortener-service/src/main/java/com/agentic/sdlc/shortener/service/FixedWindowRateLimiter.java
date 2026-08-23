package com.agentic.sdlc.shortener.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A fixed-window counter per key, not a true token bucket -- the whole
 * quota resets at once when a window elapses, rather than continuously
 * refilling. Simpler to reason about and test; the trade-off is a client
 * can burst up to {@code maxRequestsPerWindow} right at a window
 * boundary followed immediately by another full quota, rather than a
 * smooth rate. Acceptable for this prototype's purpose (blunt abuse
 * prevention on the create-link endpoint), not a claim of precise
 * traffic shaping.
 */
public final class FixedWindowRateLimiter implements RateLimiter {

    private static final class Window {
        int remaining;
        long windowStartMillis;
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequestsPerWindow;
    private final long windowMillis;

    public FixedWindowRateLimiter(int maxRequestsPerWindow, Duration window) {
        if (maxRequestsPerWindow < 1) {
            throw new IllegalArgumentException("maxRequestsPerWindow must be >= 1");
        }
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.windowMillis = window.toMillis();
    }

    @Override
    public boolean tryConsume(String key) {
        Window window = windows.computeIfAbsent(key, k -> freshWindow());
        synchronized (window) {
            long now = System.currentTimeMillis();
            if (now - window.windowStartMillis >= windowMillis) {
                window.windowStartMillis = now;
                window.remaining = maxRequestsPerWindow;
            }
            if (window.remaining > 0) {
                window.remaining--;
                return true;
            }
            return false;
        }
    }

    private Window freshWindow() {
        Window window = new Window();
        window.remaining = maxRequestsPerWindow;
        window.windowStartMillis = System.currentTimeMillis();
        return window;
    }

    /**
     * Clears all tracked windows, as if no requests had ever been made.
     * Not part of the {@link RateLimiter} interface -- this is an
     * operational/test hook on the concrete implementation, not something
     * callers throttled by it should ever invoke. Tests need it because
     * this bean is a Spring singleton: multiple {@code @Test} methods in
     * one test class share the same cached Spring context, and therefore
     * the same limiter instance and its already-consumed quota, unless
     * something resets it between methods.
     */
    public void resetAll() {
        windows.clear();
    }
}
