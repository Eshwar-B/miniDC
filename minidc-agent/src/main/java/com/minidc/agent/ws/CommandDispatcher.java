package com.minidc.agent.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.agent.system.HostStats;
import com.minidc.agent.system.LimitChecker;
import com.minidc.agent.vm.VmManager;
import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmSpec;
import com.minidc.common.ws.MessageType;
import com.minidc.common.ws.WsMessage;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.common.ws.payload.DestroyVmPayload;
import com.minidc.common.ws.payload.VmFailedPayload;
import com.minidc.common.ws.payload.VmReadyPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Dispatches server commands, executes resource validation, and sends response payloads back to the control server.
 */
@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final VmManager vmManager;
    private final LimitChecker limitChecker;
    private final HostStats hostStats;
    private final ObjectMapper objectMapper;
    private final ServerConnection serverConnection;

    public CommandDispatcher(VmManager vmManager,
                             LimitChecker limitChecker,
                             HostStats hostStats,
                             ObjectMapper objectMapper,
                             @Lazy ServerConnection serverConnection) {
        this.vmManager = vmManager;
        this.limitChecker = limitChecker;
        this.hostStats = hostStats;
        this.objectMapper = objectMapper;
        this.serverConnection = serverConnection;
    }

    public void dispatch(WsMessage message) {
        switch (message.type()) {
            case CREATE_VM -> handleCreateVm(message);
            case DESTROY_VM -> handleDestroyVm(message);
            case PAUSE_NODE -> log.info("Received PAUSE_NODE command from server");
            case RESUME_NODE -> log.info("Received RESUME_NODE command from server");
            default -> log.warn("Unhandled server command type: {}", message.type());
        }
    }

    private void handleCreateVm(WsMessage message) {
        try {
            CreateVmPayload payload = objectMapper.treeToValue(message.payload(), CreateVmPayload.class);
            log.info("Processing CREATE_VM instruction for lease {}", payload.leaseId());

            HostInfo host = hostStats.getHostInfo();
            VmSpec spec = new VmSpec(
                    payload.leaseId(),
                    "lease-" + payload.leaseId().toString().substring(0, 8),
                    payload.ramMb(),
                    payload.cores(),
                    payload.diskGb(),
                    payload.hours(),
                    payload.vmUser(),
                    payload.vmPassword(),
                    0,
                    "127.0.0.1"
            );

            if (!limitChecker.check(spec, host)) {
                log.warn("Host limits check failed for lease {}", payload.leaseId());
                serverConnection.send(MessageType.VM_FAILED, new VmFailedPayload(payload.leaseId(), "Host limits exceeded: insufficient headroom"));
                return;
            }

            VmReadyPayload ready = vmManager.create(payload);
            serverConnection.send(MessageType.VM_READY, ready);
        } catch (Exception e) {
            log.error("Error executing CREATE_VM", e);
            try {
                CreateVmPayload payload = objectMapper.treeToValue(message.payload(), CreateVmPayload.class);
                serverConnection.send(MessageType.VM_FAILED, new VmFailedPayload(payload.leaseId(), "Provisioning error: " + e.getMessage()));
            } catch (Exception ignored) {}
        }
    }

    private void handleDestroyVm(WsMessage message) {
        try {
            DestroyVmPayload payload = objectMapper.treeToValue(message.payload(), DestroyVmPayload.class);
            log.info("Processing DESTROY_VM instruction for lease {}", payload.leaseId());

            vmManager.destroy(payload.leaseId());
            serverConnection.send(MessageType.VM_DESTROYED, payload);
        } catch (Exception e) {
            log.error("Error executing DESTROY_VM", e);
        }
    }

    public void onReconnectReconcile() {
        List<String> vms = vmManager.listRunningVms();
        log.info("Reconciling {} running local VMs on reconnection: {}", vms.size(), vms);
        // TODO(phase-3): Server reconciliation of running VMs on reconnect
    }
}

