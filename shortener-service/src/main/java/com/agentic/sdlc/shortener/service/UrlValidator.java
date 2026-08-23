package com.agentic.sdlc.shortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Rejects anything that is not a well-formed, absolute http/https URL, and
 * -- specific to a URL shortener -- anything that points back at this
 * service's own base URL, which would create a redirect loop the moment
 * someone followed it.
 *
 * Host/port comparison is a simplification: it does not account for
 * scheme-default ports (e.g. treating {@code http://host} and
 * {@code http://host:80} as equivalent), which is an acceptable gap for a
 * prototype configured with an explicit port, and is called out here
 * rather than silently assumed.
 */
@Component
public class UrlValidator {

    private final URI baseUri;

    public UrlValidator(@Value("${app.base-url}") String baseUrl) {
        this.baseUri = URI.create(baseUrl);
    }

    public void validate(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("url is not a valid URI: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new InvalidUrlException("url must use the http or https scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("url must include a host");
        }
        if (isSelfReferential(uri)) {
            throw new InvalidUrlException(
                    "url must not point back at this shortener service (would create a redirect loop)");
        }
    }

    private boolean isSelfReferential(URI uri) {
        return uri.getHost().equalsIgnoreCase(baseUri.getHost()) && uri.getPort() == baseUri.getPort();
    }
}
