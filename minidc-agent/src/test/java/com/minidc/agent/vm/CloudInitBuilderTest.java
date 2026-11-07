package com.minidc.agent.vm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests ensuring cloud-init user-data and meta-data format compliance and user/shutdown configuration presence.
 */
class CloudInitBuilderTest {

    private final CloudInitBuilder builder = new CloudInitBuilder();

    @Test
    @DisplayName("UserData template contains specified username and automatic shutdown timer")
    void testUserDataContainsUserAndShutdown() {
        String yaml = builder.userData("alice", "secret123", 3);
        assertNotNull(yaml);
        assertTrue(yaml.contains("#cloud-config"));
        assertTrue(yaml.contains("name: alice"));
        assertTrue(yaml.contains("shutdown -P +180"));
        assertTrue(yaml.contains("systemctl restart xrdp"));
    }

    @Test
    @DisplayName("MetaData template contains unique instance-id and hostname")
    void testMetaDataContainsInstanceId() {
        UUID leaseId = UUID.randomUUID();
        String yaml = builder.metaData(leaseId);
        assertNotNull(yaml);
        assertTrue(yaml.contains("instance-id: " + leaseId));
        assertTrue(yaml.contains("local-hostname: minidc"));
    }
}

