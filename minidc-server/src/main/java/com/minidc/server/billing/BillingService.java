package com.minidc.server.billing;

import com.minidc.common.enums.LeaseState;
import com.minidc.common.model.WalletInfo;
import com.minidc.common.model.WalletTransactionInfo;
import com.minidc.server.audit.AuditService;
import com.minidc.server.lease.Lease;
import com.minidc.server.lease.LeaseRepository;
import com.minidc.server.lease.LeaseService;
import com.minidc.server.node.Node;
import com.minidc.server.node.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Peer-to-peer compute credit metering, double-entry ledger billing, and balance enforcement.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final LeaseRepository leaseRepository;
    private final NodeRepository nodeRepository;
    private final AuditService auditService;

    @Autowired
    @Lazy
    private LeaseService leaseService;

    public BillingService(
            WalletRepository walletRepository,
            WalletTransactionRepository transactionRepository,
            LeaseRepository leaseRepository,
            NodeRepository nodeRepository,
            AuditService auditService
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.leaseRepository = leaseRepository;
        this.nodeRepository = nodeRepository;
        this.auditService = auditService;
    }

    public void setLeaseService(LeaseService leaseService) {
        this.leaseService = leaseService;
    }

    public record RateEstimate(
            int cores,
            int ramMb,
            int diskGb,
            int hours,
            long perMinuteCredits,
            long totalEstimatedCredits
    ) {}

    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            UUID walletId = UUID.randomUUID();
            Instant now = Instant.now();
            // 500 free initial onboarding credits
            Wallet wallet = new Wallet(walletId, userId, 500, now, now);
            Wallet saved = walletRepository.save(wallet);

            WalletTransaction initTx = new WalletTransaction(
                    UUID.randomUUID(),
                    walletId,
                    null,
                    500,
                    "ONBOARDING_BONUS",
                    "Initial platform welcome compute credits",
                    now
            );
            transactionRepository.save(initTx);
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public WalletInfo getWalletInfo(UUID userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return new WalletInfo(wallet.getId(), wallet.getUserId(), wallet.getBalanceCredits(), wallet.getCreatedAt(), wallet.getUpdatedAt());
    }

    @Transactional
    public WalletInfo topUp(UUID userId, long amountCredits) {
        if (amountCredits <= 0) {
            throw new IllegalArgumentException("Top-up amount must be strictly positive");
        }
        Wallet wallet = getOrCreateWallet(userId);
        wallet.setBalanceCredits(wallet.getBalanceCredits() + amountCredits);
        wallet.setUpdatedAt(Instant.now());
        Wallet saved = walletRepository.save(wallet);

        WalletTransaction tx = new WalletTransaction(
                UUID.randomUUID(),
                wallet.getId(),
                null,
                amountCredits,
                "TOPUP",
                "Instant compute credit top-up",
                Instant.now()
        );
        transactionRepository.save(tx);
        auditService.log(userId, null, null, "WALLET_TOPUP", "Added " + amountCredits + " credits");
        return new WalletInfo(saved.getId(), saved.getUserId(), saved.getBalanceCredits(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    public long calculatePerMinuteRate(int cores, int ramMb, int diskGb) {
        long cpuRate = cores * 2L;
        long ramRate = (long) Math.ceil((double) ramMb / 1024.0) * 1L;
        long diskRate = (long) Math.ceil((double) diskGb / 10.0) * 1L;
        return Math.max(1, cpuRate + ramRate + diskRate);
    }

    public RateEstimate estimateCost(int cores, int ramMb, int diskGb, int hours) {
        long perMinute = calculatePerMinuteRate(cores, ramMb, diskGb);
        long total = perMinute * hours * 60L;
        return new RateEstimate(cores, ramMb, diskGb, hours, perMinute, total);
    }

    @Transactional
    public void meterLease(Lease lease) {
        if (lease.getState() != LeaseState.ACTIVE) {
            return;
        }

        long rate = calculatePerMinuteRate(lease.getCores(), lease.getRamMb(), lease.getDiskGb());
        Wallet borrowerWallet = getOrCreateWallet(lease.getBorrowerId());

        // Check if borrower has exhausted credits
        if (borrowerWallet.getBalanceCredits() < rate) {
            log.warn("Borrower {} credit depleted on lease {}. Terminating lease.", lease.getBorrowerId(), lease.getId());
            if (leaseService != null) {
                try {
                    leaseService.kill(lease.getId(), null, "Billing depleted: insufficient compute credits");
                } catch (Exception e) {
                    log.error("Failed to terminate depleted lease {}", lease.getId(), e);
                }
            }
            return;
        }

        // Deduct from borrower
        borrowerWallet.setBalanceCredits(borrowerWallet.getBalanceCredits() - rate);
        borrowerWallet.setUpdatedAt(Instant.now());
        walletRepository.save(borrowerWallet);

        transactionRepository.save(new WalletTransaction(
                UUID.randomUUID(),
                borrowerWallet.getId(),
                lease.getId(),
                -rate,
                "LEASE_DEBIT",
                "Compute usage fee for lease " + lease.getVmName(),
                Instant.now()
        ));

        // Credit lender
        Node node = nodeRepository.findById(lease.getNodeId()).orElse(null);
        if (node != null) {
            Wallet lenderWallet = getOrCreateWallet(node.getOwnerId());
            lenderWallet.setBalanceCredits(lenderWallet.getBalanceCredits() + rate);
            lenderWallet.setUpdatedAt(Instant.now());
            walletRepository.save(lenderWallet);

            transactionRepository.save(new WalletTransaction(
                    UUID.randomUUID(),
                    lenderWallet.getId(),
                    lease.getId(),
                    rate,
                    "LENDER_EARNING",
                    "Compute rental income for lease " + lease.getVmName(),
                    Instant.now()
            ));
        }
    }

    @Scheduled(fixedRate = 60000)
    public void meterAllActiveLeases() {
        List<Lease> activeLeases = leaseRepository.findByState(LeaseState.ACTIVE);
        for (Lease lease : activeLeases) {
            try {
                meterLease(lease);
            } catch (Exception e) {
                log.error("Error metering lease {}", lease.getId(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionInfo> getTransactions(UUID userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId()).stream()
                .map(tx -> new WalletTransactionInfo(
                        tx.getId(),
                        tx.getWalletId(),
                        tx.getLeaseId(),
                        tx.getAmount(),
                        tx.getType(),
                        tx.getDescription(),
                        tx.getCreatedAt()
                ))
                .toList();
    }
}
