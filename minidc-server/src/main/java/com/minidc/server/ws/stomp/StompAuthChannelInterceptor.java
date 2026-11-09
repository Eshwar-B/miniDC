package com.minidc.server.ws.stomp;

import com.minidc.server.security.CurrentUser;
import com.minidc.server.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Channel interceptor that authenticates incoming STOMP CONNECT frames using JWT bearer tokens.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                authHeader = accessor.getFirstNativeHeader("token");
                if (authHeader != null && !authHeader.startsWith("Bearer ")) {
                    authHeader = "Bearer " + authHeader;
                }
            }

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7).trim();
                try {
                    CurrentUser currentUser = jwtService.extractUser(token);
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            currentUser,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().name()))
                    );
                    if (accessor.isMutable()) {
                        accessor.setUser(auth);
                    } else {
                        StompHeaderAccessor mutableAccessor = StompHeaderAccessor.create(accessor.getCommand());
                        mutableAccessor.copyHeaders(accessor.toMap());
                        mutableAccessor.setUser(auth);
                        return MessageBuilder.createMessage(message.getPayload(), mutableAccessor.getMessageHeaders());
                    }
                    log.debug("STOMP connection authenticated for user: {}", currentUser.email());
                } catch (Exception e) {
                    log.warn("STOMP JWT authentication rejected: {}", e.getMessage());
                }
            }
        }
        return message;
    }
}
