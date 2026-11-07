package com.minidc.server.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.server.auth.dto.LoginRequest;
import com.minidc.server.auth.dto.RegisterRequest;
import com.minidc.server.support.PostgresTestContainer;
import com.minidc.server.user.Invite;
import com.minidc.server.user.InviteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying authentication endpoints, registration flows, and token validation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthControllerIT extends PostgresTestContainer {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InviteRepository inviteRepository;

    @Test
    @DisplayName("Complete registration and login integration flow")
    void testRegisterAndLoginFlow() throws Exception {
        String inviteCode = "INV-TEST-" + System.currentTimeMillis();
        Invite invite = new Invite(inviteCode, null, null, Instant.now().plus(1, ChronoUnit.DAYS));
        inviteRepository.save(invite);

        RegisterRequest registerReq = new RegisterRequest(
                "newuser@example.com",
                "Password123!",
                "New User",
                inviteCode
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString());

        LoginRequest loginReq = new LoginRequest("newuser@example.com", "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    @DisplayName("Registration fails when invite code is invalid")
    void testRegisterInvalidInvite() throws Exception {
        RegisterRequest registerReq = new RegisterRequest(
                "invalid@example.com",
                "Password123!",
                "Invalid Invite",
                "NON-EXISTENT-CODE"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isBadRequest());
    }
}

