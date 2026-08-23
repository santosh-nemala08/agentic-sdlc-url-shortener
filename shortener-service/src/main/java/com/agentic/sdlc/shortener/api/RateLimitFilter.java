package com.agentic.sdlc.shortener.api;

import com.agentic.sdlc.shortener.service.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Throttles the create-link endpoint specifically -- that is the
 * unbounded-cost operation (each call reserves a code and writes a row);
 * redirects and analytics reads are not throttled here.
 *
 * Keyed by remote address. Known simplification: behind a reverse proxy
 * every client would share one address unless {@code X-Forwarded-For} is
 * trusted and parsed, which this prototype does not do -- noted as a
 * limitation, not an oversight.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter createLinkRateLimiter) {
        this.rateLimiter = createLinkRateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean isCreateLink = "POST".equalsIgnoreCase(request.getMethod())
                && "/api/links".equals(request.getRequestURI());

        if (isCreateLink && !rateLimiter.tryConsume(request.getRemoteAddr())) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Rate limit exceeded, try again later\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
