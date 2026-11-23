package com.minidc.common.model;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a user's compute credit balance wallet.
 */
public record WalletInfo(
        UUID id,
        UUID userId,
        long balanceCredits,
        Instant createdAt,
        Instant updatedAt
) {
}
