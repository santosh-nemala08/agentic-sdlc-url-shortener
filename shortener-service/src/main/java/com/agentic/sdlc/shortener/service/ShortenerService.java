package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.domain.LinkRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

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
    private final UrlValidator urlValidator;

    public ShortenerService(LinkRepository repository, ShortCodeGenerator generator, UrlValidator urlValidator) {
        this.repository = repository;
        this.generator = generator;
        this.urlValidator = urlValidator;
    }

    public Link createLink(String originalUrl) {
        return createLink(originalUrl, null, null);
    }

    public Link createLink(String originalUrl, String alias) {
        return createLink(originalUrl, alias, null);
    }

    /**
     * @param alias      caller-requested short code, or {@code null}/blank
     *                   to have one generated. A non-blank alias that is
     *                   already taken throws {@link AliasAlreadyTakenException}
     *                   rather than silently falling back to a generated
     *                   code -- the caller asked for a specific code and is
     *                   owed a clear answer about whether they got it.
     * @param ttlSeconds how long until the link expires, or {@code null}
     *                   for a link that never expires.
     */
    public Link createLink(String originalUrl, String alias, Long ttlSeconds) {
        urlValidator.validate(originalUrl);

        String shortCode = (alias != null && !alias.isBlank())
                ? reserveAlias(alias)
                : reserveGeneratedShortCode();
        Instant expiresAt = ttlSeconds != null ? Instant.now().plusSeconds(ttlSeconds) : null;
        Link link = new Link(shortCode, originalUrl, Instant.now(), expiresAt);
        return repository.save(link);
    }

    public Optional<Link> resolve(String shortCode) {
        return repository.findByShortCode(shortCode);
    }

    private String reserveAlias(String alias) {
        if (repository.existsByShortCode(alias)) {
            throw new AliasAlreadyTakenException(alias);
        }
        return alias;
    }

    private String reserveGeneratedShortCode() {
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
