package com.minidc.agent.system;

import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests verifying host safety threshold calculations across five distinct hardware boundary cases.
 */
class LimitCheckerTest {

    private final LimitChecker limitChecker = new LimitChecker();

    private VmSpec createSpec(int ramMb, int cores, int diskGb) {
        return new VmSpec(UUID.randomUUID(), "lease-test", ramMb, cores, diskGb, 1, "user", "pass", 33000, "127.0.0.1");
    }

    @Test
    @DisplayName("Case 1: Standard allocation well within limits succeeds")
    void testStandardAllocationWithinLimits() {
        HostInfo host = new HostInfo(16384, 12000, 8, 100, true);
        VmSpec spec = createSpec(4096, 4, 30);
        assertTrue(limitChecker.check(spec, host));
    }

    @Test
    @DisplayName("Case 2: Allocation leaving less than 25% (4096MB) RAM free fails")
    void testExcessiveRamAllocationFails() {
        // total 16384, 25% = 4096. free 6000. request 3000 leaves 3000 < 4096 -> reject
        HostInfo host = new HostInfo(16384, 6000, 8, 100, true);
        VmSpec spec = createSpec(3000, 2, 20);
        assertFalse(limitChecker.check(spec, host));
    }

    @Test
    @DisplayName("Case 3: Allocation leaving less than 2048MB RAM free fails on small host")
    void testSmallHost2048MbMinRamSafetyFails() {
        // total 4096, 25% = 1024, max(1024, 2048) = 2048. free 3000. request 1500 leaves 1500 < 2048 -> reject
        HostInfo host = new HostInfo(4096, 3000, 4, 50, true);
        VmSpec spec = createSpec(1500, 2, 10);
        assertFalse(limitChecker.check(spec, host));
    }

    @Test
    @DisplayName("Case 4: Allocation requesting more cores than total minus 1 fails")
    void testExcessiveCoresAllocationFails() {
        HostInfo host = new HostInfo(16384, 12000, 4, 100, true);
        // Total cores 4. Max allowed = 4 - 1 = 3 cores.
        VmSpec spec = createSpec(2048, 4, 20);
        assertFalse(limitChecker.check(spec, host));
    }

    @Test
    @DisplayName("Case 5: Allocation requesting more disk than free minus 10GB fails")
    void testExcessiveDiskAllocationFails() {
        HostInfo host = new HostInfo(16384, 12000, 8, 30, true);
        // Free disk 30GB. Max allowed = 30 - 10 = 20GB. Request 25GB -> reject
        VmSpec spec = createSpec(2048, 2, 25);
        assertFalse(limitChecker.check(spec, host));
    }
}

