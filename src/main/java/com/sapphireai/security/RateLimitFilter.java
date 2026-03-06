package com.sapphireai.security;

import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter - prevents brute force attacks.
 * Uses token bucket algorithm (Google Guava).
 *
 * Limits:
 *   - General endpoints: configurable (default 60 req/min)
 *   - Search endpoint: configurable (default 20 req/min) - tighter because LLM calls are expensive
 *   - Auth login: 10 req/min per IP - prevents brute force
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    @Value("${sapphire.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${sapphire.security.rate-limit.requests-per-minute:60}")
    private double requestsPerMinute;

    @Value("${sapphire.security.rate-limit.search-requests-per-minute:20}")
    private double searchRequestsPerMinute;

    @Value("${sapphire.security.rate-limit.login-requests-per-minute:10}")
    private double loginRequestsPerMinute;

    // Separate rate limiter maps for different tiers
    private final ConcurrentHashMap<String, RateLimiter> generalLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimiter> searchLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimiter> loginLimiters = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip rate limiting for internal/docs endpoints
        String path = request.getRequestURI();
        if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") ||
                path.startsWith("/webjars") || path.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = getClientIp(request);

        // Determine which rate limiter to use based on path
        RateLimiter rateLimiter;
        String tierName;

        if (path.startsWith("/api/search")) {
            rateLimiter = searchLimiters.computeIfAbsent(
                    clientIp,
                    k -> RateLimiter.create(searchRequestsPerMinute / 60.0)
            );
            tierName = "search";
        } else if (path.equals("/api/auth/login")) {
            rateLimiter = loginLimiters.computeIfAbsent(
                    clientIp,
                    k -> RateLimiter.create(loginRequestsPerMinute / 60.0)
            );
            tierName = "login";
        } else {
            rateLimiter = generalLimiters.computeIfAbsent(
                    clientIp,
                    k -> RateLimiter.create(requestsPerMinute / 60.0)
            );
            tierName = "general";
        }

        if (!rateLimiter.tryAcquire()) {
            log.warn("Rate limit exceeded [tier={}] for IP: {}, path: {}", tierName, clientIp, path);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\",\"message\":\"Too many requests. Please try again later.\",\"tier\":\"" + tierName + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}