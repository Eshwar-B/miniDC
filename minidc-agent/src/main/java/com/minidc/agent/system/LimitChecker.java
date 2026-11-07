package com.minidc.agent.system;

import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmSpec;
import org.springframework.stereotype.Component;

/**
 * Validates requested VM resource allocations against host safety headroom margins.
 */
@Component
public class LimitChecker {

    public boolean check(VmSpec spec, HostInfo host) {
        int requiredFreeRam = Math.max((int) (host.totalRamMb() * 0.25), 2048);
        if (host.freeRamMb() - spec.ramMb() < requiredFreeRam) {
            return false;
        }

        if (spec.cores() > host.totalCores() - 1) {
            return false;
        }

        if (spec.diskGb() > host.freeDiskGb() - 10) {
            return false;
        }

        return true;
    }
}

