package com.minidc.server.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.server.config.AppProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RateLimiterFilterTest {

    private RateLimiterFilter filter;
    private RedisStateService redisStateService;

    @BeforeEach
    void setUp() {
        AppProperties.RateLimitProperties rateLimit = new AppProperties.RateLimitProperties(true, 3, 2, 5);
        AppProperties properties = new AppProperties(
                "test-secret-at-least-32-chars-long!",
                12,
                "data-key-test-32-chars-minimum!",
                "http://localhost:8080/guacamole",
                "00112233445566778899aabbccddeeff",
                1,
                false,
                new AppProperties.RedisProperties(false),
                rateLimit
        );
        redisStateService = new RedisStateService(null, properties);
        filter = new RateLimiterFilter(redisStateService, properties, new ObjectMapper());
    }

    @Test
    void testRequestsWithinLimitPassThrough() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/auth/login");
        req1.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, chain);

        assertEquals(200, res1.getStatus());
        assertEquals("2", res1.getHeader("X-RateLimit-Remaining"));
        verify(chain, times(1)).doFilter(req1, res1);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/auth/login");
        req2.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, chain);

        assertEquals(200, res2.getStatus());
        assertEquals("1", res2.getHeader("X-RateLimit-Remaining"));
        verify(chain, times(1)).doFilter(req2, res2);
    }

    @Test
    void testRequestsExceedingLimitReturn429() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Limit is 3 for /api/auth/login
        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
            req.setRemoteAddr("10.0.0.2");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertEquals(200, res.getStatus());
        }

        // 4th request should be blocked with 429
        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/auth/login");
        blockedReq.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();
        filter.doFilter(blockedReq, blockedRes, chain);

        assertEquals(429, blockedRes.getStatus());
        assertEquals("60", blockedRes.getHeader("Retry-After"));
        assertEquals("0", blockedRes.getHeader("X-RateLimit-Remaining"));
        // chain should not have been called for the 4th request
        verify(chain, times(3)).doFilter(any(), any());
    }
}

