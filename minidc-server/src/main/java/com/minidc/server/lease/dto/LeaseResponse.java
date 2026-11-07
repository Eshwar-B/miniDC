package com.minidc.server.lease.dto;

import com.minidc.common.enums.LeaseState;
import com.minidc.server.lease.Lease;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing full lease status and connection attributes.
 */
public record LeaseResponse(
        UUID id,
        UUID offerId,
        UUID borrowerId,
        UUID nodeId,
        int ramMb,
        int cores,
        int diskGb,
        int requestedHours,
        LeaseState state,
        String vmName,
        Integer vmPort,
        String vmUser,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        Instant updatedAt,
        String failReason
) {
    public static LeaseResponse from(Lease lease) {
        return new LeaseResponse(
                lease.getId(),
                lease.getOfferId(),
                lease.getBorrowerId(),
                lease.getNodeId(),
                lease.getRamMb(),
                lease.getCores(),
                lease.getDiskGb(),
                lease.getRequestedHours(),
                lease.getState(),
                lease.getVmName(),
                lease.getVmPort(),
                lease.getVmUser(),
                lease.getStartsAt(),
                lease.getEndsAt(),
                lease.getCreatedAt(),
                lease.getUpdatedAt(),
                lease.getFailReason()
        );
    }
}
