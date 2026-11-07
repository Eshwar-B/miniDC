# MiniDC – Idle Laptop Sandbox Lease Platform

MiniDC turns idle Linux and Windows laptops and PCs into mini datacenters. A **lender** installs an **agent** and sets limits (RAM, CPU cores, disk, hours). A **borrower** requests a **lease** from a web portal. The lender approves (or auto-approves). The agent boots a fresh Ubuntu desktop **VM** with those limits (KVM/libvirt on Linux, VirtualBox on Windows). The borrower interacts with the VM desktop directly in their browser through Apache Guacamole (RDP to xrdp inside the VM). At lease end, the VM is wiped and destroyed.

---

## 1. Project Directory Structure

```
minidc/
  pom.xml
  README.md
  .gitignore
  .editorconfig
  minidc-common/                 # Shared Java library: models, enums, WebSocket payloads
    pom.xml
    src/main/java/com/minidc/common/
      enums/LeaseState.java
      enums/NodeStatus.java
      enums/Role.java
      model/VmSpec.java
      model/VmInfo.java
      model/HostInfo.java
      ws/MessageType.java
      ws/WsMessage.java
      ws/payload/RegisterPayload.java
      ws/payload/HeartbeatPayload.java
      ws/payload/CreateVmPayload.java
      ws/payload/VmReadyPayload.java
      ws/payload/VmFailedPayload.java
      ws/payload/VmDestroyedPayload.java
      ws/payload/VmStatsPayload.java
      ws/payload/DestroyVmPayload.java
  minidc-server/                 # Spring Boot control server
    pom.xml
    src/main/java/com/minidc/server/
      MiniDcServerApplication.java
      config/AppProperties.java
      config/WebSocketConfig.java
      config/SchedulingConfig.java
      security/SecurityConfig.java
      security/JwtService.java
      security/JwtAuthFilter.java
      security/CurrentUser.java
      auth/AuthController.java
      auth/AuthService.java
      auth/dto/RegisterRequest.java
      auth/dto/LoginRequest.java
      auth/dto/TokenResponse.java
      user/User.java
      user/UserRepository.java
      user/Invite.java
      user/InviteRepository.java
      node/Node.java
      node/NodeRepository.java
      node/NodeController.java
      node/NodeService.java
      node/dto/CreateNodeRequest.java
      node/dto/CreateNodeResponse.java
      offer/Offer.java
      offer/OfferRepository.java
      offer/OfferController.java
      offer/OfferService.java
      offer/dto/OfferRequest.java
      lease/Lease.java
      lease/LeaseRepository.java
      lease/LeaseController.java
      lease/LeaseService.java
      lease/InvalidStateException.java
      lease/CapacityExceededException.java
      lease/dto/CreateLeaseRequest.java
      lease/dto/LeaseResponse.java
      ws/AgentSocketHandler.java
      ws/AgentSessionRegistry.java
      ws/AgentCommandSender.java
      guac/GuacService.java
      guac/GuacController.java
      audit/AuditLog.java
      audit/AuditLogRepository.java
      audit/AuditService.java
      admin/AdminController.java
      scheduler/LeaseScheduler.java
      error/ApiError.java
      error/GlobalExceptionHandler.java
      crypto/AesGcmCrypto.java
    src/main/resources/
      application.yml
      application-dev.yml
      db/migration/V1__init.sql
    src/test/java/com/minidc/server/
      lease/LeaseServiceTest.java
      lease/LeaseStateMachineTest.java
      auth/AuthControllerIT.java
      support/PostgresTestContainer.java
  minidc-agent/                  # Background node agent daemon
    pom.xml
    src/main/java/com/minidc/agent/
      MiniDcAgentApplication.java
      config/AgentProperties.java
      cmd/Shell.java
      cmd/ShellResult.java
      system/HostStats.java
      system/LimitChecker.java
      system/OsDetector.java
      vm/HypervisorDriver.java
      vm/LibvirtDriver.java
      vm/VirtualBoxDriver.java
      vm/VmManager.java
      vm/CloudInitBuilder.java
      net/TcpForwarder.java
      net/EgressRules.java
      ws/ServerConnection.java
      ws/CommandDispatcher.java
      ws/HeartbeatJob.java
    src/main/resources/
      application.yml
    src/test/java/com/minidc/agent/
      cmd/ShellTest.java
      system/LimitCheckerTest.java
      vm/CloudInitBuilderTest.java
  minidc-ui/                     # React 18 + Vite + TypeScript web portal
    package.json
    tsconfig.json
    vite.config.ts
    index.html
    src/main.tsx
    src/App.tsx
    src/api/client.ts
    src/api/types.ts
    src/auth/AuthContext.tsx
    src/pages/LoginPage.tsx
    src/pages/RegisterPage.tsx
    src/pages/LenderDashboard.tsx
    src/pages/Marketplace.tsx
    src/pages/MyLeases.tsx
    src/pages/AdminPage.tsx
    src/components/LeaseCard.tsx
    src/components/OfferForm.tsx
    src/components/Countdown.tsx
    src/styles.css
  deploy/                        # Docker compose, installation scripts, configs
    docker-compose.yml
    build-golden-image.sh
    install-agent.sh
    install-agent.ps1
    minidc-agent.service
    nftables-minidc.sh
    libvirt-net.xml
    agent.example.yml
    server.example.yml
  docs/                          # Technical guides and protocol documentation
    architecture.md
    api.md
    ws-protocol.md
    windows-lender.md
```

---

## 2. Prerequisites

- **Java Development Kit (JDK):** Version 21 (LTS) or higher.
- **Apache Maven:** Version 3.9+ (or local wrapper).
- **Node.js & npm:** Node 20+ and npm 10+.
- **Docker & Docker Compose:** For running PostgreSQL, guacd, and Apache Guacamole.
- **Hypervisor (Lender Machines):** KVM/libvirt on Linux, Oracle VirtualBox 7.x on Windows.

---

## 3. Quick Start

### Step 1: Start PostgreSQL and Guacamole Infrastructure
```bash
cd deploy && docker compose up -d
```

### Step 2: Build the Java Multi-Module Project
```bash
cd .. && mvn -q -DskipTests package
```

### Step 3: Run the Control Server in Dev Mode
```bash
java -jar minidc-server/target/minidc-server-1.0.0-SNAPSHOT.jar --spring.profiles.active=dev
```
*Note: In `dev` profile, the server automatically initializes database schema, seeds the admin account `admin@minidc.local` with password `admin12345`, and logs an active invite code.*

### Step 4: Run the Web UI
```bash
cd minidc-ui && npm install && npm run dev
```
Open `http://localhost:5173` in your browser.

---

## 4. Documentation Links

- [System Architecture & Data Flows](docs/architecture.md)
- [REST API Specification](docs/api.md)
- [Agent WebSocket Protocol](docs/ws-protocol.md)
- [Windows Lender & VirtualBox Guide](docs/windows-lender.md)

---

## 5. Assumptions & Design Decisions

1. **Spring Boot Version:** Spring Boot 3.3.5 with Spring Security 6, Hibernate 6, and JJWT 0.11.5.
2. **Flyway & PostgreSQL:** Database initialization uses standard PostgreSQL migration scripts (`V1__init.sql`), while integration tests leverage Testcontainers with an automated fallback to in-memory H2 in environments where Docker is absent.
3. **Pessimistic Locking:** Capacity checks lock the `Offer` entity row using `@Lock(LockModeType.PESSIMISTIC_WRITE)` during lease allocation to prevent race conditions.
4. **Dry-Run Mode by Default:** `minidc.dry-run` defaults to `true` in development to allow complete compilation, test verification, and mock execution on developer workstations without requiring physical KVM/libvirt or VirtualBox installations.
5. **Virtual Threads:** Java 21 virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) handle TCP forwarding in the agent for high-concurrency remote desktop data streaming.
6. **No Lombok:** Explicit plain getters, setters, constructors, and records are used throughout.

