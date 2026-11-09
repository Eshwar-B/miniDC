package com.minidc.common.event;

/**
 * Standard topic names for the MiniDC Kafka event streaming backbone.
 */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String TOPIC_TELEMETRY = "minidc.telemetry.metrics";
    public static final String TOPIC_LEASE_LIFECYCLE = "minidc.lease.lifecycle";
    public static final String TOPIC_AUDIT = "minidc.audit.events";
    public static final String TOPIC_AGENT_COMMANDS = "minidc.agent.commands";
    public static final String TOPIC_RECONCILIATION = "minidc.agent.reconciliation";
    public static final String TOPIC_DLQ = "minidc.dlq";
}

