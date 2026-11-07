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
import java.util.List;

/**
 * Hypervisor driver leveraging Oracle VirtualBox on Windows or Linux hosts.
 */
@Component
public class VirtualBoxDriver implements HypervisorDriver {

    private static final Logger log = LoggerFactory.getLogger(VirtualBoxDriver.class);

    private final Shell shell;
    private final HostStats hostStats;
    private final CloudInitBuilder cloudInitBuilder;
    private final AgentProperties properties;

    public VirtualBoxDriver(Shell shell, HostStats hostStats, CloudInitBuilder cloudInitBuilder, AgentProperties properties) {
        this.shell = shell;
        this.hostStats = hostStats;
        this.cloudInitBuilder = cloudInitBuilder;
        this.properties = properties;
    }

    private String getVBoxManagePath() {
        if (new File("C:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe").exists()) {
            return "C:\\Program Files\\Oracle\\VirtualBox\\VBoxManage.exe";
        }
        return "VBoxManage";
    }

    @Override
    public void createVm(VmSpec spec) {
        Path leaseDir = Path.of(properties.leasesDir(), spec.leaseId().toString());
        Path goldenVdi = Path.of(properties.imagesDir(), "golden.vdi");
        Path diskPath = leaseDir.resolve("disk.vdi");
        Path seedIso = leaseDir.resolve("seed.iso");
        Path userDataPath = leaseDir.resolve("user-data");
        Path metaDataPath = leaseDir.resolve("meta-data");

        try {
            Files.createDirectories(leaseDir);
            Files.writeString(userDataPath, cloudInitBuilder.userData(spec.vmUser(), spec.vmPassword(), spec.hours()));
            Files.writeString(metaDataPath, cloudInitBuilder.metaData(spec.leaseId()));
        } catch (IOException e) {
            throw new RuntimeException("Failed preparing lease directory: " + leaseDir, e);
        }

        String vbox = getVBoxManagePath();
        String natPortRule = String.format("rdp,tcp,%s,%d,,3389", spec.bindAddress(), spec.hostPort());

        List<List<String>> commands = List.of(
                List.of(vbox, "createvm", "--name", spec.vmName(), "--ostype", "Ubuntu_64", "--register", "--basefolder", leaseDir.toString()),
                List.of(vbox, "modifyvm", spec.vmName(),
                        "--memory", String.valueOf(spec.ramMb()),
                        "--cpus", String.valueOf(spec.cores()),
                        "--cpuexecutioncap", "80",
                        "--nic1", "nat",
                        "--natpf1", natPortRule,
                        "--clipboard-mode", "disabled",
                        "--draganddrop", "disabled",
                        "--audio-driver", "none",
                        "--usb", "off"),
                List.of(vbox, "storagectl", spec.vmName(), "--name", "SATA", "--add", "sata"),
                List.of(vbox, "storageattach", spec.vmName(), "--storagectl", "SATA", "--port", "0", "--device", "0", "--type", "hdd", "--medium", diskPath.toString()),
                List.of(vbox, "storageattach", spec.vmName(), "--storagectl", "SATA", "--port", "1", "--device", "0", "--type", "dvddrive", "--medium", seedIso.toString()),
                List.of(vbox, "startvm", spec.vmName(), "--type", "headless")
        );

        if (properties.dryRun()) {
            log.info("Dry-run: VirtualBoxDriver createVm prepared {} commands for {}", commands.size(), spec.vmName());
            return;
        }

        try {
            for (List<String> cmd : commands) {
                shell.run(cmd, Duration.ofSeconds(30));
            }
        } catch (Exception e) {
            destroyVm(spec.vmName());
            throw new RuntimeException("VirtualBox provisioning failed for " + spec.vmName(), e);
        }
    }

    @Override
    public VmInfo waitUntilReady(String vmName, Duration timeout) {
        if (properties.dryRun()) {
            return new VmInfo(vmName, properties.bindAddress(), 3389);
        }

        // VirtualBox exposes the forwarded port directly on host bind address
        return new VmInfo(vmName, properties.bindAddress(), 3389);
    }

    @Override
    public void destroyVm(String vmName) {
        if (properties.dryRun()) {
            log.info("Dry-run: VirtualBoxDriver destroyVm for {}", vmName);
            return;
        }

        String vbox = getVBoxManagePath();
        try {
            shell.run(List.of(vbox, "controlvm", vmName, "poweroff"), Duration.ofSeconds(15));
        } catch (Exception ignored) {}

        try {
            shell.run(List.of(vbox, "unregistervm", vmName, "--delete"), Duration.ofSeconds(15));
        } catch (Exception ignored) {}
    }

    @Override
    public List<String> listVms() {
        if (properties.dryRun()) {
            return List.of();
        }

        String vbox = getVBoxManagePath();
        try {
            ShellResult res = shell.run(List.of(vbox, "list", "vms"), Duration.ofSeconds(5));
            if (res.isSuccess()) {
                List<String> list = new ArrayList<>();
                for (String line : res.out().lines().toList()) {
                    if (line.contains("\"lease-")) {
                        int start = line.indexOf('"');
                        int end = line.indexOf('"', start + 1);
                        if (start >= 0 && end > start) {
                            list.add(line.substring(start + 1, end));
                        }
                    }
                }
                return list;
            }
        } catch (Exception e) {
            log.warn("Failed listing VirtualBox VMs: {}", e.getMessage());
        }
        return List.of();
    }

    @Override
    public HostInfo hostInfo() {
        return hostStats.getHostInfo();
    }
}
