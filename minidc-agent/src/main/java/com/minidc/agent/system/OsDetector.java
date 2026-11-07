package com.minidc.agent.system;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Utility identifying the underlying host operating system.
 */
@Component
public class OsDetector {

    private final String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    public boolean isWindows() {
        return osName.contains("win");
    }

    public boolean isLinux() {
        return osName.contains("nux") || osName.contains("nix");
    }

    public boolean isMac() {
        return osName.contains("mac");
    }

    public String getOsName() {
        return osName;
    }
}

