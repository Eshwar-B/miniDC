package com.minidc.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring task scheduling for heartbeat and lease lifecycle monitors.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
