package com.minidc.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the MiniDC control server.
 */
@ConfigurationProperties(prefix = "minidc")
public record AppProperties(
        String jwtSecret,
        int jwtHours,
        String dataKey,
        String guacBaseUrl,
        String guacJsonSecret,
        int maxActiveLeasesPerUser,
        boolean seedAdmin,
        RedisProperties redis,
        RateLimitProperties rateLimit,
        KafkaProperties kafka
) {
    public record KafkaProperties(
            boolean enabled,
            String bootstrapServers,
            String dlqTopic
    ) {
        public KafkaProperties {
            if (bootstrapServers == null || bootstrapServers.isBlank()) {
                bootstrapServers = "localhost:9092";
            }
            if (dlqTopic == null || dlqTopic.isBlank()) {
                dlqTopic = "minidc.dlq";
            }
        }
    }

    public record RedisProperties(boolean enabled) {
        public RedisProperties {
            // default enabled
        }
    }

    public AppProperties(
            String jwtSecret,
            int jwtHours,
            String dataKey,
            String guacBaseUrl,
            String guacJsonSecret,
            int maxActiveLeasesPerUser,
            boolean seedAdmin,
            RedisProperties redis,
            RateLimitProperties rateLimit
    ) {
        this(jwtSecret, jwtHours, dataKey, guacBaseUrl, guacJsonSecret, maxActiveLeasesPerUser, seedAdmin, redis, rateLimit, null);
    }

    public record RateLimitProperties(
            boolean enabled,
            int loginLimitPerMinute,
            int registerLimitPerMinute,
            int leaseLimitPerMinute
    ) {
        public RateLimitProperties {
            if (loginLimitPerMinute <= 0) loginLimitPerMinute = 5;
            if (registerLimitPerMinute <= 0) registerLimitPerMinute = 3;
            if (leaseLimitPerMinute <= 0) leaseLimitPerMinute = 10;
        }
    }

    public AppProperties {
        if (jwtHours <= 0) {
            jwtHours = 12;
        }
        if (maxActiveLeasesPerUser <= 0) {
            maxActiveLeasesPerUser = 1;
        }
        if (redis == null) {
            redis = new RedisProperties(true);
        }
        if (rateLimit == null) {
            rateLimit = new RateLimitProperties(true, 5, 3, 10);
        }
        if (kafka == null) {
            kafka = new KafkaProperties(false, "localhost:9092", "minidc.dlq");
        }
    }
}
