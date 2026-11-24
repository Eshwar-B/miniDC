package com.minidc.agent;

import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.net.EgressRules;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the MiniDC background lender node daemon.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class MiniDcAgentApplication {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MiniDcAgentApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MiniDcAgentApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setRegisterShutdownHook(true);
        app.run(args);
    }

    @Bean
    public CommandLineRunner initEgressRules(EgressRules egressRules) {
        return args -> egressRules.applyGlobalEgressRules();
    }

    @Bean
    public SmartLifecycle gracefulShutdownLifecycle() {
        return new SmartLifecycle() {
            private boolean running = false;

            @Override
            public void start() {
                running = true;
            }

            @Override
            public void stop() {
                stop(() -> {});
            }

            @Override
            public void stop(Runnable callback) {
                log.info("Agent graceful shutdown initiated - completing in-flight telemetry and persisting unmetered minutes...");
                // Allow time for in-flight telemetry and metric persistence
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Agent graceful shutdown completed");
                running = false;
                callback.run();
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                return Integer.MAX_VALUE; // Run last during shutdown
            }
        };
    }
}
