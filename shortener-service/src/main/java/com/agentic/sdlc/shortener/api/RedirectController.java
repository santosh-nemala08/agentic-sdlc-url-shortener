package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.service.ClickTracker;
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
    private final ClickTracker clickTracker;

    public RedirectController(ShortenerService shortenerService, ClickTracker clickTracker) {
        this.shortenerService = shortenerService;
        this.clickTracker = clickTracker;
    }

    /**
     * 302 (Found), not 301 (Moved Permanently), deliberately: a 301 gets
     * cached by browsers, so repeat visits never hit this endpoint again,
     * which would silently break click analytics. 302 keeps every visit
     * observable.
     *
     * An expired link returns 410 Gone rather than 404: the code did
     * exist and did resolve to something, distinct from a code that was
     * never issued. Only an actual (non-expired) redirect counts as a
     * click -- an expired-link hit didn't send anyone anywhere.
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
        clickTracker.recordClickAsync(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.get().originalUrl()))
                .build();
    }
}
