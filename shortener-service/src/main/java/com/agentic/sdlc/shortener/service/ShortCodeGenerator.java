package com.agentic.sdlc.shortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Generates random base62 short codes. */
@Component
public class ShortCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DEFAULT_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        return generate(DEFAULT_LENGTH);
    }

    public String generate(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
