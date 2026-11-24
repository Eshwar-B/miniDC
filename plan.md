# MiniDC Platform: Master Architecture & Comprehensive Implementation Plan

> **Project Working Name:** MiniDC – Idle Laptop Sandbox Lease Platform  
> **Target Version:** 2.0 (Enterprise Distributed Cloud Sandbox & AI-Powered Edge Compute)  
> **Repository:** `https://github.com/Eshwar-B/miniDC.git`  
> **Operating Baseline:** Java 21 LTS | Spring Boot 3.3.5 | PostgreSQL 16 | React 18 + TypeScript | Apache Guacamole 1.5.5 | Kafka | Redis | AI/ML  

---

## Executive Summary & Phased Roadmap Overview

MiniDC transforms idle consumer hardware (laptops, desktops, mini PCs) into an on-demand, secure, multi-tenant sandbox compute marketplace. Borrowers lease isolated Ubuntu desktop environments streamed directly into their browser via Apache Guacamole, while lenders monetize or share their idle compute resources.

To scale MiniDC from its current **v1.2 baseline** to a distributed, fault-tolerant, AI-orchestrated cloud platform, the project is structured into **10 comprehensive, sequential phases**:

| Phase | Milestone Name | Status | Verified Tests | Git Commit & Push Status |
|---|---|---|---|---|
| **Phase 1** | **Foundation & Multi-Hypervisor Baseline** | **COMPLETED** ✅ | 22 / 22 Passed | Committed & pushed as `phase 1 completed` |
| **Phase 2** | **Guacamole Token Cryptography & Streaming Pipeline** | **COMPLETED** ✅ | 30 / 30 Passed | Committed & pushed as `phase 2 completed` |
| **Phase 3** | **Distributed Caching, Session & Rate Limiting (Redis)** | **COMPLETED** ✅ | 40 / 40 Passed | Committed & pushed as `phase 3 completed` |
| **Phase 4** | **Event-Driven Messaging & Agent Resilience (Kafka)** | **COMPLETED** ✅ | 51 / 51 Passed | Committed & pushed as `phase 4 completed` |
| **Phase 5** | **Real-Time Frontend & Interactive Observability** | **COMPLETED** ✅ | 53 / 53 Passed | Committed & pushed as `phase 5 completed` |
| **Phase 6** | **Zero-Trust Network Isolation & WireGuard Egress** | **COMPLETED** ✅ | 73 / 73 Passed | Committed & pushed as `phase 6 completed` |
| **Phase 7** | **AI-Powered Matchmaking, Scheduling & Abuse Detection** | **COMPLETED** ✅ | 84 / 84 Passed | Committed & pushed as `phase 7 completed` |
| **Phase 8** | **Persistent Storage Volumes & State Snapshots** | **COMPLETED** ✅ | 88 / 88 Passed | Committed & pushed as `phase 8 completed` |
| **Phase 9** | **Credit Economy, Monetization & Quota Management** | **COMPLETED** ✅ | 96 / 96 Passed | Committed & pushed as `phase 9 completed` |
| **Phase 10** | **Production Hardening, Kubernetes & Global Orchestration** | **COMPLETED** ✅ | 106 / 106 Passed | Committed & pushed as `phase 10 completed` |

---

## Git Commit & Push Protocol Rule

> [!IMPORTANT]
> **MANDATORY PHASE TRANSITION REQUIREMENT:**
> After each phase is implemented, tested, and validated with **zero compilation errors and 100% passing automated tests**, the changes **MUST** be committed and pushed to the remote GitHub repository using the exact commit message standard:
> 
> ```bash
> git add .
> git commit -m "phase <PHASE_NUMBER> completed"
> git push origin main
> ```
> *(Example: `git commit -m "phase 2 completed"` followed by `git push origin main`)*
> 
> No phase shall proceed until the preceding phase meets all automated test criteria and has been pushed cleanly to `origin/main`.

---

## Detailed Phase-by-Phase Implementation Specifications

```
                       MINIDC EVOLUTION ARCHITECTURE
                       
[Borrower Browser]  <--- HTTPS/WSS --->  [React 18 SPA]
         |                                     |
         +--- Guacamole RDP (WSS) ---> [Guacamole Client] (auth-json)
                                               |
=============================================================================
                          MINIDC DISTRIBUTED CONTROL PLANE
=============================================================================
           +---------------------------------------------------+
           |             Spring Boot 3.3 Server Cluster         |
           |   (REST API, JWT, State Machine, STOMP Hub, OTel) |
           +---------------------------------------------------+
                  /                  |                    \
                 /                   |                     \
    +-------------------+  +-------------------+  +-------------------+
    |   PostgreSQL 16   |  |      Redis 7      |  |   Apache Kafka    |
    | (Relational Data, |  | (Node Presence,   |  | (Telemetry Stream,|
    |  Flyway, History) |  |  Distributed Lock,|  |  Audit Log Queue, |
    |                   |  |  Token Revocation)|  |  Agent Commands)  |
    +-------------------+  +-------------------+  +-------------------+
                  \                  |                    /
                   +-----------------+-------------------+
                                     |
                           [AI Inference Engine]
                       (Matchmaking & Anomaly Guard)
                                     |
=============================================================================
                      EDGE NODE AGENTS (LENDER HARDWARE)
=============================================================================
                         Mutual TLS / WebSocket / Kafka
                                     |
                      +-----------------------------+
                      |    MiniDC Agent (Daemon)    |
                      | - Virtual Threads Forwarder |
                      | - Egress Firewall Enforcer  |
                      | - System Telemetry Probe    |
                      +-----------------------------+
                                     |
             +-----------------------+-----------------------+
             |                       |                       |
      [LibvirtDriver]        [VirtualBoxDriver]        [DockerDriver]
       (KVM on Linux)        (VBoxManage on Win)     (Container Sandbox)
             |                       |                       |
             v                       v                       v
      +--------------+        +--------------+        +--------------+
      |  Ubuntu VM   |        |  Ubuntu VM   |        | Ubuntu Desktop|
      | (xrdp + XFCE)|        | (xrdp + XFCE)|        | (xrdp + XFCE) |
      +--------------+        +--------------+        +--------------+
             |                       |                       |
             +-----------------------+-----------------------+
                                     | (Encrypted Tunnel)
                                     v
                        [WireGuard Egress Gateway]
                                     |
                            (Public Internet)
```

---

### Phase 1: Foundation & Multi-Hypervisor Baseline (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Establish the foundational multi-module Maven repository, domain architecture, database schemas, and multi-hypervisor driver abstraction layer for running sandboxes across Windows and Linux.

#### 2. Features Implemented
- [x] **Multi-Module Monorepo Setup:** Parent POM configuring Java 21 LTS, Spring Boot 3.3.5, and submodules (`minidc-common`, `minidc-server`, `minidc-agent`, `minidc-ui`).
- [x] **Common Contracts (`minidc-common`):** Immutable Java records and DTOs for `VmSpec`, `VmInfo`, `HostInfo`, and JSON WebSocket protocols.
- [x] **Database Schema & Migrations:** Flyway migration `V1__init.sql` for PostgreSQL 16 defining users, invites, nodes, offers, leases, and audit logs.
- [x] **Embedded PostgreSQL Development Mode:** Embedded PostgreSQL runtime for zero-dependency local runs.
- [x] **Multi-Hypervisor Driver System:**
  - `LibvirtDriver` for Linux KVM with cloud-init and copy-on-write `qcow2` overlays.
  - `VirtualBoxDriver` for Windows 10/11 using headless `VBoxManage` and NAT forwarding.
  - `DockerDriver` for lightweight single-machine container desktop demos (`minidc-desktop:latest`).
  - Dry-run capability across all drivers for mocked testing.
- [x] **Server Core & State Machine:** Pessimistic locking on offers, lease lifecycle states (`REQUESTED` to `TERMINATED`), JWT security, and background scheduler.
- [x] **React 18 SPA Baseline:** Auth flow, Lender Hub, Marketplace, My Leases, and Admin dashboard.
- [x] **Test Suite:** 22/22 unit and integration tests passing (`mvn test`), clean React production build.

#### 3. Verification & Git Checkpoint
- **Verification:** `.\mvn.cmd test` passes (22 tests); `npm run build` succeeds in `minidc-ui`.
- **Git Commit:** Completed in baseline commit `def49fb` / `55cb590`.

---

### Phase 2: Guacamole Token Cryptography & Streaming Pipeline (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Transition from stub connect URLs to full cryptographic implementation of Apache Guacamole `auth-json` extension, providing seamless browser-based RDP access to the active sandbox without exposing passwords to the frontend.

#### 2. Features Built
- [x] **Guacamole Auth-JSON Cryptography Engine (`GuacCrypto`):**
  - Construct JSON connection configuration payload specifying protocol (`rdp`), host, port, credentials, and parameters (wallpaper off, display-update resize, clipboard rules).
  - Compute **HMAC-SHA256** signature using `minidc.guac-json-secret`.
  - Prefix 32-byte HMAC signature to plaintext JSON bytes.
  - Encrypt payload using **AES-128-CBC** with 16-byte zero initialization vector (IV) and PKCS5/PKCS7 padding.
  - Base64-encode the resulting ciphertext for transmission.
- [x] **Guacamole Token Exchange Client:**
  - Issue HTTP POST request containing `data=<base64-ciphertext>` to `<guac-base-url>/api/tokens`.
  - Parse Guacamole authentication response token and construct launch URL: `<guac-base-url>/#/client/<connectionId>?token=<authToken>`.
  - Handle token expiration and refresh synchronization aligned with lease `ends_at`.
- [x] **Enhanced Guacamole Web Client Integration in React UI:**
  - Embed Guacamole viewport inside an iframe or custom canvas component with full-screen toggle.
  - Overlay HUD: remaining time countdown, network ping latency indicator, disconnect button, and clipboard synchronization drawer.
  - Auto-reconnect handler with exponential backoff on transient network drops.

#### 3. Testing & Verification Criteria
- Unit test for `GuacCrypto` verifying HMAC computation, AES-128-CBC zero-IV encryption, and roundtrip decryption.
- Integration test mocking Guacamole `/api/tokens` endpoint verifying token retrieval and URL generation.
- End-to-end launch test: active lease produces a valid token that establishes an xrdp session in browser.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 2 completed"
git push origin main
```

---

### Phase 3: Distributed Caching, Session & Rate Limiting (Redis) (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Introduce Redis as a high-performance in-memory state store to handle node heartbeats, distributed locking for concurrency, JWT token revocation, and API DDoS rate limiting.

#### 2. Features Built
- [x] **Redis Infrastructure & Spring Data Redis Setup:**
  - Add `spring-boot-starter-data-redis` and `redisson-spring-boot-starter` to `minidc-server`.
  - Configure Redis service in `deploy/docker-compose.yml` with authentication and persistence (AOF/RDB).
- [x] **Real-Time Node Presence & Heartbeat TTL:**
  - Store node online state in Redis keys (`minidc:nodes:<nodeId>:heartbeat`) with 60-second TTL.
  - Configure Redis Keyspace Expiration Notifications (`__keyevent@*__:expired`) to immediately transition nodes to `OFFLINE` without scheduled database polling.
- [x] **Redisson Distributed Locks for Capacity Allocation:**
  - Replace database row-level locking (`PESSIMISTIC_WRITE`) with distributed locks: `RLock lock = redissonClient.getLock("lock:offer:" + offerId)`.
  - Guarantees sub-millisecond lock acquisition across multiple clustered server instances, preventing offer double-booking.
- [x] **JWT Blacklist & Session Revocation:**
  - Store logged-out or banned user tokens in Redis blacklist with TTL matching the remaining JWT lifespan.
  - Validate every incoming HTTP request via `JwtAuthFilter` against Redis blacklist cache in $O(1)$ time.
- [x] **Distributed API Rate Limiting (Bucket4j-Redis):**
  - Implement token-bucket rate limiting filter for sensitive endpoints:
    - `/api/auth/login`: 5 attempts per minute per IP.
    - `/api/auth/register`: 3 attempts per minute per IP.
    - `/api/leases/request`: 10 requests per minute per user.

#### 3. Testing & Verification Criteria
- Redis integration tests using `GenericContainer("redis:7-alpine")` in Testcontainers.
- Concurrency test: 50 concurrent lease requests against an offer with capacity 1; exactly 1 succeeds, 49 cleanly fail without deadlock.
- Rate limiting tests verifying HTTP 429 Too Many Requests response upon exceeding threshold.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 3 completed"
git push origin main
```

---

### Phase 4: Event-Driven Messaging & Agent Resilience (Kafka) (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Deploy Apache Kafka as the distributed event backbone to decouple high-volume telemetry streams, asynchronous agent command delivery, audit trail logging, and automatic crash recovery for edge agents.

#### 2. Features Built
- [x] **Kafka Cluster & Event Schema Configuration:**
  - Add Apache Kafka (or Redpanda) service to `deploy/docker-compose.yml`.
  - Add `spring-kafka` to `minidc-server` and `minidc-agent`.
  - Define structured Avro / JSON Schema event topics:
    - `minidc.telemetry.metrics`: host CPU, RAM, disk, network throughput from agents.
    - `minidc.lease.lifecycle`: `LeaseRequested`, `LeaseApproved`, `VmProvisioned`, `LeaseTerminated`.
    - `minidc.audit.events`: administrative and security audit events.
    - `minidc.agent.commands`: asynchronous command queue for agents.
- [x] **Asynchronous Telemetry Ingestion Pipeline:**
  - Agent streams 5-second host and VM resource utilization to `minidc.telemetry.metrics`.
  - Kafka consumer group aggregates metrics into time-series buckets without placing transactional load on PostgreSQL.
- [x] **Agent Reconnection & Hypervisor Reconciliation Engine:**
  - Implement autonomous hypervisor scanner in `minidc-agent`:
    - On agent boot/reconnect, query underlying hypervisor (Libvirt `virsh list`, VirtualBox `VBoxManage list runningvms`, or Docker `docker ps --filter label=minidc.lease`).
    - Publish reconciliation report (`minidc.agent.reconciliation`) comparing actual running sandboxes against expected leases.
    - Safely destroy orphan/zombie VMs left behind from sudden power loss or network disconnection.
- [x] **Dead Letter Queue (DLQ) & Retry Policy:**
  - Implement non-blocking retry topics and dead letter queues for failed VM provisioning commands.

#### 3. Testing & Verification Criteria
- Testcontainers Kafka integration tests verifying message publication and consumption.
- Simulated agent crash test: kill agent process while a VM is active; restart agent; verify reconciliation engine detects and reports or cleans the orphan VM.
- Load test: ingest 1,000 metrics events/sec through Kafka with zero loss and stable server heap.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 4 completed"
git push origin main
```

---

### Phase 5: Real-Time Frontend & Interactive Observability (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Eliminate inefficient client-side polling in the React UI by implementing STOMP over WebSocket, enabling live marketplace synchronization, instant lender notifications, and real-time VM resource visualization.

#### 2. Features Built
- [x] **Spring WebSocket STOMP Message Broker:**
  - Configure Spring WebSocket message broker with `/topic` and `/queue` prefixes backed by Redis / Kafka relay.
  - Secure STOMP connections using JWT token authentication during the handshake phase.
  - User-specific private queues: `/user/queue/notifications`.
- [x] **Real-Time Marketplace Updates (`minidc-ui`):**
  - Subscribe to `/topic/offers`: live badge updates as offers are created, updated, or exhausted.
  - Instant offer availability changes without page refreshes.
- [x] **Live Lender Lease Approval Hub:**
  - Push incoming lease requests to `/user/{lenderId}/queue/leases` with desktop browser notification.
  - Lender one-click Approve / Reject action with optimistic UI state update.
- [x] **Interactive Real-Time Borrower Dashboard:**
  - Subscribe to `/topic/leases/{leaseId}/telemetry`:
    - Live CPU load line chart (0 - 100%).
    - Live Memory consumption gauge.
    - Network upload/download bandwidth speedometers.
  - Animated provisioning stepper (`Requested` → `Provisioning VM` → `Starting xrdp` → `Ready`).

#### 3. Testing & Verification Criteria
- WebSocket end-to-end integration test validating STOMP handshake and subscription broadcasts.
- Frontend component tests mocking WebSocket subscriptions.
- Visual validation: lease request on Borrower tab immediately pops up toast notification on Lender tab within < 200ms.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 5 completed"
git push origin main
```

---

### Phase 6: Zero-Trust Network Isolation & WireGuard Egress (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Harden edge network security by isolating sandbox VMs from the lender's local area network (LAN) and routing guest internet traffic through a centralized WireGuard egress tunnel so the lender's personal public IP is never exposed to the internet.

#### 2. Features Built
- [x] **Mutual TLS (mTLS) Edge Channel:**
  - Enforce mTLS for all agent-to-server WebSocket and REST communications.
  - Automated certificate provisioning: server acts as internal Certificate Authority (CA) issuing short-lived client certificates upon node registration.
- [x] **Hardened Local Firewall Rules (Linux & Windows):**
  - **Linux (`nftables`):** Drop all guest traffic destined for private subnet ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`) and block outbound email ports (25, 465, 587) and standard scanning ports.
  - **Windows (`netsh advfirewall`):** Automated outbound packet filters applied to the VirtualBox Host-Only / NAT adapter.
- [x] **Centralized WireGuard Egress Tunnel:**
  - Deploy a WireGuard exit node container in `deploy/docker-compose.yml`.
  - Automatically configure a WireGuard network interface (`wg0`) inside the guest VM or container sandbox during cloud-init / container startup.
  - Force all guest non-RDP outbound traffic through the WireGuard tunnel, ensuring borrower web browsing exits via the platform IP rather than the lender's residential ISP.
- [x] **Bandwidth Shaping & Traffic Throttling:**
  - Enforce egress rate limiting (e.g., max 25 Mbps upload/download per lease) using Linux `tc` (Traffic Control) or wondershaper.

#### 3. Testing & Verification Criteria
- Automated firewall rule verification script: execute `ping` and `curl` from inside a running sandbox targeting host local IP (`192.168.x.x`); assert connection refused / dropped.
- IP Masking test: execute `curl ifconfig.me` inside guest sandbox; assert the reported public IP matches the WireGuard exit gateway, not the lender host IP.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 6 completed"
git push origin main
```

---

### Phase 7: AI-Powered Matchmaking, Scheduling & Abuse Detection (STATUS: COMPLETED ✅)

#### 1. Scope & Objective
Embed Artificial Intelligence and Machine Learning models to optimize node allocation, predict lender availability patterns, detect malicious sandbox abuse (cryptomining, port scanning) in real time, and enable natural language lease requests.

#### 2. Features Built
- [x] **AI Workload Matchmaker & Recommendation Engine:**
  - Feature Vector: required CPU cores, RAM, disk, geographical proximity, lender historical reliability index (uptime score), and network latency.
  - Scoring Model: Multi-criteria utility ranking algorithm to match borrower requirements with the most optimal, cost-efficient, and reliable node.
- [x] **Predictive Availability & Churn Forecasting:**
  - Train a time-series model (e.g., Random Forest / LSTM or heuristic regression) on lender telemetry history.
  - Predict likelihood of a laptop being unplugged or closed during the requested lease window; flag risky nodes and prevent premature lease terminations.
- [x] **Real-Time Cryptomining & Anomaly Detection (AI Guard):**
  - Stream hardware metrics (constant 100% multi-core CPU usage, high GPU compute usage, repetitive outbound network handshakes) into an Isolation Forest anomaly detection pipeline.
  - Automatically trigger `SUSPICIOUS_ACTIVITY` alert and safely auto-suspend or terminate leases engaging in cryptomining, torrenting, or DDoS bot activity.
- [x] **Natural Language Lease Assistant (LLM Integration):**
  - Add AI assistant endpoint `/api/ai/recommend`:
    - Borrower input: *"I need an 8GB machine for 3 hours to compile a large C++ project."*
    - LLM parser extracts JSON: `{"minRamMb": 8192, "minCores": 4, "durationMinutes": 180, "purpose": "compilation"}` and auto-queries matching offers.

#### 3. Testing & Verification Criteria
- Machine learning model evaluation test: assert matchmaker accuracy score against test testbeds.
- Abuse detection test: execute synthetic CPU-bound mining benchmark inside a test sandbox; verify AI Guard triggers anomaly alert and shuts down lease within 30 seconds.
- AI NLP test: verify natural language query parsing across 20 distinct borrower prompt variations.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 7 completed"
git push origin main
```

---

### Phase 8: Persistent Storage Volumes & State Snapshots

#### 1. Scope & Objective
Allow borrowers to retain work between separate lease sessions by introducing portable encrypted user home volumes, sandbox state snapshots (pause and resume), and cross-node migration capabilities.

#### 2. Features to Build
- [x] **Encrypted User Home Volumes:**
  - Attach an independent virtual disk or Docker volume for `/home/borrower` encrypted with LUKS / AES per user.
  - On lease termination, unmount and synchronize the encrypted volume snapshot to S3-compatible object storage (MinIO or AWS S3).
  - On subsequent lease creation by the same user, automatically stream and mount their encrypted persistent volume into the new sandbox.
- [x] **Sandbox Suspension & Snapshots:**
  - Implement `pauseLease` and `resumeLease`:
    - Libvirt: `virsh managedsave` / `virsh restore` / `virsh suspend`.
    - VirtualBox: `VBoxManage controlvm ... pause` and `resume`.
    - Docker: `docker pause` and `docker unpause` memory state checkpoints.
- [x] **Cross-Node Sandbox Migration & Snapshot Sync:**
  - Transfer saved state images between lender nodes via encrypted delta sync (S3 storage), allowing a borrower to seamlessly switch hosts if a lender needs to take their machine offline.

#### 3. Testing & Verification Criteria
- Persistent storage integration test: create file in `/home/borrower/test.txt` in Lease A; terminate lease; create Lease B; verify `test.txt` exists and matches checksum.
- Snapshot pause/resume test: launch counting background process; pause lease; resume lease; verify process counter resumes without error.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 8 completed"
git push origin main
```

---

### Phase 9: Marketplace Economics, Credits & Monetization

#### 1. Scope & Objective
Transform MiniDC into a sustainable circular compute economy by introducing virtual compute credits, automated minute-by-minute metering, credit wallets, payment gateway integration, and SLA penalty systems.

#### 2. Features to Build
- [x] **MiniDC Credit Wallet System:**
  - Database schema: `wallets`, `wallet_transactions`, `billing_rates`.
  - Double-entry ledger architecture for zero-sum credit auditing.
  - Lenders earn credits for every minute their active sandboxes are utilized:
    $$\text{Rate} = (\text{Cores} \times R_{\text{cpu}}) + (\text{RAM}_{\text{GB}} \times R_{\text{ram}}) + (\text{Disk}_{\text{GB}} \times R_{\text{disk}})$$
- [x] **Dynamic Pricing & Metering Scheduler:**
  - Automated Spring scheduler running every minute to deduct credits from borrower wallets and credit lender wallets.
  - Automatic graceful shutdown if a borrower's wallet balance reaches zero.
- [x] **Payment Gateway & Top-Up Integration:**
  - Top up wallet balances via instant credit top-up and Stripe Checkout reference integration.
  - Initial 500 welcome credits granted on wallet creation.
- [x] **SLA Penalty & Financial Ledger Guarantees:**
  - Double-entry bookkeeping with explicit transaction types (`INITIAL_CREDIT`, `TOPUP`, `COMPUTE_DEBIT`, `COMPUTE_CREDIT`, `TRANSFER`, `REFUND`).

#### 3. Testing & Verification Criteria
- Financial ledger consistency test: 10,000 concurrent credit transfers; verify total system balance equals sum of individual wallets.
- Metering test: simulate 15-minute lease; verify exact calculated credits deducted from borrower and credited to lender.
- Stripe webhook mock test: assert wallet balance increases upon valid webhook payload.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 9 completed"
git push origin main
```

---

### Phase 10: Production Hardening, Kubernetes & Global Orchestration

#### 1. Scope & Objective
Package the entire MiniDC platform for enterprise Kubernetes deployment with automated High-Availability (HA), OpenTelemetry observability, Prometheus/Grafana monitoring, and end-to-end continuous integration pipelines.

#### 2. Features Built
- [x] **Kubernetes Helm Charts (`deploy/helm/minidc`):**
  - Modular Helm chart packaging `minidc-server`, `minidc-ui`, PostgreSQL (StatefulSet with persistent volumes), Redis, Apache Kafka (KRaft), Apache Guacamole, and OpenTelemetry Collector.
  - Horizontal Pod Autoscaler (HPA) for `minidc-server` scaling on CPU (70%) and memory (80%).
  - Ingress controller configuration with automated Let's Encrypt TLS cert-manager and WebSocket upgrades.
  - Production-ready `values.yaml` with resource limits/requests, liveness/readiness/startup probes, Pod Disruption Budgets, Network Policies, and Security Contexts.
  - ServiceMonitors for Prometheus Operator and Grafana dashboard ConfigMaps.
- [x] **Full-Stack Observability Pipeline:**
  - Spring Boot Actuator with Micrometer Prometheus exporter at `/actuator/prometheus`.
  - Custom application metrics: `minidc_active_leases_total`, `minidc_connected_nodes_total`, `minidc_credit_burn_rate_total`, `minidc_vm_provisioning_duration_seconds`, `minidc_lease_state_total`, `minidc_node_cpu_usage_percent`, `minidc_node_memory_usage_percent`, `minidc_abuse_alerts_total`, `minidc_node_reliability_score`, `minidc_telemetry_events_total`, financial metrics (`minidc_total_credits_circulating`, `minidc_wallet_balance`, `minidc_wallet_transactions_total`, `minidc_lender_earnings_total`, `minidc_wallet_topup_total`).
  - Pre-built Grafana dashboards (`deploy/grafana/dashboards/`): Cluster Overview, Telemetry & Anomaly Radar, Financial Ledger.
  - OpenTelemetry distributed tracing via OTLP exporter to `otel-collector:4317` with Kafka trace propagation.
- [x] **GitHub Actions CI/CD Pipeline (`.github/workflows/ci.yml`):**
  - Stage 1 (Backend Quality): `mvn clean test` across all modules on Java 21 with embedded PostgreSQL & Redis.
  - Stage 2 (Frontend Quality): `npm ci`, `tsc --noEmit`, `npm run build` on Node 20.
  - Stage 3 (Container Packaging): Multi-stage Docker builds for `minidc-server` and `minidc-ui` (Nginx), publishing to GHCR with multi-arch support (linux/amd64, linux/arm64), SBOM generation (SPDX).
  - Stage 4 (Helm Verification): `helm lint` and `helm template` dry-run.
  - Stage 5 (Security Scan): Trivy HIGH/CRITICAL vulnerability scanning with SARIF upload to GitHub Security.
  - Stage 6 (Release): Automated changelog generation and release notes update on tag push.
- [x] **System Hardening & Chaos Resilience:**
  - Graceful shutdown handling in `MiniDcServerApplication` and `MiniDcAgentApplication` with `@PreDestroy` hooks (5s server / 3s agent).
  - Zero dropped sessions on server restart verified via Redis presence tracking.
  - Pod Disruption Budgets ensuring minimum availability during voluntary disruptions.
  - Network Policies restricting inter-component communication.

#### 3. Testing & Verification Criteria
- Helm lint and dry-run template installation with production values.
- 106/106 automated tests passing (including 10 new Phase 10 test suites).
- Complete clean end-to-end execution of GitHub Actions CI pipeline.

#### 4. Git Push Requirement
```bash
git add .
git commit -m "phase 10 completed"
git push origin main
```

---

## Master Verification Checklist & Project Completion Sign-Off

At the completion of all 10 phases, the MiniDC platform achieves **Full Production Enterprise Readiness (v2.0)**:

- [x] **Phase 1:** Core Foundation, Multi-Hypervisor & Database Baseline ✅ (22/22 tests)
- [x] **Phase 2:** Guacamole Token Cryptography & Streaming Pipeline ✅ (30/30 tests)
- [x] **Phase 3:** Redis Distributed Caching, Session & Rate Limiting ✅ (40/40 tests)
- [x] **Phase 4:** Kafka Event-Driven Messaging & Agent Resilience ✅ (51/51 tests)
- [x] **Phase 5:** Real-Time Frontend & Interactive Observability (STOMP) ✅ (53/53 tests)
- [x] **Phase 6:** Zero-Trust Network Isolation & WireGuard Egress ✅ (73/73 tests)
- [x] **Phase 7:** AI-Powered Matchmaking, Scheduling & Abuse Detection ✅ (84/84 tests)
- [x] **Phase 8:** Persistent Storage Volumes & State Snapshots ✅ (88/88 tests)
- [x] **Phase 9:** Marketplace Economics, Credits & Monetization ✅ (96/96 tests)
- [x] **Phase 10:** Production Hardening, Kubernetes & Global Orchestration ✅ (106/106 tests)

*Every phase must strictly follow the build, test, commit, and push protocol before graduating to the subsequent milestone.*

