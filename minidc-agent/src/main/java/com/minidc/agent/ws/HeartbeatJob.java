package com.minidc.agent.ws;

import com.minidc.agent.system.HostStats;
import com.minidc.agent.vm.VmManager;
import com.minidc.common.model.HostInfo;
import com.minidc.common.ws.MessageType;
import com.minidc.common.ws.payload.HeartbeatPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job dispatching node status and resource telemetry heartbeats to the server every 15 seconds.
 */
@Component
public class HeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatJob.class);

    private final ServerConnection serverConnection;
    private final HostStats hostStats;
    private final VmManager vmManager;

    public HeartbeatJob(ServerConnection serverConnection, HostStats hostStats, VmManager vmManager) {
        this.serverConnection = serverConnection;
        this.hostStats = hostStats;
        this.vmManager = vmManager;
    }

    @Scheduled(fixedDelay = 15000)
    public void sendHeartbeat() {
        if (!serverConnection.isConnected()) {
            return;
        }

        try {
            HostInfo host = hostStats.getHostInfo();
            int activeVms = vmManager.listRunningVms().size();

            HeartbeatPayload payload = new HeartbeatPayload(
                    host.freeRamMb(),
                    0.10,
                    activeVms,
                    host.freeDiskGb()
            );

            serverConnection.send(MessageType.HEARTBEAT, payload);
            log.debug("Dispatched heartbeat: freeRam={}MB, activeVms={}", host.freeRamMb(), activeVms);
        } catch (Exception e) {
            log.warn("Failed sending heartbeat: {}", e.getMessage());
        }
    }
}

