package com.minidc.agent.vm;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Generates cloud-init user-data and meta-data configurations for automated guest provisioning.
 */
@Component
public class CloudInitBuilder {

    public String userData(String vmUser, String vmPassword, int hours) {
        int shutdownMinutes = hours * 60;
        return """
                #cloud-config
                hostname: minidc
                users:
                  - name: %s
                    groups: [sudo]
                    shell: /bin/bash
                    sudo: ALL=(ALL) NOPASSWD:ALL
                    lock_passwd: false
                chpasswd: { expire: false }
                ssh_pwauth: false
                runcmd:
                  - echo "%s:%s" | chpasswd
                  - systemctl restart xrdp
                  - shutdown -P +%d
                """.formatted(vmUser, vmUser, vmPassword, shutdownMinutes);
    }

    public String metaData(UUID leaseId) {
        return """
                instance-id: %s
                local-hostname: minidc
                """.formatted(leaseId.toString());
    }
}

