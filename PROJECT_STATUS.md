# MiniDC: Project Context, Architecture & Live Phase Status

> **Document Purpose:** Central status tracker for the MiniDC platform. Maintains end-to-end project context, architectural decisions ("why this feature"), implementation status, verification metrics, and immediate next milestones.

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
| **Phase 3** | **Distributed Caching, Session & Rate Limiting (Redis)** | **NEXT UP** ⏳ | Pending | Pending |
| **Phase 4** | **Event-Driven Messaging & Agent Resilience (Kafka)** | **PLANNED** ⏳ | Pending | Pending |
| **Phase 5** | **Real-Time Frontend & Interactive Observability** | **PLANNED** ⏳ | Pending | Pending |
| **Phase 6** | **Zero-Trust Network Isolation & WireGuard Egress** | **PLANNED** ⏳ | Pending | Pending |
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

## 5. Upcoming Phases: High-Level Roadmap & "Why" Rationale

* **Phase 3 (Redis Caching & Rate Limiting):**
  * *Why:* Eliminate database bottleneck for ephemeral node heartbeats (using Redis key TTL expiration for instant offline detection), add distributed locking via Redisson, and protect APIs against brute force via token-bucket rate limiting.
* **Phase 4 (Apache Kafka Event Streaming & Agent Resilience):**
  * *Why:* Stream high-frequency CPU/RAM telemetry from dozens of edge nodes without locking PostgreSQL. Implement autonomous reconciliation on agent restart to clean up zombie VMs from power outages.
* **Phase 5 (Real-Time Frontend via STOMP):**
  * *Why:* Replace client polling with bi-directional WebSocket push so marketplace offers and incoming lease approval requests appear instantly.
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

## 6. Verification & Test Metrics Log

* **Phase 1 Tests:** 22 passing tests across parent, server, and agent.
* **Phase 1 Frontend:** 45 modules cleanly compiled into production bundle (`dist/`).
* **Current Git Branch:** `main` (Remote: `https://github.com/Eshwar-B/miniDC.git`)
