package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.service.AliasAlreadyTakenException;
import com.agentic.sdlc.shortener.service.InvalidUrlException;
import com.agentic.sdlc.shortener.service.ShortenerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class LinkController {

    private final ShortenerService shortenerService;
    private final String baseUrl;

    public LinkController(ShortenerService shortenerService, @Value("${app.base-url}") String baseUrl) {
        this.shortenerService = shortenerService;
        this.baseUrl = baseUrl;
    }

    @PostMapping("/api/links")
    public ResponseEntity<CreateLinkResponse> createLink(@Valid @RequestBody CreateLinkRequest request) {
        Link link = shortenerService.createLink(request.url(), request.alias(), request.ttlSeconds());
        CreateLinkResponse response = new CreateLinkResponse(
                link.shortCode(), baseUrl + "/" + link.shortCode(), link.originalUrl(),
                link.createdAt(), link.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler(AliasAlreadyTakenException.class)
    public ResponseEntity<Map<String, String>> handleAliasTaken(AliasAlreadyTakenException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<Map<String, String>> handleInvalidUrl(InvalidUrlException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
