package com.minidc.agent;

import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.net.EgressRules;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application entry point for the MiniDC background lender node daemon.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AgentProperties.class)
public class MiniDcAgentApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MiniDcAgentApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.run(args);
    }

    @Bean
    public CommandLineRunner initEgressRules(EgressRules egressRules) {
        return args -> egressRules.applyGlobalEgressRules();
    }
}

