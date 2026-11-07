package com.minidc.server.lease;

/**
 * Exception thrown when requested resources exceed available capacity on a node or offer.
 */
public class CapacityExceededException extends RuntimeException {

    public CapacityExceededException(String message) {
        super(message);
    }
}
