package com.minidc.common.model;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a ledger transaction entry in the credit wallet.
 */
public record WalletTransactionInfo(
        UUID id,
        UUID walletId,
        UUID leaseId,
        long amount,
        String type,
        String description,
        Instant createdAt
) {
}
