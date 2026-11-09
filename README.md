# MiniDC – Idle Laptop Sandbox Lease Platform

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-green.svg)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)]()
[![React](https://img.shields.io/badge/React-18%20%7C%20Vite%20%7C%20TS-61dafb.svg)]()
[![Hypervisors](https://img.shields.io/badge/Hypervisors-KVM%20%7C%20VirtualBox%20%7C%20Docker-blueviolet.svg)]()
[![Tests](https://img.shields.io/badge/Tests-53%20passing-brightgreen.svg)]()

MiniDC turns idle Linux and Windows laptops and PCs into mini datacenters:
- A **lender** installs an **agent** and advertises resource offers (RAM, CPU cores, disk, daily availability window, manual or auto-approve).
- A **borrower** browses available offers on a web marketplace and requests a **lease**.
- When approved, the agent provisions an isolated, clean Ubuntu desktop sandbox (via **KVM/libvirt** on Linux, **VirtualBox** on Windows, or **Docker** container desktop for demos).
- The borrower accesses the full interactive desktop directly in a web browser using **Apache Guacamole** (streaming RDP to xrdp inside the sandbox).
- At lease expiration (or when manually ended/killed), the sandbox and all associated storage are destroyed.

> 📖 **For complete setup, feature details, and user guides → see [USAGE.md](./USAGE.md)**

---

## 1. Current Project Status

- **Version:** `1.5` — Phases 1–5 complete (Foundation + Guacamole Crypto + Redis Caching & Rate Limiting + Kafka Event Streaming + Real-Time STOMP WebSockets & Observability).
- **Core Architecture:** Complete multi-module Maven monorepo + React TypeScript SPA.
- **Test Suite Status:** **53/53 unit tests passing** (`mvn test` runs in ~16s with 0 errors, 0 failures).
- **Frontend Status:** Clean compilation (`npm run build` bundles 65 modules cleanly into static assets).
- **Live API Tests:** 10/10 end-to-end integration scenarios verified against running server.
- **Admin Credentials (dev):** `admin@minidc.local` / `admin12345`
- **Driver Support:**
  - `LibvirtDriver`: Fast native KVM/QEMU virtualization on Linux.
  - `VirtualBoxDriver`: Headless VirtualBox 7.x management with NAT port forwarding on Windows 10/11.
  - `DockerDriver`: Fast lightweight container desktop (`minidc-desktop:latest`) for rapid single-machine demos and testing.
- **Database:** PostgreSQL 16 with Flyway transactional migrations, UTC timezone enforcement, and support for:
  - Docker Compose service (`postgres:16`).
  - Embedded PostgreSQL (`io.zonky.test:embedded-postgres`) for zero-dependency local development via `--spring.profiles.active=dev,embedded`.
  - Testcontainers for integration testing.
- **Redis:** Sliding-window rate limiting, JWT token blacklisting, distributed locks, and node presence tracking with TTL.
- **Kafka & Event Backbone:** Apache Kafka KRaft mode event streaming, high-throughput telemetry ring buffer, and autonomous hypervisor reconciler.
- **Real-Time WebSockets (STOMP):** Bi-directional STOMP WebSocket push (`/ws/stomp`) with JWT channel authentication, zero-polling marketplace updates, instant lease approval dispatches, and live CPU/RAM telemetry gauges on lease cards.

> 📖 **Full documentation: [USAGE.md](./USAGE.md)** | Phase tracker: [PROJECT_STATUS.md](./PROJECT_STATUS.md)

---

## 2. Directory Structure

```
minidc/
  pom.xml                                  # Maven parent POM (Java 21, Spring Boot 3.3.5 BOM)
  README.md                                # Project overview and quick start guide
  MiniDC – Sandbox Lease Platform Complete Build Documentation.md # Complete v1.2 specification
  .gitignore
  .editorconfig
  minidc-common/                           # Shared DTOs, domain models, enums, WebSocket payloads
    pom.xml
    src/main/java/com/minidc/common/
      enums/ (LeaseState, NodeStatus, Role)
      model/ (VmSpec, VmInfo, HostInfo)
      ws/ (MessageType, WsMessage)
      ws/payload/ (Register, Heartbeat, CreateVm, VmReady, VmFailed, VmDestroyed, VmStats, DestroyVm)
  minidc-server/                           # Spring Boot control server application
    pom.xml
    Dockerfile                             # Multi-stage build (UI build + Maven package + JRE runtime)
    src/main/java/com/minidc/server/
      MiniDcServerApplication.java
      config/ (AppProperties, WebSocketConfig, SchedulingConfig, EmbeddedPgConfig)
      security/ (SecurityConfig, JwtService, JwtAuthFilter, CurrentUser)
      auth/ (AuthController, AuthService, DTOs)
      user/ (User, UserRepository, Invite, InviteRepository)
      node/ (Node, NodeRepository, NodeController, NodeService, DTOs)
      offer/ (Offer, OfferRepository, OfferController, OfferService, DTOs)
      lease/ (Lease, LeaseRepository, LeaseController, LeaseService, DTOs, Exceptions)
      ws/ (AgentSocketHandler, AgentSessionRegistry, AgentCommandSender)
      guac/ (GuacService, GuacController)
      audit/ (AuditLog, AuditLogRepository, AuditService)
      admin/ (AdminController)
      scheduler/ (LeaseScheduler)
      crypto/ (AesGcmCrypto)
      error/ (ApiError, GlobalExceptionHandler)
    src/main/resources/
      application.yml                      # Base configuration (UTC time zone, validate schema)
      application-dev.yml                  # Dev profile with admin seed CommandLineRunner
      db/migration/V1__init.sql            # PostgreSQL schema migrations
    src/test/java/com/minidc/server/
      lease/ (LeaseServiceTest, LeaseStateMachineTest)
      auth/ (AuthControllerIT)
      support/ (PostgresTestContainer)
  minidc-agent/                            # Background node agent daemon (Spring Boot non-web)
    pom.xml
    Dockerfile                             # Multi-stage agent container for Docker demo mode
    src/main/java/com/minidc/agent/
      MiniDcAgentApplication.java
      config/ (AgentProperties)
      cmd/ (Shell, ShellResult)
      system/ (HostStats, LimitChecker, OsDetector)
      vm/ (HypervisorDriver, LibvirtDriver, VirtualBoxDriver, DockerDriver, HypervisorConfig, VmManager, CloudInitBuilder)
      net/ (TcpForwarder, EgressRules)
      ws/ (ServerConnection, CommandDispatcher, HeartbeatJob)
    src/main/resources/
      application.yml
    src/test/java/com/minidc/agent/
      cmd/ (ShellTest)
      system/ (LimitCheckerTest)
      vm/ (CloudInitBuilderTest, DockerDriverTest)
  minidc-ui/                               # React 18 + Vite + TypeScript web application
    package.json
    tsconfig.json
    vite.config.ts
    index.html
    src/
      main.tsx
      App.tsx                              # Routes, ProtectedRoute, Navigation
      api/ (client.ts, types.ts)
      auth/ (AuthContext.tsx)
      pages/ (LoginPage, RegisterPage, LenderDashboard, Marketplace, MyLeases, AdminPage)
      components/ (LeaseCard, OfferForm, Countdown)
      styles.css
  deploy/                                  # Deployment configurations and scripts
    docker-compose.yml                     # Full stack (Postgres, guacd, Guacamole, Server, Agent demo)
    desktop/
      Dockerfile                           # Debian Bookworm + XFCE4 + xrdp desktop sandbox
      entrypoint.sh                        # User creation, session configuration & xrdp runner
    build-golden-image.sh                  # Ubuntu Noble golden image generator for KVM/VirtualBox
    install-agent.sh                       # Linux lender setup script (.deb)
    install-agent.ps1                      # Windows lender setup script (.msi)
    minidc-agent.service                   # systemd unit file for Linux lenders
    nftables-minidc.sh                     # Linux egress isolation rules
    libvirt-net.xml                        # libvirt isolated NAT network config
    agent.example.yml
    server.example.yml
  docs/                                    # Documentation guides
    architecture.md
    api.md
    ws-protocol.md
    windows-lender.md
```

---

## 3. Prerequisites

| Tool | Recommended Version | Purpose |
|---|---|---|
| **Java JDK** | 21 (LTS) or higher | Compiling and running server & agent |
| **Apache Maven** | 3.9+ | Building Java multi-module project |
| **Node.js & npm** | Node 20+ & npm 10+ | Building and running React web portal |
| **Docker & Docker Compose** | Latest Desktop / Engine | Running database, Guacamole, or full demo stack |

---

## 4. Quick Start Options

### Option A: Complete Docker Compose Stack (Easiest)

Runs PostgreSQL 16, guacd, Apache Guacamole, and the MiniDC Server (with UI packaged into static resources) with a single command:

```bash
git clone https://github.com/Eshwar-B/miniDC.git
cd miniDC/deploy
docker compose up -d --build
```

- **Web Portal:** Open [http://localhost:8080](http://localhost:8080)
- **Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Default Dev Admin:** `admin@minidc.local` / `admin12345`

To include the agent and desktop container for the one-laptop demo:
```bash
docker compose --profile demo up -d --build
```

---

### Option B: Zero-Dependency Local Dev (Embedded PostgreSQL)

If you have Java 21 and Maven installed and do not want to install or run external databases:

```bash
# 1. Package the backend modules
mvn -q -DskipTests package

# 2. Run the server with dev and embedded profiles
java -jar minidc-server/target/minidc-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev,embedded
```
*The `embedded` profile boots an in-process PostgreSQL 16 instance storing data in `./data/pg` on port 5432, applies Flyway migrations, and seeds the initial admin user.*

```bash
# 3. In another terminal, run the React development server
cd minidc-ui
npm install
npm run dev
```
Open [http://localhost:5173](http://localhost:5173).

---

### Option C: Developer Mode with Docker Database & Guacamole

```bash
# 1. Start Postgres and Guacamole in Docker
cd deploy
docker compose up -d postgres guacd guacamole

# 2. Run the server locally
cd ..
mvn -q -DskipTests package
java -jar minidc-server/target/minidc-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev

# 3. Run the UI locally
cd minidc-ui
npm run dev
```

---

## 5. Hypervisor Configuration

The agent chooses its sandbox backend via `minidc.hypervisor` in `application.yml` or the environment variable `MINIDC_HYPERVISOR`:

```yaml
minidc:
  hypervisor: docker       # Options: libvirt | virtualbox | docker
  dry-run: false           # Set to true to mock CLI commands in dev environments
  docker-image: minidc-desktop:latest
  probe-host: 127.0.0.1    # Use host.docker.internal when agent runs inside a container
  bind-address: 127.0.0.1  # Localhost or LAN/Tailscale IP
  port-range: 33000-33999
```

- **`libvirt`**: Uses Linux KVM, `virt-install`, and copy-on-write `qcow2` overlays with nftables egress isolation.
- **`virtualbox`**: Uses headless `VBoxManage` on Windows 10/11 with NAT port forwarding and Windows Firewall LAN isolation.
- **`docker`**: Runs container desktops (`minidc-desktop:latest`) with memory caps, CPU quotas, process limits (`--pids-limit 512`), and `--security-opt no-new-privileges`.

---

## 6. One-Laptop Demo Walkthrough

1. Start the stack: `docker compose -f deploy/docker-compose.yml up -d --build`.
2. Open `http://localhost:8080` and log in as `admin@minidc.local` / `admin12345`.
3. Go to the **Admin** page and generate two invite codes.
4. **Lender Registration:** Open a regular browser window, sign up with invite code #1, register a node to get the node ID and token, and start the agent (`--profile demo`). Wait for the node status to display `ONLINE`.
5. **Create an Offer:** In the Lender Hub, create a resource offer (e.g., 2048 MB RAM, 2 CPU cores, 5 GB disk, 2 hours).
6. **Borrower Request:** Open an Incognito/Private window, register with invite code #2, browse the **Marketplace**, pick the offer, and request a lease.
7. **Approval & Provisioning:** The lender approves the request. The agent boots the sandbox and transitions the lease to `ACTIVE`.
8. **Connect:** The borrower clicks **Open Desktop**; Guacamole securely streams the interactive XFCE desktop in the browser via WebSockets.
9. **Teardown:** Click **End Lease** (or let the timer expire). The agent immediately powers down, wipes the container/VM, and cleans up all temporary resources.

---

## 7. Running Tests

Execute the full automated test suite:

```bash
# Run all unit and integration tests across all modules
mvn test

# Run tests with active build logs
mvn surefire-report:report-only
```

Run frontend tests and production build:

```bash
cd minidc-ui
npm run build
```

---

## 8. Technology Decisions & Architecture

- **Java 21 LTS & Virtual Threads:** Uses Java 21 features (records, switch expressions, pattern matching) and virtual threads for TCP forwarder streams.
- **No Lombok:** Codebase is kept clean and transparent with native Java records and explicit accessors.
- **Pessimistic Locking:** Capacity verification during lease creation uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the `Offer` row to prevent double-booking.
- **AES-256-GCM Password Encryption:** Per-lease randomly generated VM passwords are encrypted at rest using a configurable AES-GCM server key.
- **Guacamole Auth-JSON:** Guacamole authentication uses cryptographically signed tokens containing connection parameters; passwords are never sent in plain text to the browser.
- **Dry-Run Capabilities:** Both VM drivers and the Docker driver support dry-run simulation mode (`minidc.dry-run: true`) for testing without hypervisor prerequisites.

---

## 9. Documentation & Resources

- [Complete v1.2 Build Documentation](MiniDC%20–%20Sandbox%20Lease%20Platform%20Complete%20Build%20Documentation.md)
- [System Architecture](docs/architecture.md)
- [REST API Reference](docs/api.md)
- [Agent WebSocket Protocol](docs/ws-protocol.md)
- [Windows Lender & VirtualBox Guide](docs/windows-lender.md)
- **Repository Remote:** [https://github.com/Eshwar-B/miniDC.git](https://github.com/Eshwar-B/miniDC.git)
