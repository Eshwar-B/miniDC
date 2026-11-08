# MiniDC Platform: Master Architecture & Comprehensive Implementation Plan

> **Project Working Name:** MiniDC – Idle Laptop Sandbox Lease Platform  
> **Target Version:** 2.0 (Enterprise Distributed Cloud Sandbox & AI-Powered Edge Compute)  
> **Repository:** `https://github.com/Eshwar-B/miniDC.git`  
> **Operating Baseline:** Java 21 LTS | Spring Boot 3.3.5 | PostgreSQL 16 | React 18 + TypeScript | Apache Guacamole 1.5.5 | Kafka | Redis | AI/ML  

---

## Executive Summary & Phased Roadmap Overview

MiniDC transforms idle consumer hardware (laptops, desktops, mini PCs) into an on-demand, secure, multi-tenant sandbox compute marketplace. Borrowers lease isolated Ubuntu desktop environments streamed directly into their browser via Apache Guacamole, while lenders monetize or share their idle compute resources.

To scale MiniDC from its current **v1.2 baseline** to a distributed, fault-tolerant, AI-orchestrated cloud platform, the project is structured into **10 comprehensive, sequential phases**:

| Phase | Milestone Name | Core Technologies | Primary Objective |
|---|---|---|---|
| **Phase 1** | **Foundation & Multi-Hypervisor Baseline (COMPLETED)** | Java 21, Spring Boot 3.3, PostgreSQL 16, React 18, Libvirt, VirtualBox, Docker | Monorepo structure, multi-hypervisor driver contracts, JWT auth, lease lifecycle state machine, passing test suite. |
| **Phase 2** | **Guacamole Token Cryptography & Streaming Pipeline** | Apache Guacamole `auth-json`, AES-128-CBC, HMAC-SHA256, xrdp, WebSockets | Cryptographic token signing, direct Guacamole REST token dispatch, clipboard policy, and browser desktop integration. |
| **Phase 3** | **Distributed Caching, Session & Rate Limiting (Redis)** | Redis 7, Redisson, Spring Data Redis, Bucket4j | Node presence TTL heartbeats, distributed locking for lease allocation, token revocation blacklists, and API rate limiting. |
| **Phase 4** | **Event-Driven Messaging & Agent Resilience (Kafka)** | Apache Kafka / Redpanda, Spring Kafka, Virtual Threads | High-throughput telemetry streaming, audit log decoupling, and autonomous agent VM crash recovery / reconciliation. |
| **Phase 5** | **Real-Time Frontend & Interactive Observability** | STOMP, SockJS, React 18, Chart.js / Recharts | Eliminate polling; live marketplace updates, real-time lender approval notifications, and streaming borrower CPU/RAM charts. |
| **Phase 6** | **Zero-Trust Network Isolation & WireGuard Egress** | WireGuard, nftables, Windows Firewall, mTLS | Encrypted agent-to-server mTLS, lender LAN firewall drops, and centralized WireGuard exit gateway to protect lender IPs. |
| **Phase 7** | **AI-Powered Matchmaking, Scheduling & Abuse Detection** | Python / ONNX / Deeplearning4j, Isolation Forests | ML matchmaking based on hardware reliability, predictive uptime forecasting, and real-time cryptomining/anomaly detection. |
| **Phase 8** | **Persistent Storage Volumes & State Snapshots** | MinIO / AWS S3, qemu-img CoW, ZFS / Btrfs | Encrypted user home directory volume persistence across leases, state suspension, and seamless cross-node migration. |
| **Phase 9** | **Credit Economy, Monetization & Quota Management** | Stripe API, PostgreSQL, Spring Batch | Peer-to-peer compute credit tokenomics, automated billing meters, SLA penalty enforcement, and lender payout pipelines. |
| **Phase 10** | **Production Hardening, Kubernetes & Global Orchestration** | Kubernetes, Helm, OpenTelemetry, Prometheus, Grafana | High-availability server clustering, automated CI/CD GitHub Actions pipelines, distributed tracing, and chaos testing. |

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

### Phase 2: Guacamole Token Cryptography & Streaming Pipeline

#### 1. Scope & Objective
Transition from stub connect URLs to full cryptographic implementation of Apache Guacamole `auth-json` extension, providing seamless browser-based RDP access to the active sandbox without exposing passwords to the frontend.

#### 2. Features to Build
- [ ] **Guacamole Auth-JSON Cryptography Engine (`GuacCrypto`):**
  - Construct JSON connection configuration payload specifying protocol (`rdp`), host, port, credentials, and parameters (wallpaper off, display-update resize, clipboard rules).
  - Compute **HMAC-SHA256** signature using `minidc.guac-json-secret`.
  - Prefix 32-byte HMAC signature to plaintext JSON bytes.
  - Encrypt payload using **AES-128-CBC** with 16-byte zero initialization vector (IV) and PKCS5/PKCS7 padding.
  - Base64-encode the resulting ciphertext for transmission.
- [ ] **Guacamole Token Exchange Client:**
  - Issue HTTP POST request containing `data=<base64-ciphertext>` to `<guac-base-url>/api/tokens`.
  - Parse Guacamole authentication response token and construct launch URL: `<guac-base-url>/#/client/<connectionId>?token=<authToken>`.
  - Handle token expiration and refresh synchronization aligned with lease `ends_at`.
- [ ] **Enhanced Guacamole Web Client Integration in React UI:**
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

### Phase 3: Distributed Caching, Session & Rate Limiting (Redis)

#### 1. Scope & Objective
Introduce Redis as a high-performance in-memory state store to handle node heartbeats, distributed locking for concurrency, JWT token revocation, and API DDoS rate limiting.

#### 2. Features to Build
- [ ] **Redis Infrastructure & Spring Data Redis Setup:**
  - Add `spring-boot-starter-data-redis` and `redisson-spring-boot-starter` to `minidc-server`.
  - Configure Redis service in `deploy/docker-compose.yml` with authentication and persistence (AOF/RDB).
- [ ] **Real-Time Node Presence & Heartbeat TTL:**
  - Store node online state in Redis keys (`minidc:nodes:<nodeId>:heartbeat`) with 60-second TTL.
  - Configure Redis Keyspace Expiration Notifications (`__keyevent@*__:expired`) to immediately transition nodes to `OFFLINE` without scheduled database polling.
- [ ] **Redisson Distributed Locks for Capacity Allocation:**
  - Replace database row-level locking (`PESSIMISTIC_WRITE`) with distributed locks: `RLock lock = redissonClient.getLock("lock:offer:" + offerId)`.
  - Guarantees sub-millisecond lock acquisition across multiple clustered server instances, preventing offer double-booking.
- [ ] **JWT Blacklist & Session Revocation:**
  - Store logged-out or banned user tokens in Redis blacklist with TTL matching the remaining JWT lifespan.
  - Validate every incoming HTTP request via `JwtAuthFilter` against Redis blacklist cache in $O(1)$ time.
- [ ] **Distributed API Rate Limiting (Bucket4j-Redis):**
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

### Phase 4: Event-Driven Messaging & Agent Resilience (Kafka)

#### 1. Scope & Objective
Deploy Apache Kafka as the distributed event backbone to decouple high-volume telemetry streams, asynchronous agent command delivery, audit trail logging, and automatic crash recovery for edge agents.

#### 2. Features to Build
- [ ] **Kafka Cluster & Event Schema Configuration:**
  - Add Apache Kafka (or Redpanda) service to `deploy/docker-compose.yml`.
  - Add `spring-kafka` to `minidc-server` and `minidc-agent`.
  - Define structured Avro / JSON Schema event topics:
    - `minidc.telemetry.metrics`: host CPU, RAM, disk, network throughput from agents.
    - `minidc.lease.lifecycle`: `LeaseRequested`, `LeaseApproved`, `VmProvisioned`, `LeaseTerminated`.
    - `minidc.audit.events`: administrative and security audit events.
    - `minidc.agent.commands`: asynchronous command queue for agents.
- [ ] **Asynchronous Telemetry Ingestion Pipeline:**
  - Agent streams 5-second host and VM resource utilization to `minidc.telemetry.metrics`.
  - Kafka consumer group aggregates metrics into time-series buckets without placing transactional load on PostgreSQL.
- [ ] **Agent Reconnection & Hypervisor Reconciliation Engine:**
  - Implement autonomous hypervisor scanner in `minidc-agent`:
    - On agent boot/reconnect, query underlying hypervisor (Libvirt `virsh list`, VirtualBox `VBoxManage list runningvms`, or Docker `docker ps --filter label=minidc.lease`).
    - Publish reconciliation report (`minidc.agent.reconciliation`) comparing actual running sandboxes against expected leases.
    - Safely destroy orphan/zombie VMs left behind from sudden power loss or network disconnection.
- [ ] **Dead Letter Queue (DLQ) & Retry Policy:**
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

### Phase 5: Real-Time Frontend & Interactive Observability

#### 1. Scope & Objective
Eliminate inefficient client-side polling in the React UI by implementing STOMP over WebSocket, enabling live marketplace synchronization, instant lender notifications, and real-time VM resource visualization.

#### 2. Features to Build
- [ ] **Spring WebSocket STOMP Message Broker:**
  - Configure Spring WebSocket message broker with `/topic` and `/queue` prefixes backed by Redis / Kafka relay.
  - Secure STOMP connections using JWT token authentication during the handshake phase.
  - User-specific private queues: `/user/queue/notifications`.
- [ ] **Real-Time Marketplace Updates (`minidc-ui`):**
  - Subscribe to `/topic/offers`: live badge updates as offers are created, updated, or exhausted.
  - Instant offer availability changes without page refreshes.
- [ ] **Live Lender Lease Approval Hub:**
  - Push incoming lease requests to `/user/{lenderId}/queue/leases` with desktop browser notification.
  - Lender one-click Approve / Reject action with optimistic UI state update.
- [ ] **Interactive Real-Time Borrower Dashboard:**
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

### Phase 6: Zero-Trust Network Isolation & WireGuard Egress

#### 1. Scope & Objective
Harden edge network security by isolating sandbox VMs from the lender's local area network (LAN) and routing guest internet traffic through a centralized WireGuard egress tunnel so the lender's personal public IP is never exposed to the internet.

#### 2. Features to Build
- [ ] **Mutual TLS (mTLS) Edge Channel:**
  - Enforce mTLS for all agent-to-server WebSocket and REST communications.
  - Automated certificate provisioning: server acts as internal Certificate Authority (CA) issuing short-lived client certificates upon node registration.
- [ ] **Hardened Local Firewall Rules (Linux & Windows):**
  - **Linux (`nftables`):** Drop all guest traffic destined for private subnet ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`, `169.254.0.0/16`) and block outbound email ports (25, 465, 587) and standard scanning ports.
  - **Windows (`netsh advfirewall`):** Automated outbound packet filters applied to the VirtualBox Host-Only / NAT adapter.
- [ ] **Centralized WireGuard Egress Tunnel:**
  - Deploy a WireGuard exit node container in `deploy/docker-compose.yml`.
  - Automatically configure a WireGuard network interface (`wg0`) inside the guest VM or container sandbox during cloud-init / container startup.
  - Force all guest non-RDP outbound traffic through the WireGuard tunnel, ensuring borrower web browsing exits via the platform IP rather than the lender's residential ISP.
- [ ] **Bandwidth Shaping & Traffic Throttling:**
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

### Phase 7: AI-Powered Matchmaking, Scheduling & Abuse Detection

#### 1. Scope & Objective
Embed Artificial Intelligence and Machine Learning models to optimize node allocation, predict lender availability patterns, detect malicious sandbox abuse (cryptomining, port scanning) in real time, and enable natural language lease requests.

#### 2. Features to Build
- [ ] **AI Workload Matchmaker & Recommendation Engine:**
  - Feature Vector: required CPU cores, RAM, disk, geographical proximity, lender historical reliability index (uptime score), and network latency.
  - Scoring Model: Multi-criteria utility ranking algorithm to match borrower requirements with the most optimal, cost-efficient, and reliable node.
- [ ] **Predictive Availability & Churn Forecasting:**
  - Train a time-series model (e.g., Random Forest / LSTM or heuristic regression) on lender telemetry history.
  - Predict likelihood of a laptop being unplugged or closed during the requested lease window; flag risky nodes and prevent premature lease terminations.
- [ ] **Real-Time Cryptomining & Anomaly Detection (AI Guard):**
  - Stream hardware metrics (constant 100% multi-core CPU usage, high GPU compute usage, repetitive outbound network handshakes) into an Isolation Forest anomaly detection pipeline.
  - Automatically trigger `SUSPICIOUS_ACTIVITY` alert and safely auto-suspend or terminate leases engaging in cryptomining, torrenting, or DDoS bot activity.
- [ ] **Natural Language Lease Assistant (LLM Integration):**
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
- [ ] **Encrypted User Home Volumes:**
  - Attach an independent virtual disk or Docker volume for `/home/borrower` encrypted with LUKS / AES per user.
  - On lease termination, unmount and synchronize the encrypted volume snapshot to S3-compatible object storage (MinIO or AWS S3).
  - On subsequent lease creation by the same user, automatically stream and mount their encrypted persistent volume into the new sandbox.
- [ ] **Sandbox Suspension & Snapshots:**
  - Implement `pauseLease` and `resumeLease`:
    - Libvirt: `virsh managedsave` / `virsh restore`.
    - VirtualBox: `VBoxManage controlvm savestate`.
    - Docker: `docker commit` snapshot + memory state checkpoint.
- [ ] **Cross-Node Sandbox Migration:**
  - Transfer saved state images between lender nodes via encrypted delta sync (rsync over SSH or S3 storage), allowing a borrower to seamlessly switch hosts if a lender needs to take their machine offline.

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
- [ ] **MiniDC Credit Wallet System:**
  - Database schema: `wallets`, `wallet_transactions`, `billing_rates`.
  - Double-entry ledger architecture for zero-sum credit auditing.
  - Lenders earn credits for every minute their active sandboxes are utilized:
    $$\text{Rate} = (\text{Cores} \times R_{\text{cpu}}) + (\text{RAM}_{\text{GB}} \times R_{\text{ram}}) + (\text{Disk}_{\text{GB}} \times R_{\text{disk}})$$
- [ ] **Dynamic Pricing & Metering Scheduler:**
  - Spring Batch job running every minute to deduct credits from borrower wallets and credit lender wallets.
  - Automatic graceful shutdown if a borrower's wallet balance reaches zero.
- [ ] **Payment Gateway Integration (Stripe):**
  - Top up wallet balances via Stripe Checkout / PaymentIntents API.
  - Webhook listener for `payment_intent.succeeded` granting instant credits.
- [ ] **SLA Penalty & Quality of Service (QoS) Guarantees:**
  - If a lender manually kills an active lease or their node drops offline unexpectedly, deduct a penalty credit and refund the borrower with a bonus credit voucher.

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

#### 2. Features to Build
- [ ] **Kubernetes Helm Charts (`deploy/helm`):**
  - StatefulSet for PostgreSQL cluster with Patroni / CloudNative-PG.
  - Clustered Kafka (Strimzi) and Redis Sentinel deployments.
  - Horizontal Pod Autoscaler (HPA) for `minidc-server` and `guacamole` web tiers.
  - Ingress controller configuration with automated Let's Encrypt TLS cert-manager.
- [ ] **Full-Stack Observability Pipeline:**
  - Export OpenTelemetry distributed traces (`minidc-server` → `kafka` → `guacamole`).
  - Prometheus metrics exporter scraping JVM metrics, lease state counters, and agent connection numbers.
  - Pre-built Grafana dashboards (`deploy/grafana`): Global Sandbox Map, Node Uptime Matrix, Financial Revenue, and Telemetry Heatmap.
- [ ] **GitHub Actions Continuous Integration & Delivery (CI/CD):**
  - Workflow `.github/workflows/ci.yml`:
    - Linting and security scanning (SonarQube, Trivy container scan).
    - Multi-module Maven test execution with PostgreSQL & Kafka testcontainers.
    - React UI lint and production build verification.
    - Automated multi-arch Docker image build (`linux/amd64`, `linux/arm64`) and publish to GitHub Container Registry (GHCR).
- [ ] **Disaster Recovery & Chaos Testing:**
  - Automated database backup scripts to S3 with point-in-time recovery (PITR).
  - Chaos test suite: randomly terminate server pods and Redis nodes; verify zero active leases are terminated and client reconnections succeed.

#### 3. Testing & Verification Criteria
- Helm lint and dry-run template installation on a local minikube / k3s cluster.
- Chaos engineering test asserting zero drop in active Guacamole streams during server rolling update.
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

- [ ] **Phase 1:** Core Foundation, Multi-Hypervisor & Database Baseline ✅
- [ ] **Phase 2:** Guacamole Token Cryptography & Streaming Pipeline
- [ ] **Phase 3:** Redis Distributed Caching, Session & Rate Limiting
- [ ] **Phase 4:** Kafka Event-Driven Messaging & Agent Resilience
- [ ] **Phase 5:** Real-Time Frontend & Interactive Observability (STOMP)
- [ ] **Phase 6:** Zero-Trust Network Isolation & WireGuard Egress
- [ ] **Phase 7:** AI-Powered Matchmaking, Scheduling & Abuse Detection
- [ ] **Phase 8:** Persistent Storage Volumes & State Snapshots
- [ ] **Phase 9:** Marketplace Economics, Credits & Monetization
- [ ] **Phase 10:** Production Hardening, Kubernetes & Global Orchestration

*Every phase must strictly follow the build, test, commit, and push protocol before graduating to the subsequent milestone.*

