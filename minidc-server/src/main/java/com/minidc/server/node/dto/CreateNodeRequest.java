package com.minidc.server.node.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating and registering a new compute node.
 */
public record CreateNodeRequest(
        @NotBlank(message = "Node name cannot be blank")
        String name
) {
}
