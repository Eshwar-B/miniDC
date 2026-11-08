package com.minidc.server.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.server.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Distributed API rate limiting filter protecting sensitive endpoints from brute force and DoS.
 * Backed by Redis sliding window counters with in-memory fallback.
 */
@Component
@Order(1) // Run early in the filter chain
public class RateLimiterFilter extends OncePerRequestFilter {

    private final RedisStateService redisStateService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimiterFilter(RedisStateService redisStateService,
                             AppProperties properties,
                             ObjectMapper objectMapper) {
        this.redisStateService = redisStateService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (properties.rateLimit() != null && !properties.rateLimit().enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);

        String rateLimitKey = null;
        int limit = 0;

        if ("POST".equalsIgnoreCase(method) && uri.endsWith("/api/auth/login")) {
            rateLimitKey = "minidc:ratelimit:login:" + clientIp;
            limit = properties.rateLimit() != null ? properties.rateLimit().loginLimitPerMinute() : 5;
        } else if ("POST".equalsIgnoreCase(method) && uri.endsWith("/api/auth/register")) {
            rateLimitKey = "minidc:ratelimit:register:" + clientIp;
            limit = properties.rateLimit() != null ? properties.rateLimit().registerLimitPerMinute() : 3;
        } else if ("POST".equalsIgnoreCase(method) && (uri.endsWith("/api/leases") || uri.endsWith("/api/leases/"))) {
            rateLimitKey = "minidc:ratelimit:lease:" + clientIp;
            limit = properties.rateLimit() != null ? properties.rateLimit().leaseLimitPerMinute() : 10;
        }

        if (rateLimitKey != null) {
            long currentCount = redisStateService.incrementAndGet(rateLimitKey, Duration.ofMinutes(1));
            if (currentCount > limit) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", "60");
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", "0");

                Map<String, Object> errorBody = Map.of(
                        "status", 429,
                        "error", "Too Many Requests",
                        "message", "Rate limit exceeded. Please wait 60 seconds before retrying.",
                        "retryAfterSeconds", 60
                );
                objectMapper.writeValue(response.getWriter(), errorBody);
                return;
            }

            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - currentCount)));
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }
}
