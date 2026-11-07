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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Hypervisor driver leveraging KVM and libvirt on Linux hosts.
 */
@Component
public class LibvirtDriver implements HypervisorDriver {

    private static final Logger log = LoggerFactory.getLogger(LibvirtDriver.class);

    private final Shell shell;
    private final HostStats hostStats;
    private final CloudInitBuilder cloudInitBuilder;
    private final AgentProperties properties;

    public LibvirtDriver(Shell shell, HostStats hostStats, CloudInitBuilder cloudInitBuilder, AgentProperties properties) {
        this.shell = shell;
        this.hostStats = hostStats;
        this.cloudInitBuilder = cloudInitBuilder;
        this.properties = properties;
    }

    @Override
    public void createVm(VmSpec spec) {
        Path leaseDir = Path.of(properties.leasesDir(), spec.leaseId().toString());
        Path goldenImg = Path.of(properties.imagesDir(), "golden.qcow2");
        Path diskPath = leaseDir.resolve("disk.qcow2");
        Path seedIso = leaseDir.resolve("seed.iso");
        Path userDataPath = leaseDir.resolve("user-data");
        Path metaDataPath = leaseDir.resolve("meta-data");

        try {
            Files.createDirectories(leaseDir);
            Files.writeString(userDataPath, cloudInitBuilder.userData(spec.vmUser(), spec.vmPassword(), spec.hours()));
            Files.writeString(metaDataPath, cloudInitBuilder.metaData(spec.leaseId()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare cloud-init files for lease " + spec.leaseId(), e);
        }

        List<String> diskCmd = List.of(
                "qemu-img", "create", "-f", "qcow2", "-F", "qcow2",
                "-b", goldenImg.toString(), diskPath.toString(), spec.diskGb() + "G"
        );

        List<String> isoCmd = List.of(
                "cloud-localds", seedIso.toString(), userDataPath.toString(), metaDataPath.toString()
        );

        List<String> installCmd = List.of(
                "virt-install",
                "--name", spec.vmName(),
                "--memory", String.valueOf(spec.ramMb()),
                "--vcpus", String.valueOf(spec.cores()),
                "--cpu", "host-passthrough",
                "--import",
                "--os-variant", "ubuntu24.04",
                "--disk", "path=" + diskPath + ",format=qcow2,bus=virtio",
                "--disk", "path=" + seedIso + ",device=cdrom",
                "--network", "network=minidc-net,model=virtio",
                "--graphics", "none",
                "--noautoconsole"
        );

        if (properties.dryRun()) {
            log.info("Dry-run: LibvirtDriver createVm commands:\n1: {}\n2: {}\n3: {}",
                    String.join(" ", diskCmd), String.join(" ", isoCmd), String.join(" ", installCmd));
            return;
        }

        // TODO(phase-1): Add optional CPU and disk I/O quotas via virsh schedinfo and virsh blkdeviotune

        try {
            shell.run(diskCmd, Duration.ofSeconds(30));
            shell.run(isoCmd, Duration.ofSeconds(30));
            shell.run(installCmd, Duration.ofSeconds(60));
        } catch (Exception e) {
            destroyVm(spec.vmName());
            throw new RuntimeException("virt-install failed for " + spec.vmName(), e);
        }
    }

    @Override
    public VmInfo waitUntilReady(String vmName, Duration timeout) {
        if (properties.dryRun()) {
            return new VmInfo(vmName, "192.168.150.15", 3389);
        }

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        String guestIp = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                ShellResult result = shell.run(List.of("virsh", "domifaddr", vmName, "--source", "lease"), Duration.ofSeconds(5));
                if (result.isSuccess() && result.out().contains("ipv4")) {
                    for (String line : result.out().lines().toList()) {
                        if (line.contains("ipv4")) {
                            String[] parts = line.trim().split("\\s+");
                            for (String part : parts) {
                                if (part.contains("/")) {
                                    guestIp = part.substring(0, part.indexOf('/'));
                                    break;
                                }
                            }
                        }
                    }
                }
                if (guestIp != null) {
                    break;
                }
                Thread.sleep(3000);
            } catch (Exception ignored) {
            }
        }

        if (guestIp == null) {
            throw new RuntimeException("Timed out waiting for guest IP for VM: " + vmName);
        }

        return new VmInfo(vmName, guestIp, 3389);
    }

    @Override
    public void destroyVm(String vmName) {
        if (properties.dryRun()) {
            log.info("Dry-run: LibvirtDriver destroyVm for {}", vmName);
            return;
        }

        try {
            shell.run(List.of("virsh", "destroy", vmName), Duration.ofSeconds(15));
        } catch (Exception ignored) {}

        try {
            shell.run(List.of("virsh", "undefine", vmName, "--nvram", "--remove-all-storage"), Duration.ofSeconds(15));
        } catch (Exception ignored) {}

        File leasesDir = new File(properties.leasesDir());
        File[] matches = leasesDir.listFiles((dir, name) -> vmName.contains(name.replace("-", "")));
        if (matches != null) {
            for (File dir : matches) {
                deleteRecursively(dir);
            }
        }
    }

    @Override
    public List<String> listVms() {
        if (properties.dryRun()) {
            return List.of();
        }
        try {
            ShellResult res = shell.run(List.of("virsh", "list", "--all", "--name"), Duration.ofSeconds(5));
            if (res.isSuccess()) {
                return res.out().lines()
                        .map(String::trim)
                        .filter(s -> s.startsWith("lease-"))
                        .toList();
            }
        } catch (Exception e) {
            log.warn("Failed listing libvirt VMs: {}", e.getMessage());
        }
        return List.of();
    }

    @Override
    public HostInfo hostInfo() {
        return hostStats.getHostInfo();
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
