package com.minidc.agent.vm;

import com.minidc.agent.config.AgentProperties;
import com.minidc.common.event.ReconciliationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HypervisorReconcilerTest {

    private HypervisorDriver driver;
    private AgentProperties properties;
    private HypervisorReconciler reconciler;
    private UUID testNodeId;

    @BeforeEach
    void setUp() {
        driver = Mockito.mock(HypervisorDriver.class);
        testNodeId = UUID.randomUUID();
        properties = new AgentProperties(
                "ws://localhost:8080/ws/agent",
                testNodeId,
                "token",
                "docker",
                "127.0.0.1",
                "./images",
                "./leases",
                25,
                "33000-33999",
                true,
                "minidc-desktop:latest",
                "127.0.0.1"
        );
        reconciler = new HypervisorReconciler(driver, properties);
    }

    @Test
    void shouldFindAndDestroyOrphanVmsWhenAutoCleanIsEnabled() {
        when(driver.listVms()).thenReturn(List.of(
                "lease-valid1",
                "lease-orphan2",
                "non-minidc-container"
        ));

        Set<String> expected = Set.of("lease-valid1");

        ReconciliationReport report = reconciler.reconcile(expected, true);

        assertEquals(2, report.runningVmNames().size());
        assertEquals(1, report.orphanVmNames().size());
        assertEquals("lease-orphan2", report.orphanVmNames().get(0));
        assertEquals(1, report.cleanedVmNames().size());
        assertEquals("lease-orphan2", report.cleanedVmNames().get(0));

        verify(driver).destroyVm("lease-orphan2");
        verify(driver, never()).destroyVm("lease-valid1");
    }

    @Test
    void shouldNotDestroyVmsWhenAutoCleanIsDisabled() {
        when(driver.listVms()).thenReturn(List.of("lease-orphan3"));

        ReconciliationReport report = reconciler.reconcile(Set.of(), false);

        assertEquals(1, report.orphanVmNames().size());
        assertTrue(report.cleanedVmNames().isEmpty());
        verify(driver, never()).destroyVm("lease-orphan3");
    }
}

