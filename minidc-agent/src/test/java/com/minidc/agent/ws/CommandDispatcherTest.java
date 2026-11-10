package com.minidc.agent.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidc.agent.net.EgressRules;
import com.minidc.agent.net.WireGuardTunnelManager;
import com.minidc.agent.system.HostStats;
import com.minidc.agent.system.LimitChecker;
import com.minidc.agent.vm.HypervisorReconciler;
import com.minidc.agent.vm.VmManager;
import com.minidc.common.model.HostInfo;
import com.minidc.common.model.WireGuardPeerConfig;
import com.minidc.common.ws.MessageType;
import com.minidc.common.ws.WsMessage;
import com.minidc.common.ws.payload.CreateVmPayload;
import com.minidc.common.ws.payload.DestroyVmPayload;
import com.minidc.common.ws.payload.VmReadyPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandDispatcherTest {

    private VmManager vmManager;
    private LimitChecker limitChecker;
    private HostStats hostStats;
    private ObjectMapper objectMapper;
    private ServerConnection serverConnection;
    private HypervisorReconciler hypervisorReconciler;
    private EgressRules egressRules;
    private WireGuardTunnelManager wireGuardTunnelManager;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        vmManager = Mockito.mock(VmManager.class);
        limitChecker = Mockito.mock(LimitChecker.class);
        hostStats = Mockito.mock(HostStats.class);
        objectMapper = new ObjectMapper();
        serverConnection = Mockito.mock(ServerConnection.class);
        hypervisorReconciler = Mockito.mock(HypervisorReconciler.class);
        egressRules = Mockito.mock(EgressRules.class);
        wireGuardTunnelManager = Mockito.mock(WireGuardTunnelManager.class);

        dispatcher = new CommandDispatcher(
                vmManager,
                limitChecker,
                hostStats,
                objectMapper,
                serverConnection,
                hypervisorReconciler,
                egressRules,
                wireGuardTunnelManager
        );
    }

    @Test
    void shouldHandleCreateVmAndApplyEgressRules() throws Exception {
        UUID leaseId = UUID.randomUUID();
        CreateVmPayload payload = new CreateVmPayload(leaseId, 2048, 2, 20, 2, "user", "pass");
        WsMessage msg = new WsMessage(MessageType.CREATE_VM, objectMapper.valueToTree(payload));

        when(hostStats.getHostInfo()).thenReturn(new HostInfo(8192, 4096, 8, 50, true));
        when(limitChecker.check(any(), any())).thenReturn(true);
        when(vmManager.create(any())).thenReturn(new VmReadyPayload(leaseId, "lease-123", 33001));

        dispatcher.dispatch(msg);

        verify(vmManager).create(any());
        verify(egressRules).applyLeaseInboundRule(eq(leaseId), eq(33001));
        verify(serverConnection).send(eq(MessageType.VM_READY), any(VmReadyPayload.class));
    }

    @Test
    void shouldHandleDestroyVmAndRemoveEgressRules() {
        UUID leaseId = UUID.randomUUID();
        DestroyVmPayload payload = new DestroyVmPayload(leaseId);
        WsMessage msg = new WsMessage(MessageType.DESTROY_VM, objectMapper.valueToTree(payload));

        dispatcher.dispatch(msg);

        verify(vmManager).destroy(eq(leaseId));
        verify(egressRules).removeLeaseInboundRule(eq(leaseId));
        verify(serverConnection).send(eq(MessageType.VM_DESTROYED), any(DestroyVmPayload.class));
    }

    @Test
    void shouldHandleEgressConfig() throws Exception {
        WireGuardPeerConfig config = new WireGuardPeerConfig(
                "10.44.0.2/24", "priv=", "pub=", "vpn:51820", "0.0.0.0/0", "1.1.1.1", 25
        );
        WsMessage msg = new WsMessage(MessageType.EGRESS_CONFIG, objectMapper.valueToTree(config));

        dispatcher.dispatch(msg);

        verify(wireGuardTunnelManager).startTunnel(any(WireGuardPeerConfig.class));
    }
}
