package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.domain.Link;
import com.agentic.sdlc.shortener.service.ShortenerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
        Link link = shortenerService.createLink(request.url());
        CreateLinkResponse response = new CreateLinkResponse(
                link.shortCode(), baseUrl + "/" + link.shortCode(), link.originalUrl(), link.createdAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
