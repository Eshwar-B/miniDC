package com.minidc.agent.system;

import com.minidc.agent.cmd.Shell;
import com.minidc.agent.cmd.ShellResult;
import com.minidc.agent.config.AgentProperties;
import com.minidc.common.model.HostInfo;
import com.sun.management.OperatingSystemMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Collects host hardware metrics (RAM, CPU cores, free disk, virtualization support) across Linux and Windows.
 */
@Component
public class HostStats {

    private static final Logger log = LoggerFactory.getLogger(HostStats.class);

    private final OsDetector osDetector;
    private final Shell shell;
    private final AgentProperties properties;

    public HostStats(OsDetector osDetector, Shell shell, AgentProperties properties) {
        this.osDetector = osDetector;
        this.shell = shell;
        this.properties = properties;
    }

    public HostInfo getHostInfo() {
        if (osDetector.isLinux()) {
            try {
                return readLinuxHostInfo();
            } catch (Exception e) {
                log.warn("Failed reading Linux native host stats, falling back to JVM MXBean", e);
            }
        } else if (osDetector.isWindows()) {
            try {
                return readWindowsHostInfo();
            } catch (Exception e) {
                log.warn("Failed reading Windows native host stats, falling back to JVM MXBean", e);
            }
        }
        return readFallbackHostInfo();
    }

    private HostInfo readLinuxHostInfo() throws Exception {
        int totalRamMb = 0;
        int freeRamMb = 0;

        Path meminfo = Path.of("/proc/meminfo");
        if (Files.exists(meminfo)) {
            List<String> lines = Files.readAllLines(meminfo);
            for (String line : lines) {
                if (line.startsWith("MemTotal:")) {
                    totalRamMb = parseMemKb(line) / 1024;
                } else if (line.startsWith("MemAvailable:")) {
                    freeRamMb = parseMemKb(line) / 1024;
                }
            }
        }

        if (totalRamMb == 0) {
            return readFallbackHostInfo();
        }

        int cores = Runtime.getRuntime().availableProcessors();
        int freeDiskGb = getFreeDiskGb();
        boolean kvmOk = Files.isReadable(Path.of("/dev/kvm"));

        return new HostInfo(totalRamMb, freeRamMb, cores, freeDiskGb, kvmOk);
    }

    private HostInfo readWindowsHostInfo() throws Exception {
        if (properties.dryRun()) {
            return readFallbackHostInfo();
        }

        List<String> cmd = List.of(
                "powershell", "-NoProfile", "-Command",
                "$os = Get-CimInstance Win32_OperatingSystem; Write-Output ($os.TotalVisibleMemorySize.ToString() + ' ' + $os.FreePhysicalMemory.ToString())"
        );
        ShellResult res = shell.run(cmd, Duration.ofSeconds(3));
        if (res.isSuccess()) {
            String[] parts = res.out().trim().split("\\s+");
            if (parts.length >= 2) {
                int totalRamMb = (int) (Long.parseLong(parts[0]) / 1024);
                int freeRamMb = (int) (Long.parseLong(parts[1]) / 1024);
                int cores = Runtime.getRuntime().availableProcessors();
                int freeDiskGb = getFreeDiskGb();
                return new HostInfo(totalRamMb, freeRamMb, cores, freeDiskGb, true);
            }
        }
        return readFallbackHostInfo();
    }

    private HostInfo readFallbackHostInfo() {
        int totalRamMb = 8192;
        int freeRamMb = 4096;
        try {
            java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
            if (baseBean instanceof OperatingSystemMXBean osBean) {
                totalRamMb = (int) (osBean.getTotalMemorySize() / (1024 * 1024));
                freeRamMb = (int) (osBean.getFreeMemorySize() / (1024 * 1024));
            }
        } catch (Exception ignored) {
        }

        int cores = Runtime.getRuntime().availableProcessors();
        int freeDiskGb = getFreeDiskGb();
        return new HostInfo(totalRamMb, freeRamMb, cores, freeDiskGb, true);
    }

    private int parseMemKb(String line) {
        String num = line.replaceAll("[^0-9]", "");
        return num.isEmpty() ? 0 : Integer.parseInt(num);
    }

    private int getFreeDiskGb() {
        File dir = new File(properties.leasesDir());
        if (!dir.exists()) {
            dir = new File(".");
        }
        return (int) (dir.getUsableSpace() / (1024L * 1024L * 1024L));
    }
}

