package com.minidc.common.ws;

/**
 * Message types supported across the agent-server WebSocket protocol.
 */
public enum MessageType {
    REGISTER,
    HEARTBEAT,
    VM_READY,
    VM_FAILED,
    VM_DESTROYED,
    VM_STATS,
    CREATE_VM,
    DESTROY_VM,
    PAUSE_NODE,
    RESUME_NODE
}
