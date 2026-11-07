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
        boolean seedAdmin
) {
    public AppProperties {
        if (jwtHours <= 0) {
            jwtHours = 12;
        }
        if (maxActiveLeasesPerUser <= 0) {
            maxActiveLeasesPerUser = 1;
        }
    }
}
