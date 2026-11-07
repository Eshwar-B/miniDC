package com.minidc.agent.vm;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.HostStats;
import com.minidc.agent.system.OsDetector;
import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmInfo;
import com.minidc.common.model.VmSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DockerDriverTest {

    private DockerDriver driver;

    @BeforeEach
    void setUp() {
        AgentProperties properties = new AgentProperties(
                "ws://localhost:8080/ws/agent",
                UUID.randomUUID(),
                "test-token",
                "docker",
                "127.0.0.1",
                "./data/images",
                "./data/leases",
                25,
                "33000-33999",
                true, // dryRun = true
                "minidc-desktop:latest",
                "127.0.0.1"
        );

        OsDetector osDetector = new OsDetector();
        Shell shell = new Shell();
        HostStats hostStats = new HostStats(osDetector, shell, properties) {
            @Override
            public HostInfo getHostInfo() {
                return new HostInfo(16384, 8192, 8, 250, true);
            }
        };

        driver = new DockerDriver(shell, hostStats, properties);
    }

    @Test
    void testDryRunLifecycle() {
        UUID leaseId = UUID.randomUUID();
        VmSpec spec = new VmSpec(
                leaseId,
                "lease-12345678",
                2048,
                2,
                10,
                1,
                "testuser",
                "testpass",
                33001,
                "127.0.0.1"
        );

        assertDoesNotThrow(() -> driver.createVm(spec));

        VmInfo info = driver.waitUntilReady("lease-12345678", Duration.ofSeconds(5));
        assertNotNull(info);
        assertEquals("lease-12345678", info.vmName());
        assertEquals("127.0.0.1", info.guestIp());
        assertEquals(33001, info.hostPort());

        List<String> vms = driver.listVms();
        assertNotNull(vms);

        assertDoesNotThrow(() -> driver.destroyVm("lease-12345678"));
    }

    @Test
    void testHostInfoFallback() {
        HostInfo info = driver.hostInfo();
        assertNotNull(info);
        assertEquals(16384, info.totalRamMb());
        assertEquals(8, info.totalCores());
    }
}
