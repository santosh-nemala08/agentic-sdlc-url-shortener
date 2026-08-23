package com.agentic.sdlc.shortener.persistence;

import com.agentic.sdlc.shortener.domain.Link;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code @DataJpaTest} boots only the JPA slice (embedded H2, Spring Data
 * repositories) instead of the whole application context -- faster than
 * {@code @SpringBootTest}, and it is testing exactly the layer that
 * matters here: does {@link JpaLinkRepository} actually round-trip a
 * {@link Link} through a real database, not a fake.
 */
@DataJpaTest
@Import(JpaLinkRepository.class)
class JpaLinkRepositoryTest {

    @Autowired
    private JpaLinkRepository repository;

    @Test
    void savedLinkCanBeFoundByShortCodeWithAllFieldsIntact() {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        Instant expiresAt = Instant.parse("2026-01-02T00:00:00Z");
        Link link = new Link("abc1234", "https://example.com/target", createdAt, expiresAt);

        repository.save(link);

        Link found = repository.findByShortCode("abc1234").orElseThrow();
        assertThat(found.originalUrl()).isEqualTo("https://example.com/target");
        assertThat(found.createdAt()).isEqualTo(createdAt);
        assertThat(found.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void aLinkWithNoExpirationRoundTripsANullExpiresAt() {
        Link link = new Link("noexp001", "https://example.com/forever", Instant.now());

        repository.save(link);

        assertThat(repository.findByShortCode("noexp001").orElseThrow().expiresAt()).isNull();
    }

    @Test
    void existsByShortCodeReflectsWhatWasActuallyPersisted() {
        assertThat(repository.existsByShortCode("nope0000")).isFalse();

        repository.save(new Link("nope0000", "https://example.com", Instant.now()));

        assertThat(repository.existsByShortCode("nope0000")).isTrue();
    }

    @Test
    void findByShortCodeReturnsEmptyForAnUnknownCode() {
        assertThat(repository.findByShortCode("unknown0")).isEmpty();
    }
}
