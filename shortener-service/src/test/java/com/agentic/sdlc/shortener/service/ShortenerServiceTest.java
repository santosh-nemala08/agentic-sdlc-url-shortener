package com.agentic.sdlc.shortener.service;

import com.agentic.sdlc.shortener.domain.InMemoryLinkRepository;
import com.agentic.sdlc.shortener.domain.Link;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortenerServiceTest {

    /** Returns a fixed, scripted sequence of codes instead of random ones, so collision-retry is testable. */
    private static class ScriptedGenerator extends ShortCodeGenerator {
        private final Iterator<String> script;

        ScriptedGenerator(List<String> codes) {
            this.script = codes.iterator();
        }

        @Override
        public String generate() {
            return script.next();
        }
    }

    @Test
    void createLinkPersistsAndReturnsTheLinkForTheGivenUrl() {
        ShortenerService service = new ShortenerService(new InMemoryLinkRepository(), new ShortCodeGenerator());

        Link link = service.createLink("https://example.com/some/long/path");

        assertThat(link.originalUrl()).isEqualTo("https://example.com/some/long/path");
        assertThat(link.shortCode()).isNotBlank();
        assertThat(link.createdAt()).isNotNull();
    }

    @Test
    void retriesGenerationOnCollisionUntilAUniqueCodeIsFound() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        repository.save(new Link("taken01", "https://already-here.example", java.time.Instant.now()));

        ScriptedGenerator generator = new ScriptedGenerator(List.of("taken01", "taken01", "free001"));
        ShortenerService service = new ShortenerService(repository, generator);

        Link link = service.createLink("https://example.com");

        assertThat(link.shortCode()).isEqualTo("free001");
    }

    @Test
    void givesUpAfterMaxAttemptsRatherThanLoopingForever() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        repository.save(new Link("stuck01", "https://already-here.example", java.time.Instant.now()));

        // Always returns the same already-taken code -- must never succeed.
        ScriptedGenerator generator = new ScriptedGenerator(
                List.of("stuck01", "stuck01", "stuck01", "stuck01", "stuck01",
                        "stuck01", "stuck01", "stuck01", "stuck01", "stuck01"));
        ShortenerService service = new ShortenerService(repository, generator);

        assertThatThrownBy(() -> service.createLink("https://example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique short code");
    }

    @Test
    void createLinkWithAnAvailableAliasUsesItAsTheShortCode() {
        ShortenerService service = new ShortenerService(new InMemoryLinkRepository(), new ShortCodeGenerator());

        Link link = service.createLink("https://example.com", "my-alias");

        assertThat(link.shortCode()).isEqualTo("my-alias");
    }

    @Test
    void createLinkWithATakenAliasThrowsInsteadOfFallingBackToAGeneratedCode() {
        InMemoryLinkRepository repository = new InMemoryLinkRepository();
        repository.save(new Link("taken-alias", "https://already-here.example", java.time.Instant.now()));
        ShortenerService service = new ShortenerService(repository, new ShortCodeGenerator());

        assertThatThrownBy(() -> service.createLink("https://example.com", "taken-alias"))
                .isInstanceOf(AliasAlreadyTakenException.class)
                .hasMessageContaining("taken-alias");
    }

    @Test
    void blankAliasFallsBackToAGeneratedCodeRatherThanBeingTreatedAsRequested() {
        ShortenerService service = new ShortenerService(new InMemoryLinkRepository(), new ShortCodeGenerator());

        Link link = service.createLink("https://example.com", "   ");

        assertThat(link.shortCode()).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void resolveFindsAPreviouslyCreatedLinkByItsShortCode() {
        ShortenerService service = new ShortenerService(new InMemoryLinkRepository(), new ShortCodeGenerator());
        Link created = service.createLink("https://example.com/target");

        assertThat(service.resolve(created.shortCode())).contains(created);
    }

    @Test
    void resolveReturnsEmptyForAnUnknownShortCode() {
        ShortenerService service = new ShortenerService(new InMemoryLinkRepository(), new ShortCodeGenerator());

        assertThat(service.resolve("nope0000")).isEmpty();
    }
}
