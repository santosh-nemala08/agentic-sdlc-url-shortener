package com.agentic.sdlc.shortener;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Scaffold-only endpoint proving the service boots and routes requests.
 * Superseded in intent (but not removed) once the real shorten/redirect
 * API lands -- kept as a lightweight liveness check.
 */
@RestController
public class StatusController {

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of(
                "service", "shortener-service",
                "status", "scaffolded",
                "note", "Core shorten/redirect API lands in a later commit"
        );
    }
}
