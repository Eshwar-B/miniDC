package com.minidc.agent.vm;

import com.minidc.agent.config.AgentProperties;
import com.minidc.common.event.ReconciliationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Autonomous hypervisor reconciliation engine.
 * Discovers and cleans up zombie or orphan sandbox VMs left behind by crashes or power cuts.
 */
@Component
public class HypervisorReconciler {

    private static final Logger log = LoggerFactory.getLogger(HypervisorReconciler.class);

    private final HypervisorDriver driver;
    private final AgentProperties properties;

    public HypervisorReconciler(HypervisorDriver driver, AgentProperties properties) {
        this.driver = driver;
        this.properties = properties;
    }

    public ReconciliationReport reconcile(Set<String> expectedVmNames, boolean autoClean) {
        List<String> allVms = driver.listVms();
        List<String> miniDcVms = allVms.stream()
                .filter(name -> name.startsWith("lease-"))
                .toList();

        List<String> orphanVms = new ArrayList<>();
        List<String> cleanedVms = new ArrayList<>();

        for (String vmName : miniDcVms) {
            if (expectedVmNames == null || !expectedVmNames.contains(vmName)) {
                orphanVms.add(vmName);
                if (autoClean) {
                    try {
                        log.warn("Cleaning up orphan/zombie VM: {}", vmName);
                        driver.destroyVm(vmName);
                        cleanedVms.add(vmName);
                    } catch (Exception e) {
                        log.error("Failed to destroy orphan VM {}: {}", vmName, e.getMessage());
                    }
                }
            }
        }

        log.info("Hypervisor scan complete: found {} running MiniDC VMs, {} orphans, cleaned {}",
                miniDcVms.size(), orphanVms.size(), cleanedVms.size());

        return new ReconciliationReport(
                properties.nodeId(),
                miniDcVms,
                orphanVms,
                cleanedVms,
                Instant.now()
        );
    }
}
