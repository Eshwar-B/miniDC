package com.minidc.server.guac;

import com.minidc.server.security.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller providing Guacamole connection launching credentials and URLs.
 */
@RestController
@RequestMapping("/api/leases")
public class GuacController {

    private final GuacService guacService;

    public GuacController(GuacService guacService) {
        this.guacService = guacService;
    }

    @GetMapping("/{id}/connect")
    public GuacService.ConnectResponse connect(
            @PathVariable UUID id,
            @AuthenticationPrincipal CurrentUser user
    ) {
        return guacService.getConnectUrl(id, user);
    }
}
