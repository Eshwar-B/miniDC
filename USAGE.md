# MiniDC – Complete Usage Guide

> **This document is the definitive reference for running, using, and understanding the MiniDC platform.**
> It covers every user role, every feature, every API endpoint, known limitations, and step-by-step setup from scratch.
> **This file must be updated alongside `PROJECT_STATUS.md` and `README.md` after every phase of development.**

---

## Table of Contents

1. [What is MiniDC?](#1-what-is-minidc)
2. [System Architecture Overview](#2-system-architecture-overview)
3. [Prerequisites & System Requirements](#3-prerequisites--system-requirements)
4. [Quick Start – Fresh Device Setup](#4-quick-start--fresh-device-setup)
   - [Option A: Local Development (Embedded DB, No Docker)](#option-a-local-development-embedded-db-no-docker-fastest)
   - [Option B: Full Docker Compose Stack](#option-b-full-docker-compose-stack-production-like)
5. [Running the Frontend](#5-running-the-frontend)
6. [Interactive API Test Console](#6-interactive-api-test-console)
7. [User Roles & Capabilities](#7-user-roles--capabilities)
8. [Admin User – Complete Feature Guide](#8-admin-user--complete-feature-guide)
9. [Lender (Member) – Complete Feature Guide](#9-lender-member--complete-feature-guide)
10. [Borrower (Member) – Complete Feature Guide](#10-borrower-member--complete-feature-guide)
11. [Agent (Edge Node Daemon) – Setup Guide](#11-agent-edge-node-daemon--setup-guide)
12. [Full API Reference](#12-full-api-reference)
13. [Security Architecture](#13-security-architecture)
14. [Rate Limiting & Brute Force Protection](#14-rate-limiting--brute-force-protection)
15. [What Works Now vs. What's Coming](#15-what-works-now-vs-whats-coming)
16. [Troubleshooting](#16-troubleshooting)
17. [Environment Variables Reference](#17-environment-variables-reference)
18. [Running Tests](#18-running-tests)

---

## 1. What is MiniDC?

MiniDC is a **peer-to-peer sandbox compute leasing platform**. It allows:

- **Lenders** — people who have idle desktop or server hardware — to advertise their spare CPU, RAM, and disk on a marketplace.
- **Borrowers** — developers, students, or researchers — to rent that compute on demand and get a live Ubuntu XFCE desktop **inside their browser**, powered by Apache Guacamole over RDP.
- **Admins** — platform operators who govern invite codes, user accounts, and audit trails.

**Core value proposition:**

> "Instead of buying cloud VMs, borrow idle compute from people around you. Instead of sharing raw memory over a network, the borrower gets a fully isolated virtual machine that runs entirely on the lender's hardware."

---

## 2. System Architecture Overview

```
┌────────────────────┐   HTTPS/JWT    ┌─────────────────────────────────────┐
│  Browser / React   │ ◄────────────► │    minidc-server (Spring Boot 3.3)   │
│  UI  :5173         │                │    Port: 8080 (prod) / 8088 (dev)    │
└────────────────────┘                │                                      │
        │                             │  ┌───────────┐  ┌─────────────────┐ │
        │ Guacamole                   │  │ PostgreSQL │  │  Redis          │ │
        ▼                             │  │ (State DB) │  │  (Sessions,     │ │
┌────────────────────┐                │  └───────────┘  │   Rate Limits,  │ │
│ Apache Guacamole   │                │                  │   Token BL)     │ │
│ Web Client :8081   │                │                  └─────────────────┘ │
└────────────────────┘                └──────────────────────┬──────────────┘
                                                             │ WebSocket
                                                             │ /ws/agent
                                                   ┌─────────▼──────────┐
                                                   │  minidc-agent      │
                                                   │  (Edge Node Daemon) │
                                                   │  Runs on Lender PC  │
                                                   └──────────┬──────────┘
                                                              │
                                                    ┌─────────▼──────────┐
                                                    │  Hypervisor Layer   │
                                                    │  KVM / VirtualBox   │
                                                    │  / Docker           │
                                                    └────────────────────┘
```

**Components:**

| Component | Technology | Description |
|---|---|---|
| `minidc-server` | Spring Boot 3.3.5, Java 21 | Control plane API server |
| `minidc-agent` | Spring Boot 3.3.5, Java 21 | Edge daemon running on lender machines |
| `minidc-common` | Java 21 | Shared DTOs and enums |
| `minidc-ui` | React 18, TypeScript, Vite | Web frontend |
| PostgreSQL 16 | RDBMS | Persistent data store (users, nodes, offers, leases) |
| Redis 7 | In-memory cache | Session tokens, rate limit counters, token blacklist |
| Apache Guacamole | RDP proxy | In-browser remote desktop |

---

## 3. Prerequisites & System Requirements

### Server Machine (runs `minidc-server`)

| Requirement | Minimum | Notes |
|---|---|---|
| Java | 21 LTS | OpenJDK or Temurin |
| Maven | 3.9+ | Or use the included `mvn.cmd` / `./mvnw` wrapper |
| PostgreSQL | 14+ (or embedded) | Embedded Postgres auto-starts in `dev` profile |
| Redis | 7+ (optional) | In-memory fallback used if unavailable |
| RAM | 512 MB+ | 1 GB+ recommended |
| OS | Any (Windows/Linux/macOS) | Tested on Windows 11 and Ubuntu 22.04 |

### Lender Machine (runs `minidc-agent`)

| Requirement | Minimum | Notes |
|---|---|---|
| Java | 21 LTS | Same as server |
| Docker | 24+ | For Docker-based VM driver (easiest) |
| CPU | Hardware virtualization enabled | VT-x or AMD-V in BIOS for KVM |
| RAM | 8 GB+ | To have headroom for guest VMs |
| OS | Linux (KVM), Windows 10/11 (VirtualBox), or Any (Docker) | |

### Developer / Test Machine (this repo)

```
Java 21 + Maven + Node.js 20+ + npm 10+
```

---

## 4. Quick Start – Fresh Device Setup

### Option A: Local Development (Embedded DB, No Docker) ★ Fastest

This is the simplest path. No PostgreSQL or Redis installation needed.

#### Step 1 – Clone the Repository

```bash
git clone https://github.com/Eshwar-B/miniDC.git
cd miniDC
```

#### Step 2 – Build All Modules

```bash
# Windows
.\mvn.cmd clean package -DskipTests

# Linux / macOS
./mvnw clean package -DskipTests
```

This produces:
- `minidc-server/target/minidc-server-1.0.0-SNAPSHOT.jar`
- `minidc-agent/target/minidc-agent-1.0.0-SNAPSHOT.jar`

#### Step 3 – Start the Backend Server

```bash
java -jar minidc-server/target/minidc-server-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev,embedded \
  --server.port=8088
```

**What `dev,embedded` does:**
- `dev` → Seeds the default admin user and a startup invite code
- `embedded` → Starts an embedded PostgreSQL instance on port 5433 (no external DB needed)
- Redis is unavailable → Server silently falls back to thread-safe in-memory caching

**Server starts in ~9 seconds. You will see:**
```
MiniDC Dev Admin Seeded successfully:
Email:    admin@minidc.local
Password: admin12345
Startup Invite Code: INV-DEV-ADMIN-2026
```

#### Step 4 – Verify the Server is Up

Open in your browser:
```
http://localhost:8088/actuator/health
```
Expected response: `{"status":"UP"}`

---

### Option B: Full Docker Compose Stack (Production-like)

#### Step 1 – Clone & Configure

```bash
git clone https://github.com/Eshwar-B/miniDC.git
cd miniDC
cp deploy/server.example.yml deploy/server.yml
```

Edit `deploy/server.yml` and change all `change-me` values:

```yaml
minidc:
  jwt-secret: "your-32-character-secret-here-abc"
  data-key: "your-base64-encoded-32-byte-key="
  guac-json-secret: "your32hexsecretkey00112233445566"
```

#### Step 2 – Start All Services

```bash
cd deploy
docker-compose up -d
```

This starts:
- `minidc-postgres` on port 5432
- `minidc-redis` on port 6379
- `guacd` (Guacamole daemon)
- `guacamole` web client on port 8081
- `minidc-server` on port 8080

#### Step 3 – Verify

```
http://localhost:8080/actuator/health  → {"status":"UP"}
http://localhost:8081/guacamole        → Guacamole login page
```

---

## 5. Running the Frontend

#### Step 1 – Install Dependencies

```bash
cd minidc-ui
npm install
```

#### Step 2 – Start Development Server

```bash
npm run dev
```

The UI starts at **http://localhost:5173**

The Vite dev server proxies all `/api/*` and `/actuator/*` requests to the backend at `http://localhost:8088`.

#### Step 3 – Build for Production

```bash
npm run build
```

Output is in `minidc-ui/dist/`. Serve with any static file server or via Spring Boot's `static/` resources.

---

## 6. Interactive API Test Console

A standalone browser-based API test console is available at:

```
http://localhost:5173/api-tester.html
```

This page lets you execute all 10 endpoints in sequence directly from the browser with live response inspection:
1. Health check
2. Admin authentication
3. Generate invite code
4. Register user
5. View profile
6. Register edge node
7. Publish compute offer
8. Browse marketplace
9. Rate limiter test (burst 6 requests → triggers 429)
10. JWT revocation (logout + verify blacklist)

---

## 7. User Roles & Capabilities

MiniDC has **two roles** (both map to the same `MEMBER` role in the current implementation — role-based differentiation between lender and borrower is by behavior, not separate enums):

| Role | Description | How to Obtain |
|---|---|---|
| `ADMIN` | Platform operator. Full access to all users, nodes, leases, audit logs. Can ban users and generate invite codes. | Seeded automatically on first startup in `dev` mode. |
| `MEMBER` | Regular user. Can act as both lender (register nodes, create offers) and borrower (browse marketplace, request leases). | Requires a valid single-use invite code from an Admin. |

> **Important:** A single account can act as both a lender AND a borrower simultaneously. You register a node and publish offers to lend; you browse the marketplace and create leases to borrow.

---

## 8. Admin User – Complete Feature Guide

### Default Dev Credentials

| Field | Value |
|---|---|
| **Email** | `admin@minidc.local` |
| **Password** | `admin12345` |
| **Role** | `ADMIN` |
| **Default Invite Code** | `INV-DEV-ADMIN-2026` (valid 30 days, pre-seeded) |

### Logging In

**Via Web UI:**
1. Open `http://localhost:5173`
2. Enter email `admin@minidc.local` and password `admin12345`
3. Click **Sign In** → You are redirected to the Admin Dashboard

**Via API:**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "admin@minidc.local",
  "password": "admin12345"
}
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```
Save this token. Use it as `Authorization: Bearer <token>` for all subsequent admin API calls.

---

### Admin Feature 1 – Generate Invite Codes

Invite codes are **single-use, 7-day** tokens that allow new users to register.

**Via Admin Panel UI:**
1. Navigate to the **Admin** tab in the top navigation
2. Click **"Generate Invite Code"**
3. Copy the displayed code (e.g., `INV-HBBXYJZEYXQSZL0Y`)
4. Send the code to the person you want to invite

**Via API:**
```http
POST /api/admin/invites
Authorization: Bearer <admin-token>
```
Response:
```json
{
  "code": "INV-HBBXYJZEYXQSZL0Y",
  "expiresAt": "2026-11-15T12:22:51.957Z"
}
```

**What you can do:**
- ✅ Generate as many invite codes as needed
- ✅ Each code is usable exactly once
- ✅ Codes expire after 7 days automatically

**What you cannot do:**
- ❌ View list of all previously generated invite codes (UI not built yet)
- ❌ Revoke an unused invite code (planned for Phase 10)
- ❌ Set custom expiry on individual invite codes (fixed at 7 days)

---

### Admin Feature 2 – Ban Users

Permanently prevents a user from logging in or performing any actions.

**Via API:**
```http
POST /api/admin/users/{userId}/ban
Authorization: Bearer <admin-token>
```
Response: The updated User object with `"banned": true`.

**What you can do:**
- ✅ Ban any MEMBER user
- ✅ Banned users cannot authenticate (even with valid credentials)

**What you cannot do:**
- ❌ Unban a user via API (requires direct database update currently)
- ❌ Ban another Admin via API (admin self-protection)

---

### Admin Feature 3 – View All Nodes

```http
GET /api/admin/nodes
Authorization: Bearer <admin-token>
```
Returns all registered edge nodes across all lenders, with status and hardware specs.

---

### Admin Feature 4 – View All Leases

```http
GET /api/admin/leases
Authorization: Bearer <admin-token>
```
Returns every lease in the system with full state machine details.

---

### Admin Feature 5 – Audit Log

Every significant action in the platform is logged (invite creation, user banning, lease approval, etc.).

```http
GET /api/admin/audit
Authorization: Bearer <admin-token>
```
Returns the most recent audit log entries with actor, event type, timestamp, and detail string.

---

### Admin Feature 6 – Logout (Token Revocation)

```http
POST /api/auth/logout
Authorization: Bearer <admin-token>
```
The JWT is immediately SHA-256 hashed and added to the Redis token blacklist. Any subsequent request using that token returns `403 Forbidden`.

---

## 9. Lender (Member) – Complete Feature Guide

A **lender** is any `MEMBER` account that wants to contribute their idle hardware to the marketplace.

### Registration

1. Obtain an invite code from an Admin (e.g., `INV-DEV-ADMIN-2026` works in dev mode).
2. Open `http://localhost:5173/register`
3. Fill in:
   - **Email** (unique)
   - **Password** (must be secure)
   - **Display Name**
   - **Invite Code**
4. Click **Create Account** → You receive a JWT and are redirected to the dashboard.

**Via API:**
```http
POST /api/auth/register
Content-Type: application/json

{
  "email": "lender@example.com",
  "password": "SecurePass123!",
  "displayName": "My Lab Workstation",
  "inviteCode": "INV-DEV-ADMIN-2026"
}
```
Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

> **Note:** The invite code `INV-DEV-ADMIN-2026` is pre-seeded only in `dev` mode. In production, admins must generate and share fresh invite codes.

---

### Lender Feature 1 – Register an Edge Node

A node represents the physical computer where VMs will run.

**Via UI:** Navigate to the **Lender Dashboard** → Click **"Register New Node"** → Enter a name.

**Via API:**
```http
POST /api/nodes
Authorization: Bearer <member-token>
Content-Type: application/json

{
  "name": "My-Home-Workstation"
}
```
Response:
```json
{
  "nodeId": "74174e91-adad-4ae3-bd09-6e7b7a6be368",
  "agentToken": "2RbxTxxUnKaYoXzaZzZC6cdexawbCFUk1snOj1bd1QM"
}
```

**Save both values:**
- `nodeId` → Used when creating offers
- `agentToken` → Configured into the agent daemon running on your lender machine

**Node Statuses:**

| Status | Meaning |
|---|---|
| `OFFLINE` | Node is registered but agent is not connected |
| `ONLINE` | Agent is connected and heartbeating |
| `PAUSED` | Lender manually paused — no new leases accepted |

**What you can do:**
- ✅ Register multiple nodes under one account
- ✅ Each node gets a unique tamper-proof agent authentication token
- ✅ Pause or resume a node to block/allow new leases

**What you cannot do:**
- ❌ Delete a node (planned Phase 8)
- ❌ Rename a node after creation (planned Phase 8)
- ❌ Have the same node connect from two locations simultaneously

---

### Lender Feature 2 – View Your Nodes

**Via UI:** Lender Dashboard automatically shows your nodes with their status.

**Via API:**
```http
GET /api/nodes/mine
Authorization: Bearer <member-token>
```

---

### Lender Feature 3 – Pause / Resume a Node

Stop accepting new lease requests on a node without deleting it.

```http
PUT /api/nodes/{nodeId}/pause
Authorization: Bearer <member-token>

PUT /api/nodes/{nodeId}/resume
Authorization: Bearer <member-token>
```

---

### Lender Feature 4 – Publish a Compute Offer

An offer describes how much of your node's hardware you're willing to share and under what conditions.

**Via UI:** Lender Dashboard → **"Create Offer"** form.

**Via API:**
```http
POST /api/offers
Authorization: Bearer <member-token>
Content-Type: application/json

{
  "nodeId": "74174e91-adad-4ae3-bd09-6e7b7a6be368",
  "maxRamMb": 16384,
  "maxCores": 8,
  "maxDiskGb": 100,
  "maxHours": 24,
  "windowStart": "08:00",
  "windowEnd": "22:00",
  "autoApprove": true,
  "allowedUserIds": []
}
```

**Fields explained:**

| Field | Description | Example |
|---|---|---|
| `nodeId` | UUID of your registered node | `74174e91...` |
| `maxRamMb` | Maximum RAM you offer (MB) | `16384` = 16 GB |
| `maxCores` | Maximum CPU cores | `8` |
| `maxDiskGb` | Maximum disk space (GB) | `100` |
| `maxHours` | Maximum lease duration | `24` |
| `windowStart` | Start time you allow usage | `"08:00"` (optional) |
| `windowEnd` | End time you allow usage | `"22:00"` (optional) |
| `autoApprove` | Skip manual approval step | `true` |
| `allowedUserIds` | Restrict to specific users (empty = public) | `[]` |

Response:
```json
{
  "id": "f97a2b58-4459-47ee-a1ad-cd74bb9ca0a0",
  "nodeId": "74174e91...",
  "maxRamMb": 16384,
  "maxCores": 8,
  "maxDiskGb": 100,
  "maxHours": 24,
  "autoApprove": true,
  "active": true
}
```

**What you can do:**
- ✅ Publish multiple offers from the same node with different configurations
- ✅ Restrict visibility to specific user IDs (private offers)
- ✅ Set time windows during which the offer is active
- ✅ Enable auto-approve so borrowers get instant access without waiting

**What you cannot do:**
- ❌ An offer for a node in `OFFLINE` status will not appear in the marketplace for borrowers
- ❌ Request more resources than the node's registered total (enforced by agent)

---

### Lender Feature 5 – Update or Deactivate an Offer

```http
PUT /api/offers/{offerId}
Authorization: Bearer <member-token>
Content-Type: application/json

{ ... same fields as POST ... }

DELETE /api/offers/{offerId}
Authorization: Bearer <member-token>
```

---

### Lender Feature 6 – View & Manage Incoming Lease Requests

When `autoApprove: false`, borrowers' lease requests land in your **incoming queue** for manual review.

**Via UI:** Lender Dashboard → **"Incoming Requests"** tab.

**Via API:**
```http
GET /api/leases/incoming
Authorization: Bearer <member-token>
```

**Approve a request:**
```http
POST /api/leases/{leaseId}/approve
Authorization: Bearer <member-token>
```

**Reject a request:**
```http
POST /api/leases/{leaseId}/reject?reason=Outside+operating+hours
Authorization: Bearer <member-token>
```

**Force-kill an active lease:**
```http
POST /api/leases/{leaseId}/kill?reason=Policy+violation
Authorization: Bearer <member-token>
```

---

## 10. Borrower (Member) – Complete Feature Guide

Any `MEMBER` account can browse the marketplace and request compute time.

### Borrower Feature 1 – Browse the Marketplace

**Via UI:** Click the **"Marketplace"** tab in navigation.

**Via API:**
```http
GET /api/offers/available
Authorization: Bearer <member-token>
```

> **Important:** Only offers where:
> - The underlying node status is `ONLINE`
> - The offer is marked `active: true`
> - You are either not restricted (empty `allowedUserIds`) or specifically allowed
>
> ...will appear in this list. If a lender's machine is off, their offer does not show.

---

### Borrower Feature 2 – Request a Lease

Pick an offer from the marketplace and request compute resources within its limits.

**Via UI:** Marketplace → Click **"Request Lease"** on an offer → Fill the form.

**Via API:**
```http
POST /api/leases
Authorization: Bearer <member-token>
Content-Type: application/json

{
  "offerId": "f97a2b58-4459-47ee-a1ad-cd74bb9ca0a0",
  "ramMb": 8192,
  "cores": 4,
  "diskGb": 50,
  "requestedHours": 2
}
```

**Constraints:**
- `ramMb` ≥ 512 MB, must not exceed the offer's `maxRamMb`
- `cores` ≥ 1, must not exceed the offer's `maxCores`
- `diskGb` ≥ 5, must not exceed the offer's `maxDiskGb`
- `requestedHours` ≥ 1, must not exceed the offer's `maxHours`
- You can only have **1 active lease at a time** (configurable in `application.yml`)

**Lease State Machine:**

```
REQUESTED → PENDING_PROVISION → PROVISIONING → ACTIVE → TERMINATING → TERMINATED
                                                      ↘ FAILED
         → REJECTED
```

| State | Meaning |
|---|---|
| `REQUESTED` | Lease submitted; waiting for lender approval (or auto-approve) |
| `PENDING_PROVISION` | Approved; waiting for agent to pick up the create-VM command |
| `PROVISIONING` | Agent is creating the VM |
| `ACTIVE` | VM is running; borrower can connect |
| `TERMINATING` | Lease ended; agent is destroying the VM |
| `TERMINATED` | Completely shut down and cleaned up |
| `REJECTED` | Lender rejected the request |
| `FAILED` | VM provisioning failed |

---

### Borrower Feature 3 – View Your Leases

**Via UI:** Click **"My Leases"** in navigation.

**Via API:**
```http
GET /api/leases/mine
Authorization: Bearer <member-token>
```

Returns all leases you've requested, with current state, VM connection details (when active), and countdown timers.

---

### Borrower Feature 4 – Connect to Your VM Desktop (via Guacamole)

When a lease reaches `ACTIVE` state, the VM is live and you can connect to the full Ubuntu XFCE desktop in your browser.

**Via UI:** My Leases → Click **"Open Desktop"** button on the active lease card → Opens the Guacamole browser desktop.

**Via API:**
```http
GET /api/leases/{leaseId}/connect
Authorization: Bearer <member-token>
```
Response:
```json
{
  "url": "http://localhost:8081/guacamole/#/client/lease-<uuid>?token=...",
  "token": "encrypted-auth-token",
  "connectionId": "lease-<uuid>"
}
```

Navigate to the `url` in your browser → You are automatically authenticated into the Guacamole desktop.

**Desktop Features:**
- ✅ Full XFCE Ubuntu desktop in browser (no additional software needed)
- ✅ Copy-paste between local machine and VM (use `Ctrl+Alt+Shift` to open Guacamole sidebar)
- ✅ Audio forwarding enabled
- ✅ Screen auto-resize to browser window

> **Requires:** Apache Guacamole to be running (included in Docker Compose stack). In pure local dev mode, the connect URL is generated but the Guacamole UI may not be accessible.

---

### Borrower Feature 5 – End a Lease Early

Terminates the VM before the scheduled `endsAt` time.

**Via UI:** My Leases → **"End Lease"** button.

**Via API:**
```http
POST /api/leases/{leaseId}/end
Authorization: Bearer <member-token>
```

This triggers the full teardown: `ACTIVE → TERMINATING → TERMINATED`. All VM data is **permanently deleted**.

---

### Borrower Feature 6 – View a Specific Lease

```http
GET /api/leases/{leaseId}
Authorization: Bearer <member-token>
```

---

## 11. Agent (Edge Node Daemon) – Setup Guide

The agent runs on the **lender's physical machine** and manages VM lifecycle on behalf of the control server.

### Step 1 – Register the Node in the Portal

Follow the [Lender Feature 1](#lender-feature-1--register-an-edge-node) steps above. You'll receive a `nodeId` and `agentToken`.

### Step 2 – Configure the Agent

Copy the example config:

```bash
cp deploy/agent.example.yml /etc/minidc/agent.yml  # Linux
# or create minidc-agent/src/main/resources/application-local.yml  # Dev
```

Edit the configuration:

```yaml
minidc:
  server-url: ws://your-server-host:8088/ws/agent
  node-id: 74174e91-adad-4ae3-bd09-6e7b7a6be368   # from portal
  agent-token: 2RbxTxxUnKaYoXzaZzZC6cdexawbCFUk1snOj1bd1QM  # from portal
  hypervisor: docker       # Options: auto, docker, kvm, virtualbox
  bind-address: 127.0.0.1  # Where VM RDP ports are bound
  images-dir: ./data/images
  leases-dir: ./data/leases
  reserve-ram-percent: 25  # Keep 25% RAM reserved for host OS
  port-range: 33000-33999  # Range of TCP ports for RDP forwarding
  dry-run: false           # Set true to test without actually creating VMs
  docker-image: minidc-desktop:latest
  probe-host: 127.0.0.1
```

### Step 3 – Build the Golden Desktop Image (Docker driver)

```bash
cd deploy
docker build -t minidc-desktop:latest ./desktop
```

This creates the Ubuntu XFCE + xrdp image that is instantiated for each lease.

### Step 4 – Run the Agent

```bash
# Dev mode (dry-run, no real VMs)
java -jar minidc-agent/target/minidc-agent-1.0.0-SNAPSHOT.jar \
  --minidc.dry-run=true \
  --minidc.server-url=ws://localhost:8088/ws/agent \
  --minidc.node-id=<your-node-id> \
  --minidc.agent-token=<your-agent-token>

# Production (Linux, as systemd service)
sudo bash deploy/install-agent.sh
sudo systemctl start minidc-agent
```

### Step 5 – Verify Agent is ONLINE

After the agent starts, it connects to the server via WebSocket and sends a `REGISTER` message followed by periodic `HEARTBEAT` messages (every 30 seconds).

The node's status in the portal should change from `OFFLINE` to `ONLINE`:
```http
GET /api/nodes/mine
Authorization: Bearer <member-token>
```

**Supported Hypervisor Drivers:**

| Driver | OS | Requirement | Notes |
|---|---|---|---|
| `docker` | Any | Docker Engine installed | Easiest; uses containers as lightweight VMs |
| `kvm` | Linux | `/dev/kvm` present, libvirt installed | Native performance for Linux hosts |
| `virtualbox` | Windows 10/11 | VirtualBox 7+ installed | For Windows lender machines |
| `auto` | Any | Auto-detected based on OS and available tools | Recommended default |

---

## 12. Full API Reference

**Base URL (dev):** `http://localhost:8088`
**All protected endpoints require:** `Authorization: Bearer <JWT>`

### Authentication Endpoints (Public)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/auth/register` | Register new user with invite code |
| `POST` | `/api/auth/login` | Authenticate and receive JWT |
| `POST` | `/api/auth/logout` | Revoke JWT (blacklisted in Redis) |
| `GET` | `/api/me` | Get current user's profile |

### Admin Endpoints (ADMIN role required)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/admin/invites` | Generate a new 7-day invite code |
| `POST` | `/api/admin/users/{id}/ban` | Ban a user account |
| `GET` | `/api/admin/nodes` | View all nodes in the platform |
| `GET` | `/api/admin/leases` | View all leases in the platform |
| `GET` | `/api/admin/audit` | View system audit log |

### Node Endpoints (MEMBER role)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/nodes` | Register a new edge node |
| `GET` | `/api/nodes/mine` | Get your nodes |
| `PUT` | `/api/nodes/{id}/pause` | Pause node (block new leases) |
| `PUT` | `/api/nodes/{id}/resume` | Resume paused node |

### Offer Endpoints (MEMBER role)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/offers` | Publish a new compute offer |
| `PUT` | `/api/offers/{id}` | Update an existing offer |
| `DELETE` | `/api/offers/{id}` | Deactivate/remove an offer |
| `GET` | `/api/offers/available` | Browse active marketplace offers |

### Lease Endpoints (MEMBER role)

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/leases` | Request a compute lease |
| `GET` | `/api/leases/mine` | Get your lease requests |
| `GET` | `/api/leases/incoming` | Get incoming requests (as lender) |
| `GET` | `/api/leases/{id}` | Get a specific lease |
| `POST` | `/api/leases/{id}/approve` | Approve a pending lease request |
| `POST` | `/api/leases/{id}/reject` | Reject a lease request |
| `POST` | `/api/leases/{id}/kill` | Force-terminate an active lease |
| `POST` | `/api/leases/{id}/end` | Borrower-initiated graceful end |

### Guacamole / Desktop Endpoints (MEMBER role, lease owner)

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/leases/{id}/connect` | Get Guacamole connection URL for active lease |

### System Endpoints (Public)

| Method | Path | Description |
|---|---|---|
| `GET` | `/actuator/health` | Server health status |

### WebSocket Endpoint (Agent)

| Path | Protocol | Description |
|---|---|---|
| `/ws/agent` | WebSocket + JSON | Agent-to-server persistent control channel |

---

## 13. Security Architecture

### JWT Authentication

- Tokens are signed with **HS256** using a configurable secret (min 32 characters).
- Default expiry: **12 hours** (configurable via `minidc.jwt-hours`).
- Every request is validated by `JwtAuthFilter` before reaching controllers.
- Revoked tokens are SHA-256 hashed and stored in Redis with TTL matching their remaining validity.

### Password Security

- All passwords are hashed with **BCrypt** (cost factor 12).
- VM passwords inside leases are encrypted with **AES-256-GCM**.

### Guacamole Connection Security

- Desktop session tokens are signed with **HMAC-SHA256** and encrypted with **AES-128-CBC**.
- Tokens are time-limited to the lease's `endsAt` timestamp.
- Only the lease's borrower can retrieve the connection URL.

### Agent Authentication

- Agents connect via WebSocket with a bearer token (`MINIDC_AGENT_TOKEN`).
- The token is stored as a **BCrypt hash** in the database — the plaintext is never persisted.

### CORS

Currently configured to allow `http://localhost:5173` only. Update `SecurityConfig.corsConfigurationSource()` for production deployments.

---

## 14. Rate Limiting & Brute Force Protection

The Redis-backed sliding-window rate limiter protects sensitive endpoints:

| Endpoint | Limit | Window | Response when exceeded |
|---|---|---|---|
| `POST /api/auth/login` | **5 requests** | 60 seconds per IP | `HTTP 429 Too Many Requests` |
| `POST /api/auth/register` | **3 requests** | 60 seconds per IP | `HTTP 429 Too Many Requests` |
| `POST /api/leases` | **10 requests** | 60 seconds per IP | `HTTP 429 Too Many Requests` |

**429 Response body:**
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Please wait 60 seconds before retrying.",
  "retryAfterSeconds": 60
}
```

**Response headers on rate-limited requests:**
```
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
Retry-After: 60
```

> Rate limiting can be disabled for testing: `--minidc.rate-limit.enabled=false`

---

## 15. What Works Now vs. What's Coming

### ✅ Completed Features (Phases 1–6)

| Feature | Status | Phase |
|---|---|---|
| Multi-module monorepo (server, agent, common, UI) | ✅ Done | 1 |
| PostgreSQL schema + Flyway migrations | ✅ Done | 1 |
| JWT authentication (login, register, logout) | ✅ Done | 1 |
| Invite-only registration flow | ✅ Done | 1 |
| Lease state machine (8 states) | ✅ Done | 1 |
| Multi-hypervisor abstraction (KVM, VirtualBox, Docker) | ✅ Done | 1 |
| Embedded PostgreSQL (zero-dependency dev mode) | ✅ Done | 1 |
| Apache Guacamole token cryptography (HMAC-SHA256 + AES-CBC) | ✅ Done | 2 |
| Guacamole REST token exchange | ✅ Done | 2 |
| Redis distributed session management | ✅ Done | 3 |
| Redis-backed JWT token blacklisting | ✅ Done | 3 |
| Distributed lease locking (prevent double-booking) | ✅ Done | 3 |
| Sliding-window API rate limiting (login, register, leases) | ✅ Done | 3 |
| Node presence tracking with TTL heartbeat | ✅ Done | 3 |
| In-memory fallback when Redis is unavailable | ✅ Done | 3 |
| Apache Kafka event streaming & dead letter queue routing | ✅ Done | 4 |
| High-throughput time-series telemetry buffer (`TelemetryService`) | ✅ Done | 4 |
| Autonomous agent hypervisor crash reconciliation (`HypervisorReconciler`) | ✅ Done | 4 |
| Real-time bi-directional STOMP WebSocket broker (`/ws/stomp`) | ✅ Done | 5 |
| Live marketplace sync (`/topic/offers`) with zero polling | ✅ Done | 5 |
| Targeted lease lifecycle notifications (`/user/{id}/queue/leases`) | ✅ Done | 5 |
| Real-time CPU & RAM live telemetry gauges on lease cards | ✅ Done | 5 |
| Zero-Trust LAN isolation (RFC 1918 subnets blocked on Linux & Windows) | ✅ Done | 6 |
| Anti-spam/anti-abuse port filter (SMTP 25/465/587, SSH 22 blocked) | ✅ Done | 6 |
| WireGuard Curve25519/X25519 key generation & `wg0.conf` renderer | ✅ Done | 6 |
| WireGuard exit gateway IP pool allocator (`10.44.0.0/24`) | ✅ Done | 6 |
| Egress verification API (`POST /api/egress/verify`) & UI simulator | ✅ Done | 6 |
| Admin panel (invites, banning, audit log, all-nodes, all-leases, egress) | ✅ Done | 1–6 |
| React frontend (login, register, marketplace, dashboard, leases, admin) | ✅ Done | 1–6 |
| 73/73 unit tests passing | ✅ Done | 1–6 |

### 🚧 Coming Soon (Planned Phases 7–10)

| Feature | Phase | Why |
|---|---|---|
| AI-powered workload matchmaking | Phase 7 | Match borrower requests to the most reliable/compatible node using ML |
| Anomaly detection (cryptomining, port scanning) | Phase 7 | Automatically terminate rogue sandboxes |
| Persistent home directory snapshots (MinIO/S3) | Phase 8 | Allow borrowers to retain `/home` data across leases |
| Compute credits and billing | Phase 9 | Fair credit exchange and Stripe payouts for lenders |
| Kubernetes deployment + Helm charts | Phase 10 | Production high-availability clustering |
| GitHub Actions CI/CD pipeline | Phase 10 | Automated test + build + deploy on push |
| OpenTelemetry distributed tracing | Phase 10 | Full observability across server and agents |

### ❌ Known Limitations (Current Build)

- **VM connection requires Guacamole:** In pure local dev mode (no Docker), the `GET /api/leases/{id}/connect` URL is generated correctly but the Guacamole UI is not accessible. Run the full Docker Compose stack for end-to-end VM browser access.
- **Marketplace shows 0 offers when node is OFFLINE:** The node must have the agent daemon running and heartbeating. Register your node, then start the agent.
- **Maximum 1 active lease per user:** Configurable via `minidc.max-active-leases-per-user` in `application.yml`.
- **No password reset:** Forgot your password? A database update is required manually. Email-based password reset is planned for Phase 5.
- **No email verification:** Accounts are active immediately upon registration. Email verification is planned.
- **No unban API:** Unbanning a user requires a direct `UPDATE users SET banned = false WHERE id = '...'` database query.
- **CORS locked to localhost:5173:** Needs to be updated in `SecurityConfig.java` for production domains.
- **Invite codes cannot be listed or revoked:** Only the generated code is returned once. Store it safely.

---

## 16. Troubleshooting

### "Invalid email or password" on login

| Cause | Fix |
|---|---|
| Wrong password | Dev admin password is `admin12345` (10 characters), not `admin` |
| Rate limit triggered | Wait 60 seconds and try again (or restart the server to reset in-memory counters) |
| Account banned | An admin must check the database: `SELECT banned FROM users WHERE email = '...'` |

### "Available Offers: 0" in marketplace

The node must be `ONLINE`. Ensure the `minidc-agent` is running and connected to the server. The node status will update from `OFFLINE` to `ONLINE` within one heartbeat cycle (30 seconds).

### Server returns HTTP 500 on login

Usually a JSON parsing error. Ensure your Content-Type is `application/json` and your JSON is valid. Test with:

```powershell
$body = @{ email = "admin@minidc.local"; password = "admin12345" } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8088/api/auth/login" -Method POST -ContentType "application/json" -Body $body
```

### Port conflicts

| Port | Conflict | Fix |
|---|---|---|
| 8080 | Keycloak or another Spring app | Use `--server.port=8088` |
| 5432 | Existing PostgreSQL | Use `embedded` profile (auto-assigns port 5433) |
| 6379 | Existing Redis | App falls back to in-memory cache automatically |
| 5173 | Another Vite app | Change port in `vite.config.ts` |

### Agent shows as OFFLINE after connecting

Check the agent logs for connection errors. Common causes:
- Wrong `server-url` (must be `ws://`, not `http://`)
- Firewall blocking WebSocket connections
- Wrong `node-id` or `agent-token` values

### mvn is not recognized

Use the included wrapper:
```
# Windows
.\mvn.cmd test

# Linux / macOS
./mvnw test
```

---

## 17. Environment Variables Reference

### Server (`minidc-server`)

| Variable | Default | Description |
|---|---|---|
| `MINIDC_JWT_SECRET` | `change-me-change-me-change-me-32` | HS256 JWT signing key (min 32 chars) |
| `MINIDC_DATA_KEY` | `Y2hhbmdlLW1l...` | Base64-encoded AES-256 key for VM password encryption |
| `MINIDC_GUAC_SECRET` | `00112233445566778899aabbccddeeff` | 32 hex chars shared with Guacamole auth-json |
| `MINIDC_REDIS_ENABLED` | `true` | Set `false` to force in-memory fallback |
| `MINIDC_RATE_LIMIT_ENABLED` | `true` | Set `false` to disable rate limiting |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/minidc` | PostgreSQL JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `minidc` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | `change-me` | Database password |

### Agent (`minidc-agent`)

| Variable | Default | Description |
|---|---|---|
| `MINIDC_SERVER_URL` | `ws://localhost:8080/ws/agent` | WebSocket URL to control server |
| `MINIDC_NODE_ID` | (required) | Node UUID from portal |
| `MINIDC_AGENT_TOKEN` | (required) | Agent auth token from portal |
| `MINIDC_HYPERVISOR` | `auto` | `auto`, `docker`, `kvm`, `virtualbox` |
| `MINIDC_DRY_RUN` | `true` | `false` to actually create VMs |
| `MINIDC_BIND_ADDRESS` | `127.0.0.1` | Address to bind VM RDP ports |

---

## 18. Running Tests

### Unit and Integration Tests

```bash
# Windows
.\mvn.cmd clean test

# Linux / macOS
./mvnw clean test
```

**Expected output:**
```
Tests run: 29, Failures: 0, Errors: 0  (minidc-server)
Tests run: 11, Failures: 0, Errors: 0  (minidc-agent)
BUILD SUCCESS — Total: 40 tests in ~10s
```

### Live End-to-End API Tests

Requires the server to be running on port 8088:

```powershell
powershell -ExecutionPolicy Bypass -File .\test-live-api.ps1
```

This executes all 10 live API scenarios (health → auth → invite → register → profile → node → offer → marketplace → rate-limit → logout) and reports 100% pass/fail.

### Frontend Build Verification

```bash
cd minidc-ui
npm run build
```

Expected: 45 modules compiled, 0 errors, output in `dist/`.

---

## Document Update Policy

> **This file must be updated whenever:**
> - A new phase is completed and committed
> - A new API endpoint is added or modified
> - Default credentials or configuration change
> - A known limitation is resolved
> - A new limitation or caveat is discovered
>
> **Files to update together as a set:**
> 1. `USAGE.md` ← this file
> 2. `PROJECT_STATUS.md`
> 3. `README.md`
> 4. `docs/api.md` (endpoint reference)

---

*Last updated: Phase 3 completed — 40 tests passing — Phases 1-3 fully verified end-to-end.*
*Repository: https://github.com/Eshwar-B/miniDC.git*

