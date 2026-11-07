package com.minidc.agent.vm;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.cmd.ShellResult;
import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.HostStats;
import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmInfo;
import com.minidc.common.model.VmSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HypervisorDriver implementation leveraging Docker containers for demo and lightweight sandbox leases.
 */
@Component
public class DockerDriver implements HypervisorDriver {

    private static final Logger log = LoggerFactory.getLogger(DockerDriver.class);

    private final Shell shell;
    private final HostStats hostStats;
    private final AgentProperties properties;
    private final Map<String, Integer> vmPorts = new ConcurrentHashMap<>();

    public DockerDriver(Shell shell, HostStats hostStats, AgentProperties properties) {
        this.shell = shell;
        this.hostStats = hostStats;
        this.properties = properties;
    }

    @Override
    public void createVm(VmSpec spec) {
        vmPorts.put(spec.vmName(), spec.hostPort());

        List<String> command = List.of(
                "docker", "run", "-d",
                "--name", spec.vmName(),
                "--memory", spec.ramMb() + "m",
                "--memory-swap", spec.ramMb() + "m",
                "--cpus", String.valueOf(spec.cores()),
                "--pids-limit", "512",
                "--shm-size", "1g",
                "--security-opt", "no-new-privileges",
                "--label", "minidc.lease=" + spec.leaseId(),
                "-p", spec.bindAddress() + ":" + spec.hostPort() + ":3389",
                "-e", "USER_NAME=" + spec.vmUser(),
                "-e", "PASSWORD=" + spec.vmPassword(),
                properties.dockerImage()
        );

        if (properties.dryRun()) {
            log.info("Dry-run: DockerDriver createVm prepared command: {}", String.join(" ", command));
            return;
        }

        try {
            ShellResult res = shell.run(command, Duration.ofSeconds(30));
            if (!res.isSuccess()) {
                throw new RuntimeException("docker run failed (exit " + res.code() + "): " + res.out());
            }
            log.info("Container {} started successfully: {}", spec.vmName(), res.out().trim());
        } catch (Exception e) {
            destroyVm(spec.vmName());
            throw new RuntimeException("Docker container provisioning failed for " + spec.vmName(), e);
        }
    }

    @Override
    public VmInfo waitUntilReady(String vmName, Duration timeout) {
        int hostPort = vmPorts.getOrDefault(vmName, 3389);
        if (properties.dryRun()) {
            return new VmInfo(vmName, properties.bindAddress(), hostPort);
        }

        String probeHost = properties.probeHost();
        Instant deadline = Instant.now().plus(timeout);

        log.info("Waiting for desktop container {} RDP port on {}:{} (timeout: {}s)...",
                vmName, probeHost, hostPort, timeout.toSeconds());

        while (Instant.now().isBefore(deadline)) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(probeHost, hostPort), 2000);
                log.info("Desktop container {} is reachable on {}:{}", vmName, probeHost, hostPort);
                return new VmInfo(vmName, properties.bindAddress(), hostPort);
            } catch (IOException ignored) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for container readiness", ie);
                }
            }
        }

        throw new RuntimeException("Timed out waiting for desktop container " + vmName + " on port " + hostPort);
    }

    @Override
    public void destroyVm(String vmName) {
        vmPorts.remove(vmName);

        if (properties.dryRun()) {
            log.info("Dry-run: DockerDriver destroyVm for {}", vmName);
            return;
        }

        List<String> command = List.of("docker", "rm", "-f", "-v", vmName);
        try {
            shell.run(command, Duration.ofSeconds(15));
            log.info("Container {} destroyed successfully", vmName);
        } catch (Exception e) {
            log.warn("Failed destroying Docker container {}: {}", vmName, e.getMessage());
        }
    }

    @Override
    public List<String> listVms() {
        if (properties.dryRun()) {
            return List.of();
        }

        List<String> command = List.of("docker", "ps", "-a", "--filter", "name=lease-", "--format", "{{.Names}}");
        try {
            ShellResult res = shell.run(command, Duration.ofSeconds(10));
            if (res.isSuccess()) {
                List<String> list = new ArrayList<>();
                for (String line : res.out().lines().toList()) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        list.add(trimmed);
                    }
                }
                return list;
            }
        } catch (Exception e) {
            log.warn("Failed listing Docker containers: {}", e.getMessage());
        }
        return List.of();
    }

    @Override
    public HostInfo hostInfo() {
        if (properties.dryRun()) {
            return hostStats.getHostInfo();
        }

        try {
            ShellResult res = shell.run(List.of("docker", "info", "--format", "{{.MemTotal}} {{.NCPU}}"), Duration.ofSeconds(5));
            if (res.isSuccess()) {
                String[] parts = res.out().trim().split("\\s+");
                if (parts.length >= 2) {
                    long memTotalBytes = Long.parseLong(parts[0]);
                    int totalRamMb = (int) (memTotalBytes / (1024 * 1024));
                    int cores = Integer.parseInt(parts[1]);

                    HostInfo nativeInfo = hostStats.getHostInfo();
                    return new HostInfo(
                            totalRamMb,
                            Math.min(totalRamMb, nativeInfo.freeRamMb()),
                            cores,
                            nativeInfo.freeDiskGb(),
                            true
                    );
                }
            }
        } catch (Exception e) {
            log.debug("Could not query docker info, falling back to HostStats: {}", e.getMessage());
        }

        return hostStats.getHostInfo();
    }
}
