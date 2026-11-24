package com.minidc.server.billing;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.model.WalletInfo;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.lease.LeaseService;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingServiceTest {

    private WalletRepository walletRepository;
    private WalletTransactionRepository transactionRepository;
    private LeaseRepository leaseRepository;
    private NodeRepository nodeRepository;
    private AuditService auditService;
    private LeaseService leaseService;
    private BillingService billingService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        walletRepository = Mockito.mock(WalletRepository.class);
        transactionRepository = Mockito.mock(WalletTransactionRepository.class);
        leaseRepository = Mockito.mock(LeaseRepository.class);
        nodeRepository = Mockito.mock(NodeRepository.class);
        auditService = Mockito.mock(AuditService.class);
        leaseService = Mockito.mock(LeaseService.class);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();

        billingService = new BillingService(walletRepository, transactionRepository, leaseRepository, nodeRepository, auditService, meterRegistry);
        billingService.setLeaseService(leaseService);
    }

    @Test
    void shouldInitializeWalletWithWelcomeCredits() {
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Wallet wallet = billingService.getOrCreateWallet(userId);

        assertNotNull(wallet);
        assertEquals(500, wallet.getBalanceCredits());
        verify(transactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void shouldTopUpWalletCredits() {
        Wallet wallet = new Wallet(UUID.randomUUID(), userId, 500, Instant.now(), Instant.now());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        WalletInfo updated = billingService.topUp(userId, 300);

        assertEquals(800, updated.balanceCredits());
        verify(transactionRepository).save(any(WalletTransaction.class));
    }

    @Test
    void shouldCalculateAccuratePerMinuteRates() {
        // 2 cores * 2 = 4; 4096MB / 1024 * 1 = 4; 20GB / 10 * 1 = 2 -> total = 10
        long rate = billingService.calculatePerMinuteRate(2, 4096, 20);
        assertEquals(10, rate);

        BillingService.RateEstimate estimate = billingService.estimateCost(2, 4096, 20, 2);
        assertEquals(10, estimate.perMinuteCredits());
        assertEquals(1200, estimate.totalEstimatedCredits()); // 10 * 120 mins
    }

    @Test
    void shouldMeterActiveLeaseAndDebitBorrowerAndCreditLender() {
        UUID lenderId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(userId);
        lease.setNodeId(nodeId);
        lease.setCores(2);
        lease.setRamMb(4096);
        lease.setDiskGb(20);
        lease.setState(LeaseState.ACTIVE);
        lease.setVmName("lease-123");

        Node node = new Node();
        node.setId(nodeId);
        node.setOwnerId(lenderId);
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

        Wallet borrowerWallet = new Wallet(UUID.randomUUID(), userId, 500, Instant.now(), Instant.now());
        Wallet lenderWallet = new Wallet(UUID.randomUUID(), lenderId, 100, Instant.now(), Instant.now());

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(borrowerWallet));
        when(walletRepository.findByUserId(lenderId)).thenReturn(Optional.of(lenderWallet));

        billingService.meterLease(lease);

        // 10 credits debited from borrower and credited to lender
        assertEquals(490, borrowerWallet.getBalanceCredits());
        assertEquals(110, lenderWallet.getBalanceCredits());
    }

    @Test
    void shouldKillLeaseWhenBorrowerCreditDepleted() {
        UUID leaseId = UUID.randomUUID();
        Lease lease = new Lease();
        lease.setId(leaseId);
        lease.setBorrowerId(userId);
        lease.setCores(4);
        lease.setRamMb(8192);
        lease.setDiskGb(50);
        lease.setState(LeaseState.ACTIVE);

        // Rate = 4*2 + 8*1 + 5*1 = 21 credits. Balance = 5 -> insufficient!
        Wallet borrowerWallet = new Wallet(UUID.randomUUID(), userId, 5, Instant.now(), Instant.now());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(borrowerWallet));

        billingService.meterLease(lease);

        verify(leaseService).kill(Mockito.eq(leaseId), Mockito.isNull(), Mockito.contains("Billing depleted"));
    }
}
