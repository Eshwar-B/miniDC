package com.minidc.agent.vm;

import com.minidc.common.model.HostInfo;
import com.minidc.common.model.VmInfo;
import com.minidc.common.model.VmSpec;

import java.time.Duration;
import java.util.List;

/**
 * Common hypervisor driver interface abstracting VM creation, polling, destruction, and host metrics.
 */
public interface HypervisorDriver {

    void createVm(VmSpec spec);

    VmInfo waitUntilReady(String vmName, Duration timeout);

    void destroyVm(String vmName);

    List<String> listVms();

    HostInfo hostInfo();
}

