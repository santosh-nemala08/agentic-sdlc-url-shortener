package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.domain.LinkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ShortenerService {

    /**
     * Collision probability at 7 base62 characters (~3.5 trillion codes) is
     * astronomically low; this bound exists so a pathological run fails
     * loudly with a clear cause instead of looping forever.
     */
    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final LinkRepository repository;
    private final ShortCodeGenerator generator;

    public ShortenerService(LinkRepository repository, ShortCodeGenerator generator) {
        this.repository = repository;
        this.generator = generator;
    }

    public Link createLink(String originalUrl) {
        String shortCode = reserveUniqueShortCode();
        Link link = new Link(shortCode, originalUrl, Instant.now());
        return repository.save(link);
    }

    private String reserveUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = generator.generate();
            if (!repository.existsByShortCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique short code after " + MAX_GENERATION_ATTEMPTS + " attempts");
    }
}
