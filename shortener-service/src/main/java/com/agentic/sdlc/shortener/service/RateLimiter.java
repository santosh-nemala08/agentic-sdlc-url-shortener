package com.agentic.sdlc.shortener.service;

public interface RateLimiter {

    /** Returns true and consumes one unit of quota for {@code key}, or false if the key is out of quota. */
    boolean tryConsume(String key);
}
