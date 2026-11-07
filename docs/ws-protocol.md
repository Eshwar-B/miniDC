# MiniDC Agent WebSocket Protocol

The node agent opens an outbound WebSocket connection to `wss://<server-host>:8443/ws/agent`.

### Handshake Authentication Headers
- `X-Node-Id`: `<UUID>`
- `X-Agent-Token`: `<one-time-plain-token>`

Server compares `X-Agent-Token` against the BCrypt hash in `nodes.token_hash`.

### Envelope Structure
Every frame transferred across the WebSocket channel is formatted as:
```json
{
  "type": "<MESSAGE_TYPE>",
  "id": "<UUID>",
  "payload": { ... }
}
```

---

## 1. Agent to Server Messages

### 1.1 `REGISTER`
Dispatched upon initial connection to report hardware capabilities.
```json
{
  "type": "REGISTER",
  "id": "1a2b3c4d-0001-4000-8000-000000000001",
  "payload": {
    "ramMb": 32768,
    "cores": 16,
    "diskGb": 512,
    "address": "192.168.1.150",
    "agentVersion": "1.0.0",
    "kvmOk": true
  }
}
```

### 1.2 `HEARTBEAT`
Dispatched every 15 seconds to report operational vitality.
```json
{
  "type": "HEARTBEAT",
  "id": "1a2b3c4d-0002-4000-8000-000000000002",
  "payload": {
    "freeRamMb": 18432,
    "load1": 0.42,
    "activeVms": 1,
    "diskFreeGb": 380
  }
}
```

### 1.3 `VM_READY`
Sent when a newly created VM has booted and xrdp is accepting connections.
```json
{
  "type": "VM_READY",
  "id": "1a2b3c4d-0003-4000-8000-000000000003",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a",
    "vmName": "lease-e4f8a3c2",
    "port": 33001
  }
}
```

### 1.4 `VM_FAILED`
Sent when VM creation or startup fails.
```json
{
  "type": "VM_FAILED",
  "id": "1a2b3c4d-0004-4000-8000-000000000004",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a",
    "reason": "virt-install timed out waiting for domain IP address"
  }
}
```

### 1.5 `VM_DESTROYED`
Sent after a VM has been powered down and ephemeral storage deleted.
```json
{
  "type": "VM_DESTROYED",
  "id": "1a2b3c4d-0005-4000-8000-000000000005",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a"
  }
}
```

### 1.6 `VM_STATS`
Periodic resource utilization report for an active lease.
```json
{
  "type": "VM_STATS",
  "id": "1a2b3c4d-0006-4000-8000-000000000006",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a",
    "cpuPct": 14.8,
    "memMb": 1820
  }
}
```

---

## 2. Server to Agent Messages

### 2.1 `CREATE_VM`
Instructs the agent to provision a guest VM overlay and start execution.
```json
{
  "type": "CREATE_VM",
  "id": "9f8e7d6c-0001-4000-8000-000000000001",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a",
    "ramMb": 4096,
    "cores": 2,
    "diskGb": 20,
    "hours": 2,
    "vmUser": "minidc",
    "vmPassword": "temporary-password"
  }
}
```

### 2.2 `DESTROY_VM`
Instructs the agent to immediately kill and purge a VM.
```json
{
  "type": "DESTROY_VM",
  "id": "9f8e7d6c-0002-4000-8000-000000000002",
  "payload": {
    "leaseId": "e4f8a3c2-1d5b-4c7e-9a2f-8b3c4d5e6f7a"
  }
}
```

### 2.3 `PAUSE_NODE` / `RESUME_NODE`
Temporarily halts or resumes new VM lease scheduling on the host.
```json
{
  "type": "PAUSE_NODE",
  "id": "9f8e7d6c-0003-4000-8000-000000000003",
  "payload": null
}
```

