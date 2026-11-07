package com.minidc.agent.vm;

import com.minidc.agent.config.AgentProperties;
import com.minidc.agent.system.OsDetector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Hypervisor driver bean configuration selecting libvirt or VirtualBox according to properties or host OS.
 */
@Configuration
public class HypervisorConfig {

    @Bean
    @Primary
    public HypervisorDriver hypervisorDriver(
            AgentProperties properties,
            OsDetector osDetector,
            LibvirtDriver libvirtDriver,
            VirtualBoxDriver virtualBoxDriver
    ) {
        String choice = properties.hypervisor();
        if ("virtualbox".equalsIgnoreCase(choice)) {
            return virtualBoxDriver;
        } else if ("libvirt".equalsIgnoreCase(choice)) {
            return libvirtDriver;
        }
        return osDetector.isWindows() ? virtualBoxDriver : libvirtDriver;
    }
}

