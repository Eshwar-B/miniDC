package com.minidc.agent.vm;

import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.net.EgressRules;
import com.minidc.agent.net.TcpForwarder;
import com.minidc.common.model.VmInfo;
import com.minidc.common.model.VmSpec;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.common.ws.payload.VmReadyPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator managing VM lifecycle operations, port forwarding, and cleanup coordination.
 */
@Component
public class VmManager {

    private static final Logger log = LoggerFactory.getLogger(VmManager.class);

    private final HypervisorDriver driver;
    private final EgressRules egressRules;
    private final AgentProperties properties;
    private final Map<UUID, TcpForwarder> forwarders = new ConcurrentHashMap<>();

    public VmManager(HypervisorDriver driver, EgressRules egressRules, AgentProperties properties) {
        this.driver = driver;
        this.egressRules = egressRules;
        this.properties = properties;
    }

    public VmReadyPayload create(CreateVmPayload payload) {
        String vmName = "lease-" + payload.leaseId().toString().substring(0, 8);
        int hostPort = allocateFreePort();

        VmSpec spec = new VmSpec(
                payload.leaseId(),
                vmName,
                payload.ramMb(),
                payload.cores(),
                payload.diskGb(),
                payload.hours(),
                payload.vmUser(),
                payload.vmPassword(),
                hostPort,
                properties.bindAddress()
        );

        try {
            log.info("Creating VM {} on port {}", vmName, hostPort);
            driver.createVm(spec);
            VmInfo info = driver.waitUntilReady(vmName, Duration.ofSeconds(180));

            // Start forwarder if driver is Libvirt (where guest receives private virbr IP)
            if (driver instanceof LibvirtDriver && info.guestIp() != null) {
                TcpForwarder forwarder = new TcpForwarder(properties.bindAddress(), hostPort, info.guestIp(), 3389);
                forwarder.start();
                forwarders.put(payload.leaseId(), forwarder);
            }

            egressRules.applyLeaseInboundRule(payload.leaseId(), hostPort);
            log.info("VM {} successfully provisioned and ready on port {}", vmName, hostPort);
            return new VmReadyPayload(payload.leaseId(), vmName, hostPort);
        } catch (Exception e) {
            log.error("Failed creating VM for lease {}", payload.leaseId(), e);
            destroy(payload.leaseId());
            throw new RuntimeException("VM provisioning failed: " + e.getMessage(), e);
        }
    }

    public void destroy(UUID leaseId) {
        String vmName = "lease-" + leaseId.toString().substring(0, 8);
        log.info("Destroying VM {}", vmName);

        TcpForwarder forwarder = forwarders.remove(leaseId);
        if (forwarder != null) {
            forwarder.stop();
        }

        egressRules.removeLeaseInboundRule(leaseId);

        try {
            driver.destroyVm(vmName);
        } catch (Exception e) {
            log.warn("Error destroying VM {}: {}", vmName, e.getMessage());
        }
    }

    public List<String> listRunningVms() {
        return driver.listVms();
    }

    private int allocateFreePort() {
        int start = 33000;
        int end = 33999;
        String[] range = properties.portRange().split("-");
        if (range.length == 2) {
            try {
                start = Integer.parseInt(range[0].trim());
                end = Integer.parseInt(range[1].trim());
            } catch (NumberFormatException ignored) {}
        }

        for (int p = start; p <= end; p++) {
            if (isPortAvailable(p)) {
                return p;
            }
        }
        return start;
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

