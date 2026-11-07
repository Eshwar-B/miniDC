package com.minidc.server.security;

import com.minidc.common.enums.Role;

import java.security.Principal;
import java.util.UUID;

/**
 * Principal object representing the currently authenticated user in the security context.
 */
public record CurrentUser(
        UUID id,
        String email,
        Role role
) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
