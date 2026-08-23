package com.agentic.sdlc.shortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator("http://localhost:8080");

    @Test
    void acceptsAWellFormedHttpsUrl() {
        assertThatCode(() -> validator.validate("https://example.com/some/path?query=1"))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsAWellFormedHttpUrl() {
        assertThatCode(() -> validator.validate("http://example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAMalformedUri() {
        assertThatThrownBy(() -> validator.validate("http://exa mple.com"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsANonHttpScheme() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/file"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void rejectsAUrlWithNoHost() {
        assertThatThrownBy(() -> validator.validate("https:///no-host"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("host");
    }

    @Test
    void rejectsAUrlThatPointsBackAtThisServicesOwnBaseUrl() {
        assertThatThrownBy(() -> validator.validate("http://localhost:8080/some-other-code"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("redirect loop");
    }

    @Test
    void acceptsAUrlOnTheSameHostButADifferentPort() {
        assertThatCode(() -> validator.validate("http://localhost:9090/not-this-service"))
                .doesNotThrowAnyException();
    }
}
