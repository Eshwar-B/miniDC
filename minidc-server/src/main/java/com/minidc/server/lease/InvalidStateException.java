package com.minidc.server.lease;

import com.minidc.common.enums.LeaseState;

/**
 * Exception thrown when an illegal lease state transition is attempted.
 */
public class InvalidStateException extends RuntimeException {

    private final LeaseState from;
    private final LeaseState to;

    public InvalidStateException(LeaseState from, LeaseState to) {
        super("Invalid lease state transition from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public LeaseState getFrom() {
        return from;
    }

    public LeaseState getTo() {
        return to;
    }
}
