package com.minidc.server.node;

import com.minidc.server.node.dto.CreateNodeRequest;
import com.minidc.server.node.dto.CreateNodeResponse;
import com.minidc.server.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for lender node operations including registration and status changes.
 */
@RestController
@RequestMapping("/api/nodes")
public class NodeController {

    private final NodeService nodeService;

    public NodeController(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateNodeResponse createNode(
            @AuthenticationPrincipal CurrentUser user,
            @Valid @RequestBody CreateNodeRequest request
    ) {
        return nodeService.createNode(user.id(), request);
    }

    @GetMapping("/mine")
    public List<Node> getMyNodes(@AuthenticationPrincipal CurrentUser user) {
        return nodeService.getMyNodes(user.id());
    }

    @PutMapping("/{id}/pause")
    public Node pauseNode(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID id
    ) {
        return nodeService.pauseNode(id, user.id());
    }

    @PutMapping("/{id}/resume")
    public Node resumeNode(
            @AuthenticationPrincipal CurrentUser user,
            @PathVariable UUID id
    ) {
        return nodeService.resumeNode(id, user.id());
    }
}
