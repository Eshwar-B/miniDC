package com.minidc.server;

import com.minidc.common.enums.Role;
import com.minidc.server.config.AppProperties;
import com.minidc.server.user.Invite;
import com.minidc.server.user.InviteRepository;
import com.minidc.server.user.User;
import com.minidc.server.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Main entry point for the MiniDC control server application.
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class MiniDcServerApplication {

    private static final Logger log = LoggerFactory.getLogger(MiniDcServerApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MiniDcServerApplication.class);
        app.setRegisterShutdownHook(true);
        app.run(args);
    }

    @Bean
    @Profile("dev")
    public CommandLineRunner devAdminSeeder(UserRepository userRepository,
                                           InviteRepository inviteRepository,
                                           PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.count() == 0) {
                UUID adminId = UUID.randomUUID();
                User admin = new User(
                        adminId,
                        "admin@minidc.local",
                        passwordEncoder.encode("admin12345"),
                        "Administrator",
                        Role.ADMIN,
                        false,
                        Instant.now()
                );
                userRepository.save(admin);

                String startupInviteCode = "INV-DEV-ADMIN-2026";
                Invite invite = new Invite(startupInviteCode, adminId, null, Instant.now().plus(30, ChronoUnit.DAYS));
                inviteRepository.save(invite);

                log.info("==================================================");
                log.info("MiniDC Dev Admin Seeded successfully:");
                log.info("Email:    admin@minidc.local");
                log.info("Password: admin12345");
                log.info("Startup Invite Code: {}", startupInviteCode);
                log.info("==================================================");
            }
        };
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
                log.info("Graceful shutdown initiated - completing in-flight operations...");
                // Allow time for in-flight requests to complete
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("Graceful shutdown completed");
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
