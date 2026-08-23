package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.service.ShortenerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

@RestController
public class RedirectController {

    private final ShortenerService shortenerService;

    public RedirectController(ShortenerService shortenerService) {
        this.shortenerService = shortenerService;
    }

    /**
     * 302 (Found), not 301 (Moved Permanently), deliberately: a 301 gets
     * cached by browsers, so repeat visits never hit this endpoint again --
     * which would silently break click analytics (commit 13) before it
     * even exists. 302 keeps every visit observable.
     *
     * An expired link returns 410 Gone rather than 404: the code did
     * exist and did resolve to something, distinct from a code that was
     * never issued.
     */
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable("code") String code) {
        Optional<Link> link = shortenerService.resolve(code);
        if (link.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (link.get().isExpired()) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.get().originalUrl()))
                .build();
    }
}
