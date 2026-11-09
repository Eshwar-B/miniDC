# MiniDC: Project Context, Architecture & Live Phase Status

> **Document Purpose:** Central status tracker for the MiniDC platform. Maintains end-to-end project context, architectural decisions ("why this feature"), implementation status, verification metrics, and immediate next milestones.
> 
> 📖 **For the full user manual, feature guide, and setup instructions for all roles → see [USAGE.md](./USAGE.md)**

---

## 1. Project Context & Purpose

### 1.1 The Core Problem
Many developers, researchers, and students require temporary compute (more RAM, multiple CPU cores, dedicated disk) for tasks like compilation, simulations, or clean sandbox testing. Concurrently, millions of laptops and workstations sit idle in offices, university labs, and homes. 

*Why not remote RAM-sharing?* Sharing raw memory across a network introduces millisecond latencies, rendering computation orders of magnitude slower than local execution. 

### 1.2 The MiniDC Solution
MiniDC turns idle consumer and lab hardware into mini edge datacenters:
1. **Lenders** run a lightweight agent daemon and advertise idle capacity (RAM, CPU cores, disk, schedule window, auto-approve policies).
2. **Borrowers** browse available capacity on a web marketplace and request an on-demand lease.
3. **The Node Agent** boots a fresh, pristine Ubuntu desktop sandbox (via native Linux KVM/libvirt, Windows 10/11 VirtualBox, or lightweight Docker desktop container).
4. **Interactive Browser Desktop:** The borrower interacts with the full XFCE desktop in their browser through **Apache Guacamole** (streaming RDP to xrdp inside the guest sandbox).
5. **Ephemerality & Security:** When the lease expires or is terminated, the VM and its storage are completely destroyed, wiping all data.

---

## 2. Master Phase Tracker

MiniDC is divided into **10 structured phases** to evolve from MVP baseline to an enterprise-grade, distributed, AI-orchestrated cloud compute platform:

| Phase | Milestone Name | Status | Verified Tests | Git Commit & Push Status |
|---|---|---|---|---|
| **Phase 1** | **Foundation & Multi-Hypervisor Baseline** | **COMPLETED** ✅ | 22 / 22 Passed | Committed & pushed as `phase 1 completed` |
| **Phase 2** | **Guacamole Token Cryptography & Streaming Pipeline** | **COMPLETED** ✅ | 30 / 30 Passed | Committed & pushed as `phase 2 completed` |
| **Phase 3** | **Distributed Caching, Session & Rate Limiting (Redis)** | **COMPLETED** ✅ | 40 / 40 Passed | Committed & pushed as `phase 3 completed` |
| **Phase 4** | **Event-Driven Messaging & Agent Resilience (Kafka)** | **COMPLETED** ✅ | 51 / 51 Passed | Committed & pushed as `phase 4 completed` |
| **Phase 5** | **Real-Time Frontend & Interactive Observability** | **COMPLETED** ✅ | 53 / 53 Passed | Committed & pushed as `phase 5 completed` |
| **Phase 6** | **Zero-Trust Network Isolation & WireGuard Egress** | **NEXT UP** ⏳ | Pending | Pending |
| **Phase 7** | **AI Matchmaking, Scheduling & Abuse Detection** | **PLANNED** ⏳ | Pending | Pending |
| **Phase 8** | **Persistent Storage Volumes & State Snapshots** | **PLANNED** ⏳ | Pending | Pending |
| **Phase 9** | **Marketplace Economics, Credits & Monetization** | **PLANNED** ⏳ | Pending | Pending |
| **Phase 10** | **Production Hardening, Kubernetes & Global Orchestration** | **PLANNED** ⏳ | Pending | Pending |

---

## 3. What Has Been Completed (Phase 1) & Architectural Rationale

### 3.1 Subsystems Completed in Phase 1
* **Multi-Module Monorepo Setup:** Parent POM configuring Java 21 LTS, Spring Boot 3.3.5, and submodules (`minidc-common`, `minidc-server`, `minidc-agent`, `minidc-ui`).
* **Relational Persistence Layer:** PostgreSQL 16 schema with transactional Flyway migrations (`V1__init.sql`). Support for both Docker Compose PostgreSQL and an embedded PostgreSQL profile (`dev,embedded`) for zero-dependency local setup.
* **Authentication & Role-Based Access Control:** Secure JWT authentication, BCrypt password hashing, role-based authorization (`ADMIN`, `LENDER`, `BORROWER`), and invite-only code generation.
* **Lease State Machine:** Comprehensive state transition rules (`REQUESTED` → `PENDING_PROVISION` → `PROVISIONING` → `ACTIVE` → `TERMINATING` → `TERMINATED` / `FAILED` / `REJECTED`).
* **Multi-Hypervisor Edge Drivers:**
  * `LibvirtDriver`: Fast native KVM/QEMU virtualization on Linux with cloud-init and copy-on-write `qcow2` overlays.
  * `VirtualBoxDriver`: Headless `VBoxManage` integration with NAT port forwarding on Windows 10/11.
  * `DockerDriver`: Fast container desktop (`minidc-desktop:latest`) for rapid single-machine demos.
  * Dry-run simulation mode on all drivers for testing on systems without hypervisors.
* **React 18 SPA:** Full web portal with Vite, TypeScript, route protection, Marketplace, Lender Hub, My Leases, and Admin Audit page.

### 3.2 "Why This Feature?" (Architectural Decisions in Phase 1)
| Feature | Why It Exists / Problem Solved |
|---|---|
| **Multi-Hypervisor Abstraction** | Lenders have varied OS environments (Linux laptops with KVM, Windows PCs with VirtualBox, demo laptops with Docker). A unified `HypervisorDriver` contract decouples the agent logic from the virtualization engine. |
| **Pessimistic Locking on Offers** | In a shared marketplace, two borrowers requesting a single available slot at the same millisecond causes double-booking. `@Lock(LockModeType.PESSIMISTIC_WRITE)` guarantees atomic reservation. |
| **AES-256-GCM VM Password Encryption** | Each guest sandbox gets a distinct randomly generated root/user password. Storing plaintext passwords in the database is a critical vulnerability; AES-GCM provides authenticated encryption at rest. |
| **Outbound WebSocket Agent Connection** | Most lenders sit behind residential NAT routers or corporate firewalls without public IP addresses or port forwarding. The agent opens an *outbound* persistent WebSocket to the server, bypassing NAT issues completely. |
| **Zero Lombok** | Strict reliance on native Java 21 records and explicit getters avoids annotation-processing quirks and ensures transparent maintenance across diverse IDEs and build pipelines. |

---

## 4. Completed: Phase 2 (Guacamole Token Cryptography & Streaming Pipeline)

### 4.1 Objectives & Features Completed
* **HMAC-SHA256 & AES-128-CBC Cryptography Engine (`GuacCrypto`):** 
  * Implemented HMAC-SHA256 signature computation and AES-128-CBC zero-IV encryption matching Apache Guacamole `auth-json` specifications.
  * Robust key parser supporting 32-character hex strings, 64-character hex strings, and arbitrary UTF-8 fallback secrets.
  * Strict tampering verification throwing `SecurityException` upon signature mismatch or corrupted ciphertext.
* **Direct Token Dispatch & Fallback (`GuacService`):**
  * HTTP client exchanges signed data payload with Guacamole's `/api/tokens` endpoint.
  * Automatic graceful fallback to signed token launch URL when Guacamole container is offline or during offline testing.
* **Enhanced Frontend Guacamole Integration:**
  * Updated API response models to carry `token` and `connectionId`.
  * Desktop session launch notifications with tips on accessing the Guacamole clipboard menu (`Ctrl+Alt+Shift`).
* **Test Verification:**
  * 8 new tests added (5 unit tests in `GuacCryptoTest`, 3 unit/integration tests in `GuacServiceTest`), bringing the automated test suite to 30 passing tests.

---

## 5. Completed: Phase 3 (Distributed Caching, Session & Rate Limiting with Redis)

### 5.1 Objectives & Features Completed
* **Redis State Management & Fallback Layer (`RedisStateService`):**
  * Spring Data Redis integration (`StringRedisTemplate`) with transparent thread-safe in-memory fallback for local dev & testing.
  * Node presence tracking with TTL (`minidc:node:presence:<nodeId>`) for instantaneous $O(1)$ online/offline detection without database polling.
  * Atomic sliding-window counters for rate limiting and `setIfAbsent` atomic primitives for locks.
* **Distributed Locking Engine (`DistributedLockService`):**
  * Distributed mutex locks on offer capacity allocation (`minidc:lock:offer:<offerId>`) to prevent concurrent double-booking across clustered control servers.
  * Integrated seamlessly into `LeaseService.createLease` with safe fallback for backward compatibility.
* **Token Blacklist & Session Revocation (`TokenBlacklistService`):**
  * High-speed $O(1)$ SHA-256 hashed lookup for revoked JWT tokens on user logout or account suspension.
  * Integrated into `JwtAuthFilter` to reject blacklisted tokens before request processing.
  * Added `/api/auth/logout` endpoint in `AuthController` and hooked into frontend `AuthContext`.
* **Distributed Rate Limiting Filter (`RateLimiterFilter`):**
  * Sliding-window token-bucket rate limiting defending critical endpoints (`/api/auth/login`, `/api/auth/register`, `/api/leases`) against brute force and DDoS attacks.
  * Emits standard HTTP 429 Too Many Requests response with `Retry-After: 60` and `X-RateLimit-*` headers.
* **Container Infrastructure:**
  * Added Redis 7 Alpine service with persistence and healthcheck to `deploy/docker-compose.yml`.
* **Test Verification:**
  * Added 10 new automated tests (`RedisStateServiceTest`, `DistributedLockServiceTest`, `TokenBlacklistServiceTest`, `RateLimiterFilterTest`), bringing the automated test suite to 40 passing tests.

---

## 6. Completed: Phase 4 (Event-Driven Messaging & Agent Resilience with Kafka)

### 6.1 Objectives & Features Completed
* **Kafka Event Backbone & Schema Models (`minidc-common/event`):**
  * Defined canonical Kafka topics: `minidc.telemetry.metrics`, `minidc.lease.lifecycle`, `minidc.audit.events`, `minidc.agent.reconciliation`, and `minidc.dlq`.
  * Created structured event models: `TelemetryMetricEvent`, `LeaseLifecycleEvent`, `AuditEventMessage`, and `ReconciliationReport`.
* **Resilient Event Publisher & In-Memory Fallback (`KafkaEventPublisher`):**
  * Spring Kafka integration using `KafkaTemplate<String, String>` with transparent, thread-safe in-memory subscriber dispatch.
  * Allows offline local development and tests to execute with zero external broker dependencies while scaling seamlessly to Kafka in Docker/Kubernetes.
  * Built-in Dead Letter Queue (DLQ) envelope routing for unparseable or failed messages.
* **Asynchronous Telemetry Ingestion Pipeline (`TelemetryService`, `TelemetryConsumer`, `TelemetryController`):**
  * Consumer groups process high-frequency CPU, RAM, and disk utilization streams into a bounded 120-point sliding window ring buffer (10 minutes of 5-second metrics) without touching PostgreSQL.
  * REST endpoints: `GET /api/telemetry/leases/{leaseId}`, `GET /api/telemetry/leases/{leaseId}/latest`, `GET /api/telemetry/nodes/{nodeId}`, and `POST /api/telemetry/ingest`.
* **Autonomous Hypervisor Reconciliation Engine (`HypervisorReconciler`, `AgentReconciliationService`):**
  * Edge agents inspect the underlying hypervisor (Libvirt, VirtualBox, or Docker) on boot and reconnect.
  * Compares running sandbox containers/domains against expected active leases.
  * Automatically isolates and terminates zombie/orphan VMs left behind by abrupt power outages or crashes.
  * Emits `ReconciliationReport` back to the server and creates audit log records.
* **Docker Compose Multi-Broker Infrastructure:**
  * Added Apache Kafka 3.7.0 service in KRaft mode to `deploy/docker-compose.yml`.
* **Test Verification:**
  * Added 11 new automated tests (`KafkaEventPublisherTest`, `TelemetryServiceTest`, `AgentReconciliationServiceTest`, `HypervisorReconcilerTest`), bringing the automated test suite to 51 passing tests with 0 failures.

---

## 7. Completed: Phase 5 (Real-Time Frontend & Interactive Observability)

### 7.1 Objectives & Features Completed
* **STOMP WebSocket Broker (`StompWebSocketConfig`):**
  * Configured bi-directional message broker over `/ws/stomp` with SockJS fallback and CORS support.
  * Defined prefix conventions: `/topic` (public broadcast), `/queue` and `/user` (point-to-point private notifications).
* **JWT Authenticated STOMP Interceptor (`StompAuthChannelInterceptor`):**
  * Authenticates incoming STOMP `CONNECT` frames via Bearer token in headers (`Authorization` or `token`).
  * Injects authenticated `CurrentUser` into Spring Security context with seamless fallback for immutable header wrappers.
* **Real-Time Notification Dispatcher (`StompNotificationService`):**
  * `/topic/offers`: Instant marketplace updates when offers are published, updated, or removed (zero-polling browsing).
  * `/topic/leases/incoming`: Real-time push to lenders when manual-approval lease requests arrive.
  * `/user/{userId}/queue/leases`: Targeted lifecycle updates to borrowers and lenders upon lease approval, provisioning, or termination.
  * `/topic/leases/{leaseId}/telemetry`: High-frequency push of live CPU load percentage and RAM usage.
* **Frontend Real-Time Architecture (`minidc-ui`):**
  * Integrated `@stomp/stompjs` with resilient auto-reconnect and token injection (`stompClient.ts`).
  * `Marketplace`: Replaced HTTP polling intervals with live `/topic/offers` subscriptions.
  * `LenderDashboard`: Instant notifications on pending lease requests via `/topic/leases/incoming`.
  * `MyLeases`: Live lease state transitions without manual page reloads.
  * `LeaseCard`: Live animated telemetry gauges showing real-time CPU utilization (%) and RAM consumption.
  * Updated `vite.config.ts` to reverse-proxy `/ws` WebSocket connections to backend port 8080.
* **Test Verification:**
  * Added automated tests for STOMP notifications and interceptor token verification (`StompNotificationServiceTest`, `StompAuthChannelInterceptorTest`), bringing total passing tests to 53.
  * Frontend compilation verified: 65 modules transformed cleanly with zero TypeScript errors.

---

## 8. Upcoming Phases: High-Level Roadmap & "Why" Rationale

* **Phase 6 (Zero-Trust Network Isolation & WireGuard Egress):**
  * *Why:* Protect lender residential IPs from guest abuse or DMCA notices by funneling all guest internet traffic through a centralized WireGuard exit gateway, and dropping local LAN access.
* **Phase 7 (AI Matchmaking & Anomaly Detection):**
  * *Why:* Match workloads based on predicted node reliability and automatically shut down rogue sandboxes attempting cryptomining or port scanning.
* **Phase 8 (Persistent Storage Volumes):**
  * *Why:* Allow borrowers to retain their `/home` directory across leases through encrypted snapshot syncing to S3/MinIO.
* **Phase 9 (Marketplace Economics & Billing):**
  * *Why:* Enable fair compute credit exchange between lenders and borrowers with automated per-minute billing and Stripe payouts.
* **Phase 10 (Kubernetes & Enterprise Hardening):**
  * *Why:* Deliver full high-availability clustering, Helm deployments, OpenTelemetry tracing, and automated GitHub Actions CI/CD pipelines.

---

## 9. Verification & Test Metrics Log

* **Phase 1 Tests:** 22 passing tests across parent, server, and agent.
* **Phase 2 Tests:** 30 passing tests (including Guac crypto and REST token service).
* **Phase 3 Tests:** 40 passing tests (including Redis state presence, rate limiting filter, token blacklisting, and distributed locks).
* **Phase 4 Tests:** 51 passing tests (including Kafka event bus, telemetry sliding-window buffer, and autonomous hypervisor reconciler).
* **Phase 5 Tests:** 53 passing tests (including STOMP WebSocket notification dispatcher and JWT channel security interceptor).
* **Live End-to-End API Suite (`test-live-api.ps1`):** 10 / 10 live integration tests passing on running server:
  1. Actuator Health (`/actuator/health`) -> `UP`
  2. Admin Authentication (`/api/auth/login`) -> `200 OK + JWT`
  3. Single-Use Invite Code (`/api/admin/invites`) -> `200 OK + INV-code`
  4. User Registration (`/api/auth/register`) -> `200 OK + JWT`
  5. Authenticated Profile Context (`/api/me`) -> `200 OK`
  6. Edge Node Registration (`/api/nodes`) -> `201 Created + Daemon Token`
  7. Marketplace Offer Publishing (`/api/offers`) -> `201 Created`
  8. Marketplace Browsing & Node Status (`/api/offers/available`, `/api/nodes/mine`) -> `200 OK`
  9. Redis Rate Limiter Brute-Force Defense -> `429 Too Many Requests (Blocked by Redis Filter)`
  10. JWT Token Blacklist & Session Revocation (`/api/auth/logout`) -> `204 No Content`, subsequent calls rejected with `403 Forbidden`
* **Frontend:** 65 modules cleanly compiled into production bundle (`dist/`).
* **Current Git Branch:** `main` (Remote: `https://github.com/Eshwar-B/miniDC.git`)
