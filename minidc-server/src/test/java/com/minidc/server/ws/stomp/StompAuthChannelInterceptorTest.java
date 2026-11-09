package com.minidc.server.ws.stomp;

import com.minidc.common.enums.Role;
import com.minidc.server.security.CurrentUser;
import com.minidc.server.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    private JwtService jwtService;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = Mockito.mock(JwtService.class);
        interceptor = new StompAuthChannelInterceptor(jwtService);
    }

    @Test
    void shouldAuthenticateValidBearerTokenOnConnect() {
        UUID userId = UUID.randomUUID();
        CurrentUser user = new CurrentUser(userId, "test@minidc.local", Role.MEMBER);
        when(jwtService.extractUser("valid-jwt")).thenReturn(user);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer valid-jwt");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = Mockito.mock(MessageChannel.class);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) resultAccessor.getUser();
        assertNotNull(auth);
        assertEquals(user, auth.getPrincipal());
    }

    @Test
    void shouldIgnoreNonConnectFrames() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.addNativeHeader("Authorization", "Bearer token");
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = Mockito.mock(MessageChannel.class);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }

    @Test
    void shouldGracefullyHandleMissingOrMalformedTokens() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        MessageChannel channel = Mockito.mock(MessageChannel.class);
        Message<?> result = interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertNull(resultAccessor.getUser());
    }
}
