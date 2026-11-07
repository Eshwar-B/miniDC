# MiniDC – Sandbox Lease Platform: Complete Build Documentation

Working name: MiniDC. Turns idle Linux laptops and PCs into mini datacenters. A lender offers resources; a borrower gets a clean, isolated Linux desktop VM on the lender's machine, streamed to a browser. Version 1.2, November 2026 (adds Windows lenders, HypervisorDriver, VirtualBox backend, DockerDriver demo mode, and PostgreSQL 16 embedded/Docker deployment). This document supersedes the earlier RAM-sharing document.

## 1. Overview

### 1.1 Problem

People with weak machines need more RAM, CPU, and disk for a limited time. Others own idle laptops. Sharing raw RAM over a network is too slow (remote RAM latency is thousands of times worse than local RAM). Sharing a whole VM avoids that: RAM, CPU, and disk stay local to the VM, and only the screen image crosses the network.

### 1.2 Solution

1. Lender installs the agent and sets limits: RAM, CPU cores, disk, time window.
2. Borrower requests a lease from a web portal.
3. Lender approves (or auto-approve rules apply).
4. Agent boots a fresh Ubuntu desktop VM from a golden image with those limits.
5. Borrower sees the desktop in a browser tab (Apache Guacamole, RDP).
6. At expiry or cancel, the VM is destroyed and its disk wiped.

### 1.3 Version 1 scope (decisions already made)

- Trusted groups only: office, college lab, friends, own devices. Invite-only accounts.
- Lender hosts: Linux (KVM/libvirt) and Windows 10/11 (VirtualBox). Guests: Linux only (Ubuntu), so no Windows guest license issue. The agent installs and later removes the VM; it never installs an OS on the lender's disk.
- Cost: everything free and open source. Control server runs on any always-on Linux machine (an old laptop works). LAN first; Tailscale or WireGuard for different networks.
- Spring Boot is the core of the control server and the agent (well above 10% of code).
- Platform-controlled egress so abuse is not traced to lender LAN; full IP masking comes in v2.
- Windows guests only later, with borrower-supplied valid entitlement, after licensing review.

### 1.4 Non-goals for v1

RAM pooling across machines, GPU sharing, public marketplace, payments, macOS lenders, Windows guests, mobile apps.

## 2. Roles

- **Lender:** registers a node, sets limits and schedule, approves or rejects requests, can kill any lease.
- **Borrower:** requests leases, opens the desktop, can end the lease early.
- **Admin:** creates invites, sees all nodes and leases, bans users, reads audit log.
- One user may be lender and borrower (own devices).

## 3. Architecture

```
Borrower browser  <--HTTPS-->  Control Server (Spring Boot) + PostgreSQL
      |                          |  REST + WebSocket (agent connects OUTBOUND)
      |                          v
      |                   Node Agent (Spring Boot, Linux or Windows lender)
      |                          |  HypervisorDriver: libvirt/KVM or VBoxManage
      |                          v
      +--HTTPS--> Guacamole --RDP--> TCP forwarder (host:port) --> Guest VM (xrdp + XFCE)
```

Components:

- **Control Server (`minidc-server`):** auth, invites, nodes, offers, leases, state machine, scheduler, WebSocket hub, audit log, Guacamole connection signing.
- **Node Agent (`minidc-agent`):** runs as a systemd service (Linux) or Windows service (Windows) on each lender and delegates VM work to a HypervisorDriver. Opens a WebSocket to the server (outbound only, so no router setup). Executes VM commands, reports health.
- **Web UI (`minidc-ui`):** React single-page app (Thymeleaf is an acceptable alternative).
- **Guacamole:** `guacd` plus Guacamole web app with the JSON auth extension. Runs on the control server machine via Docker Compose. Browser talks only to Guacamole and the server.
- **Guest VM:** Ubuntu 24.04 LTS desktop (XFCE), xrdp, browser, office suite, dev tools.
- **Network layer:** same LAN (direct), or Tailscale / WireGuard mesh so the control server can reach each node's forwarded port.

## 4. Technology stack

- Java 21 (LTS), Spring Boot 3.3+, Maven.
- Server: Spring Web, Spring Security (JWT), Spring Data JPA, Spring WebSocket, Spring Validation, Spring Scheduling, Spring Boot Actuator, Flyway.
- Database: PostgreSQL 16 (H2 or SQLite for throwaway prototypes).
- Agent: Spring Boot (non-web) with a Java WebSocket client (`org.springframework.web.socket.client`), `ProcessBuilder` for shell commands, Jackson for JSON.
- Virtualization: KVM, QEMU, libvirt, `virt-install`, `cloud-image-utils` (`cloud-localds`), `libguestfs-tools` (`virt-customize`).
- Remote desktop: Apache Guacamole 1.5.x, `guacamole-auth-json`, xrdp.
- Frontend: React + Vite + a fetch wrapper; STOMP or native WebSocket for live status.
- Packaging: `jpackage` or `jlink` bundles a JRE into a `.deb` so lenders need no Java.
- Testing: JUnit 5, Mockito, Testcontainers (PostgreSQL), Spring Boot Test.

* Windows lender backend: VirtualBox 7.x (`VBoxManage`), PowerShell, Windows Firewall, `jpackage --type msi`.
* Hypervisor choice per node: `minidc.hypervisor: libvirt | virtualbox | docker` in the agent config.

- Container runtime for local, demo, and pilot deployments: Docker (Desktop on a laptop, Engine on Linux), Docker Compose v2.
- Build in containers: multi-stage Dockerfiles (`node:20`, `maven:3-eclipse-temurin-21`, `eclipse-temurin:21-jre`), so no JDK, Maven, or Node needs to be installed on the host.
- Third hypervisor backend: `DockerDriver` (desktop container instead of a VM), for demos and trusted groups only.
- Hypervisor choice per node: `minidc.hypervisor: libvirt | virtualbox | docker`.

## 5. Repository layout (Maven multi-module)

```
minidc/
  pom.xml
  minidc-common/      DTOs, enums, message types shared by server and agent
  minidc-server/      control server
    src/main/java/com/minidc/server/
      config/ security/ auth/ user/ node/ offer/ lease/ ws/ guac/ audit/ scheduler/
    src/main/resources/ application.yml, db/migration/V1__init.sql
  minidc-agent/       node agent
    src/main/java/com/minidc/agent/
      config/ ws/ vm/ (HypervisorDriver, LibvirtDriver, VirtualBoxDriver) net/ system/ cmd/
  minidc-ui/          React app
  deploy/
    docker-compose.yml   (postgres, guacd, guacamole)
    build-golden-image.sh
    install-agent.sh, install-agent.ps1 (golden image ships as .qcow2 and .vdi)
```

## 6. Prerequisites and setup

### 6.1 Control server machine

Any always-on machine with Docker. Nothing else is required.

```bash
git clone <repo> && cd minidc
docker compose -f deploy/docker-compose.yml up -d --build     # postgres, guacd, guacamole, server
docker compose -f deploy/docker-compose.yml ps
curl http://localhost:8080/actuator/health
```

Without Docker for the server (optional): JDK 21 and Maven, then `mvn -q -DskipTests package` and `java -jar minidc-server/target/minidc-server-*.jar`.

### 6.2 Linux lender machine (Ubuntu or Debian family)

1. Enable CPU virtualization (VT-x / AMD-V) in BIOS/UEFI. Verify: `egrep -c '(vmx|svm)' /proc/cpuinfo` returns above 0, and `ls /dev/kvm` exists.
2. Install the agent: `sudo apt install ./minidc-agent.deb`. The post-install script installs `qemu-kvm libvirt-daemon-system libvirt-clients virtinst cloud-image-utils libguestfs-tools`, adds the `minidc` user to groups `libvirt` and `kvm`, creates `/var/lib/minidc/{images,leases}`, and downloads or copies the golden image.
3. Open `http://localhost:7070` (agent local UI) or the web portal, enter invite code, register the node, set limits.

The agent only makes outbound connections. No port forwarding on the router.

### 6.3 Golden image (build once, reuse)

`deploy/build-golden-image.sh`:

```bash
wget https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img
cp noble-server-cloudimg-amd64.img golden.qcow2
qemu-img resize golden.qcow2 20G
virt-customize -a golden.qcow2 \
  --install xfce4,xfce4-goodies,xrdp,firefox,libreoffice,git,build-essential,python3-pip,openjdk-21-jdk,nodejs,npm \
  --run-command 'adduser xrdp ssl-cert' \
  --run-command 'echo xfce4-session > /etc/skel/.xsession' \
  --run-command 'systemctl enable xrdp' \
  --run-command 'cloud-init clean'
sha256sum golden.qcow2 > golden.qcow2.sha256
```

The agent verifies the SHA-256 before each use. The golden image is never modified; every lease uses a copy-on-write overlay.

### 6.4 Windows lender machine (any random Windows 10/11 laptop)

The agent never installs Linux on the laptop's disk (no repartitioning, no dual boot, no BitLocker or Secure Boot risk). It installs VirtualBox and runs an Ubuntu **VM**, then removes it at lease end. The owner stays in control: one admin (UAC) prompt at install, and a visible uninstall button.

1. Owner installs `minidc-agent.msi` (built with `jpackage --type msi`, bundles a JRE). Windows service `MiniDCAgent` starts automatically.
2. Pre-checks run before the first offer:
   - 64-bit Windows 10 or 11, any edition (Home included).
   - RAM: 8 GB or more recommended (4 GB hosts only a light 1-2 GB guest).
   - Free disk: golden image (about 5 GB) plus lease disks plus 10 GB headroom.
   - CPU virtualization on: `(Get-CimInstance Win32_Processor).VirtualizationFirmwareEnabled` must be `True`. If `False`, the agent shows BIOS/UEFI instructions. The agent cannot change BIOS itself.
   - Hyper-V / Memory Integrity conflict: if the Windows Hypervisor Platform is active, VirtualBox falls back to the Hyper-V backend (slower but works). Show a warning.
3. If VirtualBox is missing, the agent downloads the installer from Oracle, verifies SHA-256 against a pinned value, and runs it silently (check the flags for your VirtualBox version in its docs; the installer is `VirtualBox-<ver>-Win.exe --silent`). Skip the proprietary Extension Pack: RDP runs inside the guest through xrdp.
4. The agent downloads the golden image as `golden.vdi` (convert on the build machine: `qemu-img convert -f qcow2 -O vdi golden.qcow2 golden.vdi`), verifies the checksum, and stores it in `C:\ProgramData\MiniDC\images`.
5. The agent connects outbound to the control server, as on Linux. No router changes.
6. Uninstall button (agent UI) removes: all `lease-*` VMs, golden image, firewall rules, VirtualBox (only if MiniDC installed it, via `msiexec /x`), and the agent. Nothing is left behind.

Corporate-managed laptops, strict antivirus, or disabled virtualization can block this. Detect and report; do not try to bypass.

## 7. Data model (PostgreSQL)

### 7.0 Database: PostgreSQL 16 (decision)

PostgreSQL 16 is the database for all environments. It is free and open source. MySQL is not used.

Why PostgreSQL:
- Native `uuid` and `UUID[]` match the entities with no extra mapping.
- `TIMESTAMPTZ` avoids time zone mistakes in lease expiry.
- Transactional DDL: a failed Flyway migration rolls back fully.
- `SELECT ... FOR UPDATE` row locking for the capacity check (`@Lock(PESSIMISTIC_WRITE)`).

Rules:
- All times stored in UTC. Entities use `Instant`. Set `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`.
- Flyway is the only way to change the schema (`ddl-auto: validate`).
- Backup: `docker exec minidc-postgres pg_dump -U minidc minidc > backup.sql`. Restore: `docker exec -i minidc-postgres psql -U minidc minidc < backup.sql`.
- Low-RAM hosts (1 GB): `shared_buffers=64MB`, `max_connections=30`.

Where PostgreSQL runs:

| Environment | How | Extra install |
|---|---|---|
| Developer laptop, quick start | Embedded PostgreSQL (7.1), profile `dev` | none beyond Java 21 and Maven |
| Developer laptop or demo with Docker | `postgres:16` container (7.2) | Docker Desktop (already present) |
| Pilot and later | Same compose file on an always-on Linux machine with Docker Engine | Docker Engine |
| Tests | Testcontainers `PostgreSQLContainer<>("postgres:16")`, `@Testcontainers(disabledWithoutDocker = true)` | Docker |

### 7.1 Embedded PostgreSQL (dev profile, nothing installed system-wide)

Real PostgreSQL binaries come inside a Maven dependency and run as a child process of the server. Delete `./data/pg` to remove everything. Check the latest version of the library and its builder API before use.

`minidc-server/pom.xml`:

```xml
<dependency>
  <groupId>io.zonky.test</groupId>
  <artifactId>embedded-postgres</artifactId>
  <version><!-- latest 2.x, check Maven Central --></version>
  <scope>runtime</scope>
</dependency>
```

`config/EmbeddedPgConfig.java`:

```java
@Configuration
@Profile("embedded")
public class EmbeddedPgConfig {
  @Bean(destroyMethod = "close")
  EmbeddedPostgres embeddedPostgres() throws IOException {
    return EmbeddedPostgres.builder()
        .setDataDirectory(Path.of("./data/pg"))
        .setCleanDataDirectory(false)      // keep data between runs
        .setPort(5432)
        .start();
  }
  @Bean
  DataSource dataSource(EmbeddedPostgres pg) {
    return pg.getPostgresDatabase();       // db "postgres", user "postgres"
  }
}
```

Run: `java -jar minidc-server.jar --spring.profiles.active=dev,embedded`. Flyway runs on this DataSource as usual. Profile `embedded` and the Docker Postgres of 7.2 are mutually exclusive.

### 7.2 PostgreSQL in Docker

Settings for the Docker database:

- `spring.datasource.url: jdbc:postgresql://localhost:5432/minidc` (from the host) or `jdbc:postgresql://postgres:5432/minidc` (from another container)
- Maven: `org.postgresql:postgresql` (runtime), `flyway-core`, `flyway-database-postgresql`, `org.testcontainers:postgresql` (test).
- Compose service name `postgres`, container name `minidc-postgres`, volume `pg-data` (data survives `docker compose down`; `docker compose down -v` deletes it).

### 7.3 Schema (`db/migration/V1__init.sql`)

```sql
CREATE TABLE users (
  id UUID PRIMARY KEY, email VARCHAR(200) UNIQUE NOT NULL,
  password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(100),
  role VARCHAR(20) NOT NULL,           -- ADMIN, MEMBER
  banned BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now());

CREATE TABLE invites (
  code VARCHAR(40) PRIMARY KEY, created_by UUID REFERENCES users(id),
  used_by UUID REFERENCES users(id), expires_at TIMESTAMPTZ NOT NULL);

CREATE TABLE nodes (
  id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES users(id),
  name VARCHAR(100) NOT NULL, token_hash VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,         -- ONLINE, OFFLINE, PAUSED
  total_ram_mb INT, total_cores INT, total_disk_gb INT,
  address VARCHAR(100),                -- LAN or Tailscale IP reachable by Guacamole
  last_heartbeat TIMESTAMPTZ);

CREATE TABLE offers (
  id UUID PRIMARY KEY, node_id UUID NOT NULL REFERENCES nodes(id),
  max_ram_mb INT NOT NULL, max_cores INT NOT NULL, max_disk_gb INT NOT NULL,
  max_hours INT NOT NULL,
  window_start TIME, window_end TIME,  -- daily availability, optional
  auto_approve BOOLEAN NOT NULL DEFAULT FALSE,
  allowed_user_ids UUID[],             -- null means any group member
  active BOOLEAN NOT NULL DEFAULT TRUE);

CREATE TABLE leases (
  id UUID PRIMARY KEY, offer_id UUID NOT NULL REFERENCES offers(id),
  borrower_id UUID NOT NULL REFERENCES users(id),
  node_id UUID NOT NULL REFERENCES nodes(id),
  ram_mb INT NOT NULL, cores INT NOT NULL, disk_gb INT NOT NULL,
  requested_hours INT NOT NULL, state VARCHAR(20) NOT NULL,
  vm_name VARCHAR(60), vm_port INT, vm_user VARCHAR(40), vm_password_enc TEXT,
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  fail_reason TEXT);

CREATE TABLE audit_log (
  id BIGSERIAL PRIMARY KEY, at TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id UUID, lease_id UUID, node_id UUID, event VARCHAR(60) NOT NULL, detail TEXT);
```

`vm_password_enc` is AES-256-GCM encrypted with a server key from config.

## 8. Lease lifecycle

States: `REQUESTED` → `APPROVED` → `PROVISIONING` → `ACTIVE` → `EXPIRING` → `ENDED`. Side exits: `REJECTED`, `CANCELLED`, `FAILED`.

- Request validation: borrower not banned; offer active; requested values at or below offer maximums; node `ONLINE`; node has free capacity (sum of active leases plus request is at or below offer maximums); within the daily window if set.
- `REQUESTED` → `APPROVED`: lender clicks approve, or auto-approve if the borrower is allowed. Approval times out after 10 minutes (state `REJECTED`, reason `timeout`).
- `APPROVED` → `PROVISIONING`: server sends `CREATE_VM` to the agent.
- `PROVISIONING` → `ACTIVE`: agent reports `VM_READY` with port. `starts_at` = now, `ends_at` = now + hours. Timeout 5 minutes, then `FAILED` and `DESTROY_VM` sent.
- `ACTIVE` → `EXPIRING`: scheduler, 5 minutes before `ends_at`. Notify both users.
- `EXPIRING` or `ACTIVE` → `ENDED`: at `ends_at`, on early end by borrower or lender, on node offline over 3 minutes, or on ban. Server sends `DESTROY_VM`, waits for `VM_DESTROYED`.
- Lender kill is allowed in any state. Borrower sees reason.

## 9. REST API (control server)

All responses JSON. Auth: `Authorization: Bearer <JWT>`. Roles enforced with `@PreAuthorize`.

Auth and users:

- `POST /api/auth/register` body `{email, password, displayName, inviteCode}`
- `POST /api/auth/login` body `{email, password}` returns `{token}`
- `GET /api/me`
- `POST /api/admin/invites` (ADMIN) returns `{code, expiresAt}`
- `POST /api/admin/users/{id}/ban` (ADMIN)

Nodes and offers (lender):

- `POST /api/nodes` body `{name}` returns `{nodeId, agentToken}` (token shown once)
- `GET /api/nodes/mine`
- `PUT /api/nodes/{id}/pause` and `/resume`
- `POST /api/offers` body `{nodeId, maxRamMb, maxCores, maxDiskGb, maxHours, windowStart, windowEnd, autoApprove, allowedUserIds}`
- `PUT /api/offers/{id}`, `DELETE /api/offers/{id}`

Browsing and leases (borrower):

- `GET /api/offers/available` offers where node is ONLINE and has free capacity
- `POST /api/leases` body `{offerId, ramMb, cores, diskGb, hours}`
- `GET /api/leases/mine`, `GET /api/leases/{id}`
- `POST /api/leases/{id}/end`
- `GET /api/leases/{id}/connect` returns Guacamole URL with signed token (only when ACTIVE)

Lender actions:

- `GET /api/leases/incoming`
- `POST /api/leases/{id}/approve`, `/reject`, `/kill`

Admin: `GET /api/admin/nodes`, `GET /api/admin/leases`, `GET /api/admin/audit`.

Errors: `{timestamp, status, code, message}`. Codes: `CAPACITY_EXCEEDED`, `OUT_OF_WINDOW`, `NODE_OFFLINE`, `FORBIDDEN_USER`, `INVALID_STATE`.

## 10. Agent WebSocket protocol

Endpoint: `wss://<server>/ws/agent`. Header `X-Node-Id` and `X-Agent-Token` verified against `nodes.token_hash` (BCrypt). Messages are JSON `{type, id, payload}`. Reconnect with exponential backoff (1s, 2s, 4s, up to 60s).

Agent to server:

- `REGISTER` `{ramMb, cores, diskGb, address, agentVersion, kvmOk}`
- `HEARTBEAT` every 15s `{freeRamMb, load1, activeVms, diskFreeGb}`
- `VM_READY` `{leaseId, vmName, port}`
- `VM_FAILED` `{leaseId, reason}`
- `VM_DESTROYED` `{leaseId}`
- `VM_STATS` every 30s `{leaseId, cpuPct, memMb}`

Server to agent:

- `CREATE_VM` `{leaseId, ramMb, cores, diskGb, hours, vmUser, vmPassword}`
- `DESTROY_VM` `{leaseId}`
- `PAUSE_NODE`, `RESUME_NODE`

Server marks a node `OFFLINE` when no heartbeat for 45s. On reconnect the agent sends its list of running VMs; the server reconciles: destroy VMs whose lease is not `ACTIVE`, and mark leases `FAILED` if their VM is missing.

## 11. Node agent implementation

### 11.1 Startup checks

- `/dev/kvm` readable; `virsh` reachable (`virsh -c qemu:///system version`).
- Golden image present and checksum valid.
- libvirt network `minidc-net` exists, otherwise create it:

```xml
<network><name>minidc-net</name><forward mode='nat'/>
  <bridge name='virbr-minidc' stp='on' delay='0'/>
  <ip address='192.168.150.1' netmask='255.255.255.0'>
    <dhcp><range start='192.168.150.10' end='192.168.150.250'/></dhcp></ip></network>
```

`virsh net-define net.xml && virsh net-autostart minidc-net && virsh net-start minidc-net`

### 11.2 Local limits enforced by the agent

The agent refuses a `CREATE_VM` if: requested RAM would leave the host with under 25% or 2 GB free (whichever is larger); requested cores exceed total minus 1; disk exceeds free space minus 10 GB; or node is paused. The server also checks, but the agent is the last line.

### 11.3 Provisioning steps for `CREATE_VM`

All commands run through one helper:

```java
@Component
public class Shell {
  public Result run(List<String> cmd, Duration timeout) throws Exception {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    boolean done = p.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
    String out = new String(p.getInputStream().readAllBytes(), UTF_8);
    if (!done) { p.destroyForcibly(); throw new TimeoutException(String.join(" ", cmd)); }
    return new Result(p.exitValue(), out);
  }
  public record Result(int code, String out) {}
}
```

Steps (`LEASE=/var/lib/minidc/leases/<leaseId>`, `NAME=lease-<first8chars>`):

1. `mkdir -p $LEASE`
2. Overlay disk: `qemu-img create -f qcow2 -F qcow2 -b /var/lib/minidc/images/golden.qcow2 $LEASE/disk.qcow2 <diskGb>G`
3. `user-data` file:

```yaml
#cloud-config
hostname: minidc
users:
  - name: <vmUser>
    groups: [sudo]
    shell: /bin/bash
    sudo: ALL=(ALL) NOPASSWD:ALL
    lock_passwd: false
chpasswd: { expire: false }
ssh_pwauth: false
runcmd:
  - echo "<vmUser>:<vmPassword>" | chpasswd
  - systemctl restart xrdp
  - shutdown -P +<hours*60>   # guest powers itself off as a backup timer
```

Also write a minimal `meta-data` file (`instance-id: <leaseId>`).

Step 4, build the seed ISO: `cloud-localds $LEASE/seed.iso $LEASE/user-data $LEASE/meta-data`

Step 5, create and start the VM:

```bash
virt-install --name $NAME --memory <ramMb> --vcpus <cores> \
  --cpu host-passthrough --import --os-variant ubuntu24.04 \
  --disk path=$LEASE/disk.qcow2,format=qcow2,bus=virtio \
  --disk path=$LEASE/seed.iso,device=cdrom \
  --network network=minidc-net,model=virtio \
  --graphics none --noautoconsole
```

6. Wait for IP (poll every 3s up to 180s): `virsh domifaddr $NAME --source lease`. Parse the IPv4 address.
7. Wait until guest TCP 3389 accepts connections (poll up to 120s).
8. Start a TCP forwarder (Java) from a free host port in range 33000-33999 to `<guestIp>:3389`. Bind to the node's LAN or Tailscale address only, not `0.0.0.0` on public interfaces.
9. Send `VM_READY {leaseId, vmName, port}`.

If any step fails: run cleanup (11.5), send `VM_FAILED` with the reason.

TCP forwarder sketch:

```java
void forward(int hostPort, String guestIp) throws IOException {
  ServerSocket ss = new ServerSocket(hostPort, 50, InetAddress.getByName(bindAddr));
  executor.submit(() -> { while (!ss.isClosed()) {
    Socket in = ss.accept(); Socket out = new Socket(guestIp, 3389);
    executor.submit(() -> in.transferTo(out.getOutputStream()));
    executor.submit(() -> out.transferTo(in.getOutputStream())); } });
}
```

(Wrap with try/catch and close both sockets on any error.)

### 11.4 Resource limits and isolation

- RAM, vCPU, disk are fixed by `virt-install` and the qcow2 size.
- Optional CPU cap: `virsh schedinfo $NAME --set vcpu_quota=<cores*100000>`.
- Disk I/O cap: `virsh blkdeviotune $NAME vda --total-iops-sec 2000`.
- No shared folders, no clipboard device from the host, no USB redirect, `--graphics none` (display only through RDP).
- Egress rules in section 14.

### 11.5 Destroy / cleanup for `DESTROY_VM`

```bash
virsh destroy $NAME
virsh undefine $NAME --nvram --remove-all-storage
shred -n 1 -z $LEASE/disk.qcow2   # optional; plain rm is acceptable for v1
rm -rf $LEASE
```

Then close the forwarder socket and send `VM_DESTROYED`. At agent start, destroy any `lease-*` domain not in the server's active list.

### 11.6 Agent configuration

`/etc/minidc/agent.yml`:

```yaml
minidc:
  server-url: wss://control.example.local:8443/ws/agent
  node-id: <uuid>
  agent-token: <token>
  bind-address: 192.168.1.20     # LAN or Tailscale IP
  images-dir: /var/lib/minidc/images
  leases-dir: /var/lib/minidc/leases
  reserve-ram-percent: 25
  port-range: 33000-33999
```

Add `hypervisor: libvirt` (Linux default) or `hypervisor: virtualbox` (Windows default) and `bind-address`. On Windows, `images-dir` is `C:\ProgramData\MiniDC\images` and `leases-dir` is `C:\ProgramData\MiniDC\leases`.

### 11.7 HypervisorDriver abstraction

The lease logic (limits, WebSocket, forwarding, cleanup) is shared. Only VM operations differ per backend:

```java
public interface HypervisorDriver {
  void createVm(VmSpec spec);                 // name, ramMb, cores, diskGb, vmUser, vmPassword, hostPort
  VmInfo waitUntilReady(String vmName, Duration timeout);   // returns guest IP or forwarded port
  void destroyVm(String vmName);              // stop, unregister, delete disks
  List<String> listVms();                     // names starting with "lease-"
  HostInfo hostInfo();                        // total/free RAM, cores, free disk, virtualization ok
}
```

- `LibvirtDriver`: the commands in 11.3 and 11.5 (Linux, fastest).
- `VirtualBoxDriver`: section 11.8 (Windows and Linux).
- Spring selects the bean with `@ConditionalOnProperty("minidc.hypervisor")`. Both drivers must pass the same integration test suite (section 18).
- With VirtualBox NAT port forwarding the Java TCP forwarder from 11.3 is not needed; `waitUntilReady` returns the host port.

### 11.8 VirtualBox driver (Windows lenders)

Set `LEASE=C:\ProgramData\MiniDC\leases\<leaseId>`, `NAME=lease-<first8chars>`. Run through the same `Shell` helper with `VBoxManage` (full path `C:\Program Files\Oracle\VirtualBox\VBoxManage.exe`).

1. Create a per-lease disk from the golden image: `VBoxManage clonemedium disk golden.vdi $LEASE\disk.vdi --variant Standard` then `VBoxManage modifymedium disk $LEASE\disk.vdi --resize <diskGb*1024>`. Faster alternative to test: attach `golden.vdi` as `--mtype multiattach` and let VirtualBox write to a differencing disk (verify behavior on your version before relying on it).
2. Create the cloud-init seed ISO. Bundle `mkisofs.exe` (or use a Java ISO library) with volume label `cidata` and the same `user-data` / `meta-data` as in 11.3. If you prefer, skip cloud-init and set the password by first-boot script.
3. Define and configure the VM:

```
VBoxManage createvm --name %NAME% --ostype Ubuntu_64 --register --basefolder %LEASE%
VBoxManage modifyvm %NAME% --memory <ramMb> --cpus <cores> --cpuexecutioncap 80 ^
  --nic1 nat --natpf1 "rdp,tcp,<bindIP>,<hostPort>,,3389" ^
  --clipboard-mode disabled --draganddrop disabled --audio-driver none --usb off
VBoxManage storagectl %NAME% --name SATA --add sata
VBoxManage storageattach %NAME% --storagectl SATA --port 0 --device 0 --type hdd --medium %LEASE%\disk.vdi
VBoxManage storageattach %NAME% --storagectl SATA --port 1 --device 0 --type dvddrive --medium %LEASE%\seed.iso
VBoxManage startvm %NAME% --type headless
```

4. Wait until TCP `<bindIP>:<hostPort>` accepts a connection (poll up to 180 seconds), then send `VM_READY {leaseId, vmName, port}`.
5. Egress isolation: VirtualBox NAT lets the guest reach the lender's LAN. Block it with Windows Firewall (the exact process name can differ by version; test the rule from inside the guest with a ping to the router):

```powershell
New-NetFirewallRule -DisplayName "minidc-block-lan" -Direction Outbound -Action Block `
  -Program "C:\Program Files\Oracle\VirtualBox\VBoxHeadless.exe" `
  -RemoteAddress 10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
New-NetFirewallRule -DisplayName "minidc-in-<leaseId>" -Direction Inbound -Action Allow `
  -Protocol TCP -LocalPort <hostPort> -RemoteAddress <controlServerIp>
```

6. Destroy:

```
VBoxManage controlvm %NAME% poweroff
VBoxManage unregistervm %NAME% --delete
rmdir /s /q %LEASE%
```

After the VM is gone, remove the lease's inbound firewall rule (`Remove-NetFirewallRule -DisplayName "minidc-in-<leaseId>"`) and send `VM_DESTROYED`.

Step 7, host limits on Windows: leave at least 25% or 2 GB RAM free (whichever is larger), cores total minus 1, disk free minus 10 GB. Read host stats with `Get-CimInstance Win32_OperatingSystem` (`FreePhysicalMemory`) and `Win32_Processor`.

Step 8, on agent start: run `VBoxManage list vms` and destroy every `lease-*` VM not in the server's active list (same reconciliation as 11.5).

### 11.9 DockerDriver (container desktop for demos and trusted groups)

`DockerDriver implements HypervisorDriver`. It starts a desktop **container** (XFCE + xrdp) instead of a VM. It needs only Docker, so it works on a laptop that already runs Docker Desktop and avoids the VirtualBox versus Docker Desktop (WSL2/Hyper-V) conflict from 6.4.

Differences from VM drivers:
- Containers share a kernel, so isolation is weaker than a VM. On Docker Desktop they run inside Docker's own Linux VM, which still separates them from Windows files, but still treat this mode as demo and trusted-group only.
- Starts in seconds, uses less RAM.
- RAM, CPU, and process limits work. A per-container disk size limit is not reliable on Docker Desktop (`--storage-opt size=` needs specific storage drivers). Use a small `tmpfs` or a size-limited volume if disk limits matter, and state this limit in the UI.
- Host limits come from the Docker VM allocation (Docker Desktop settings), not from the laptop's total RAM. Read them with `docker info --format "{{.MemTotal}} {{.NCPU}}"` in `hostInfo()`.

Commands (`NAME=lease-<first8chars>`), run through the `Shell` helper:

```bash
# createVm
docker run -d --name $NAME \
  --memory <ramMb>m --memory-swap <ramMb>m --cpus <cores> --pids-limit 512 \
  --shm-size 1g --security-opt no-new-privileges \
  --label minidc.lease=<leaseId> \
  -p <bindIP>:<hostPort>:3389 \
  -e USER_NAME=<vmUser> -e PASSWORD=<vmPassword> \
  minidc-desktop:latest

# waitUntilReady: poll TCP <probeHost>:<hostPort> until it accepts (max 120 s)
# listVms
docker ps -a --filter "name=lease-" --format "{{.Names}}"
# destroyVm
docker rm -f -v $NAME
# stats (optional, VM_STATS)
docker stats --no-stream --format "{{.CPUPerc}} {{.MemUsage}}" $NAME
```

Rules:
- Port range 33000-33999 as for other drivers; Docker publishes the port, so no Java `TcpForwarder` is needed.
- `probeHost` property: `host.docker.internal` when the agent runs in a container (compose demo), `127.0.0.1` when the agent runs natively.
- `no-new-privileges` can break `sudo` inside the desktop. Remove it if the desktop needs sudo, or drop sudo from the image.
- Add `--cap-drop ALL --cap-add CHOWN --cap-add SETUID --cap-add SETGID ...` only after testing that xrdp still works; start without it.
- On agent start: `docker ps -a --filter name=lease-`, destroy every `lease-*` container not in the server's active list (same reconciliation as 11.5).

Desktop image `deploy/desktop/Dockerfile` (Debian base, because Ubuntu's Firefox is a snap and does not run in containers):

```dockerfile
FROM debian:bookworm-slim
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      xfce4 xfce4-terminal xorgxrdp xrdp dbus-x11 sudo firefox-esr \
      git curl nano ca-certificates python3 openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
EXPOSE 3389
ENTRYPOINT ["/entrypoint.sh"]
```

`deploy/desktop/entrypoint.sh`:

```bash
#!/bin/bash
set -e
useradd -m -s /bin/bash -G sudo "$USER_NAME"
echo "$USER_NAME:$PASSWORD" | chpasswd
echo "xfce4-session" > "/home/$USER_NAME/.xsession"
chown "$USER_NAME:$USER_NAME" "/home/$USER_NAME/.xsession"
service dbus start || true
xrdp-sesman
exec xrdp -n
```

Build once: `docker build -t minidc-desktop:latest deploy/desktop`. Test it alone first: `docker run --rm -p 33890:3389 -e USER_NAME=demo -e PASSWORD=demo12345 minidc-desktop:latest`, then connect with any RDP client to `localhost:33890`. Expect to tune packages and xrdp config; this image is untested boilerplate.

Agent config for Docker mode:

```yaml
minidc:
  hypervisor: docker
  docker-image: minidc-desktop:latest
  probe-host: host.docker.internal
  bind-address: 0.0.0.0        # demo only; use a LAN/Tailscale IP otherwise
  port-range: 33000-33999
```

## 12. Control server implementation

### 12.1 Key classes

- `LeaseController`, `OfferController`, `NodeController`, `AuthController`, `AdminController` (REST).
- `LeaseService`: validation, state transitions, capacity calculation.
- `AgentSocketHandler extends TextWebSocketHandler`: maps `nodeId` to `WebSocketSession`, parses messages, calls `LeaseService`.
- `AgentCommandSender`: sends `CREATE_VM`, `DESTROY_VM`.
- `LeaseScheduler`: `@Scheduled` jobs.
- `GuacService`: builds the signed connection payload.
- `SecurityConfig`: JWT filter, BCrypt, CORS for the UI origin.
- `AuditService`: writes `audit_log`.

### 12.2 Entity and state transition

```java
@Entity @Table(name = "leases")
public class Lease {
  @Id UUID id; UUID offerId, borrowerId, nodeId;
  int ramMb, cores, diskGb, requestedHours;
  @Enumerated(EnumType.STRING) LeaseState state;
  String vmName, vmUser; Integer vmPort; String vmPasswordEnc;
  Instant startsAt, endsAt, createdAt; String failReason;
}

@Service
public class LeaseService {
  private static final Map<LeaseState, Set<LeaseState>> ALLOWED = Map.of(
    REQUESTED, Set.of(APPROVED, REJECTED, CANCELLED),
    APPROVED, Set.of(PROVISIONING, CANCELLED),
    PROVISIONING, Set.of(ACTIVE, FAILED),
    ACTIVE, Set.of(EXPIRING, ENDED),
    EXPIRING, Set.of(ENDED));

  @Transactional
  public Lease transition(UUID id, LeaseState to, String reason) {
    Lease l = repo.findById(id).orElseThrow();
    if (!ALLOWED.getOrDefault(l.getState(), Set.of()).contains(to))
      throw new InvalidStateException(l.getState(), to);
    l.setState(to); if (to == FAILED) l.setFailReason(reason);
    audit.log("LEASE_" + to, l); return repo.save(l);
  }
}
```

Use a pessimistic lock (`@Lock(PESSIMISTIC_WRITE)`) on the offer row when computing capacity so two concurrent requests cannot both succeed.

### 12.3 Scheduler

```java
@Scheduled(fixedDelay = 15000)
void tick() {
  nodes.markOfflineIfNoHeartbeat(Duration.ofSeconds(45));
  leases.findByStateAndEndsAtBefore(ACTIVE, now().plus(5, MINUTES)).forEach(l -> svc.transition(l.getId(), EXPIRING, null));
  leases.findByStateInAndEndsAtBefore(List.of(ACTIVE, EXPIRING), now()).forEach(this::endLease);
  leases.findByStateAndCreatedAtBefore(REQUESTED, now().minus(10, MINUTES)).forEach(this::rejectTimeout);
  leases.findByStateAndUpdatedAtBefore(PROVISIONING, now().minus(5, MINUTES)).forEach(this::failTimeout);
  leases.findActiveOnOfflineNodesLongerThan(Duration.ofMinutes(3)).forEach(this::endLease);
}
```

### 12.4 Server configuration (`application.yml`)

```yaml
server: { port: 8443 }
spring:
  datasource: { url: jdbc:postgresql://localhost:5432/minidc, username: minidc, password: change-me }
  jpa: { hibernate: { ddl-auto: validate } }
  flyway: { enabled: true }
minidc:
  jwt-secret: <32+ random bytes, base64>
  jwt-hours: 12
  data-key: <32 byte base64 key for AES-GCM>
  guac-base-url: https://control.example.local/guacamole
  guac-json-secret: <32 hex chars, same as guacamole-auth-json secret>
  max-active-leases-per-user: 1
```

## 13. Remote desktop (Guacamole + xrdp)

`deploy/docker-compose.yml` runs `postgres`, `guacd` (image `guacamole/guacd`) and `guacamole` (image `guacamole/guacamole`) with environment `JSON_SECRET_KEY=<hex secret>` and the JSON auth extension enabled via `EXTENSIONS=auth-json`.

Flow for `GET /api/leases/{id}/connect`:

1. Check caller is the borrower and lease is `ACTIVE`.
2. Build a JSON payload with an expiry set to the lease end:

```json
{ "username": "<borrowerEmail>", "expires": <epoch_ms of ends_at>,
  "connections": { "vm": { "protocol": "rdp", "parameters": {
    "hostname": "<node.address>", "port": "<vmPort>",
    "username": "<vmUser>", "password": "<decrypted password>",
    "security": "any", "ignore-cert": "true",
    "resize-method": "display-update", "enable-wallpaper": "false",
    "disable-copy": "true", "disable-paste": "false" } } } }
```

3. Sign with HMAC-SHA256 using `guac-json-secret`, then encrypt with AES-128-CBC (zero IV) using the same key, Base64 the result (format required by `guacamole-auth-json`).
4. POST `data=<base64>` to `<guac-base-url>/api/tokens` and return `<guac-base-url>/#/client/<id>?token=<authToken>`.

The password never reaches the browser in plain form, and the token dies at lease end. Drive redirection, printing, and audio are off.

## 14. Networking and egress control

- Guests live in `minidc-net` (NAT, 192.168.150.0/24). They are not bridged to the lender's LAN.
- Block the guest from reaching the lender's LAN, the host itself, and other VMs. On the lender host (nftables):

```bash
nft add table inet minidc
nft add chain inet minidc fwd '{ type filter hook forward priority 0; policy accept; }'
nft add rule inet minidc fwd iifname "virbr-minidc" ip daddr { 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 } drop
nft add rule inet minidc fwd iifname "virbr-minidc" tcp dport { 25, 465, 587, 22 } drop
nft add rule inet minidc fwd iifname "virbr-minidc" oifname "virbr-minidc" drop
```

(Add an accept rule for the libvirt DNS/DHCP at 192.168.150.1 before the first drop.)

- The agent installs these rules at startup and removes them on uninstall.
- Internet traffic exits through the lender's connection in v1 (trusted groups only; tell lenders this clearly during registration).
- v2 upgrade: route guest egress through a WireGuard exit node run by the group, so abuse is not traced to the lender's IP.
- Cross-network access: join control server and nodes to one Tailscale tailnet (or WireGuard mesh). Set `node.address` to the node's tailnet IP.

Windows lenders: guests use VirtualBox NAT (10.0.2.0/24 inside the VM, not bridged). The Windows Firewall rules in 11.8 block guest traffic to private ranges and allow inbound only from the control server on the forwarded port. Also block outbound SMTP/SSH from the VM process by adding ports 25, 465, 587, 22 to a second block rule. Remove every rule on lease end and on uninstall. Test from inside the guest: ping to the router and to another LAN host must fail.

## 15. Security

- Registration needs an invite code. Passwords hashed with BCrypt (cost 12). JWT HS256, 12 hour expiry.
- Agent tokens: 32 random bytes, shown once, stored BCrypt-hashed. Rotate via re-register.
- TLS everywhere (self-signed CA on LAN, Let's Encrypt on a public domain).
- Lender protection: KVM hardware isolation, no host shares, no clipboard channel from host, no USB, LAN blocked, SMTP/SSH-out blocked, hard RAM/CPU caps, agent runs as a non-root user (`minidc`), lender kill switch in the local UI and API.
- Borrower privacy warning shown at lease start: the lender controls the host and can inspect the VM. Do not enter passwords for personal accounts or handle sensitive data.
- Rate limit login and lease requests (Bucket4j or a simple filter). Account lockout after 5 failed logins for 5 minutes.
- Audit every state change and admin action. Retain 90 days.
- Dependency checks: Dependabot or OWASP Dependency-Check in CI.
- Ubuntu guest auto-updates are on in the golden image; rebuild the golden image monthly.

* Windows lender consent and safety: install needs the owner's admin approval; the agent shows a persistent tray icon while a lease runs, with a Stop button; it never touches the owner's files, accounts, or disk partitions; no clipboard, drag and drop, USB, or shared folders on the VM.
* Supply chain on Windows: sign the MSI and agent binaries (code-signing certificate; unsigned installers trigger SmartScreen warnings), and pin the SHA-256 of the VirtualBox installer and golden image.

- Docker mode: weaker isolation than VM mode (shared kernel). Use only for demos and trusted groups. Never mount the Docker socket on machines used by strangers. Run lease containers with `--memory`, `--cpus`, `--pids-limit`, `--security-opt no-new-privileges`, and no host mounts.
- Compose secrets: replace every `change-me` value and both 32+ character secrets before any non-local deployment. Keep real values in an `.env` file excluded from git.
- Postgres port 5432 should not be published beyond localhost in non-demo setups (use `127.0.0.1:5432:5432`).

## 16. Web UI pages

- **Login / Register (invite code).**
- **Lender dashboard:** my nodes with status and heartbeat, create offer form (sliders for RAM, cores, disk, hours, daily window, auto-approve), incoming request list with approve and reject, active leases with live CPU/RAM chart and a red Kill button.
- **Borrower marketplace:** available offers with free capacity and node status; request form (RAM, cores, disk, hours) with the offer maximums as slider limits.
- **My leases:** state badge, countdown, Open Desktop (opens `/connect` link), End Lease.
- **Admin:** invites, users, nodes, leases, audit log.
- Live updates: UI subscribes to `/ws/ui` (STOMP) for lease state changes and node status; fall back to 10 second polling.

## 17. Packaging and deployment

- Server: `mvn -pl minidc-server -am package` produces `minidc-server.jar`; run as a systemd service with `Restart=always`.
- Agent: `jpackage --type deb --input target --main-jar minidc-agent.jar --name minidc-agent --app-version 1.0.0 --linux-package-deps "qemu-kvm,libvirt-daemon-system,virtinst,cloud-image-utils,libguestfs-tools"`. The post-install script performs the steps in 6.2 and installs `minidc-agent.service` (`User=minidc`, `Restart=always`).
- UI: `npm run build`, serve `dist/` from Spring Boot static resources (`src/main/resources/static`) so one jar serves both.
- Uninstall: stop service, destroy `lease-*` domains, remove nft table and `/var/lib/minidc`.

* Windows agent: `jpackage --type msi --input target --main-jar minidc-agent.jar --name minidc-agent --win-service` (verify the jpackage flags for your JDK version; a service wrapper such as WinSW or Apache Commons Daemon is the usual alternative). The MSI installs the service `MiniDCAgent`, a tray UI, and `install-agent.ps1` pre-checks.
* Windows uninstall: stop service, destroy `lease-*` VMs, remove firewall rules (`Remove-NetFirewallRule -DisplayName "minidc-*"`), delete `C:\ProgramData\MiniDC`, optionally uninstall VirtualBox if MiniDC installed it.
* Build the golden image once on Linux, then export both formats: `golden.qcow2` (KVM) and `golden.vdi` (VirtualBox).

## 18. Testing plan

- **Unit:** state machine transitions, capacity calculation, limit checks, password encryption.
- **Integration (Testcontainers):** full REST flow request → approve → active using a fake agent that replies over the WebSocket.
- **Agent tests:** `Shell` wrapper mocked; run a real VM test on a KVM machine (`@EnabledIf` for `/dev/kvm`).
- **End to end:** two laptops on a LAN. Create lease, open desktop, type, end lease, verify the VM and files are gone.
- **Failure tests:** kill agent mid-lease, pull network cable, lender shuts down, server restarts, expiry under load, two simultaneous requests for last capacity.
- **Security tests:** guest ping to LAN IPs fails; guest cannot reach `192.168.x.x`; wrong agent token rejected; expired Guacamole token rejected; borrower cannot open another user's lease.
- **Performance targets:** VM ready in under 60 seconds (SSD); desktop latency under 100 ms on LAN; server handles 100 nodes and 50 concurrent leases on a small machine.

* **Windows lender tests:** run the same end-to-end flow on Windows 10 Home, Windows 11 Pro, and one machine with Hyper-V / Memory Integrity on. Verify: pre-check messages when virtualization is off, silent VirtualBox install, lease start under 90 seconds on SSD, RDP through the NAT forward, firewall LAN block, clean uninstall (no `VBoxManage list vms` entries, no folders, no firewall rules left).
* **Driver contract tests:** `LibvirtDriver` and `VirtualBoxDriver` run one shared test suite against the `HypervisorDriver` interface.
* **Owner experience:** lease running while the owner browses, watches video, and works; confirm the host stays responsive at the default caps.

- **Docker driver tests:** shared `HypervisorDriver` contract suite also runs against `DockerDriver` (needs Docker). Verify: container starts, port opens, memory limit visible in `docker inspect`, destroy leaves no container or volume, restart reconciliation removes orphan `lease-*` containers.
- **Compose smoke test:** `docker compose up -d --build`, then `curl /actuator/health` returns `UP`, register and login through the API, create node, offer, lease through an agent test double.
- **Embedded Postgres test:** start with `--spring.profiles.active=dev,embedded`; Flyway migration applies; restart keeps data in `./data/pg`.
- **Demo rehearsal:** run the full script in 21.5 twice on a clean machine; time each step; record any manual fix.

## 19. Build plan

**Phase 1 (week 1-2): VM engine by hand.** Build the golden image, create a VM with the commands in 11.3 manually, connect with an RDP client. Write the Java `Shell`, `VmManager.create/destroy`, and the forwarder; test from a `main` method.

**Phase 2 (week 3-4): control server core.** Spring Boot project, Flyway schema, auth with invites, node, offer, lease endpoints, state machine, unit tests.

**Phase 3 (week 5-6): agent link.** WebSocket protocol, register, heartbeat, `CREATE_VM` / `DESTROY_VM` wired end to end, reconciliation on reconnect, scheduler.

**Phase 4 (week 7-8): desktop in browser.** Docker Compose for Guacamole, `GuacService`, `/connect` endpoint, egress rules, limits.

**Phase 5 (week 9-10): UI.** React pages, live updates, countdown, kill switch.

**Phase 6 (week 11-12): hardening and packaging.** `.deb` build, install script, security tests, docs, pilot with 3-5 real machines.

**Phase 6b (week 12-13): Docker demo mode.** `DockerDriver`, `minidc-desktop` image, Dockerfiles for server and agent, `docker-compose.yml` with the `demo` profile, embedded Postgres dev profile, demo script rehearsal, short screen recording of the demo.

**Phase 7 (week 13-15): Windows lenders.** Extract the `HypervisorDriver` interface (do this early, in phase 1, even if only `LibvirtDriver` exists), implement `VirtualBoxDriver`, Windows pre-checks, firewall rules, MSI packaging and signing, uninstall flow, Windows test matrix, pilot on 3-5 random Windows laptops.

## 20. Limits, legal notes, and future work

- Needs CPU virtualization and enough spare RAM. Old laptops with 4 GB RAM run only light 1-2 GB guests; an SSD is strongly recommended.
- Lender going offline ends the lease; borrower files in the VM are lost. Tell borrowers to save work elsewhere (git, cloud storage, or a mounted network share in a later version).
- Internet traffic exits from the lender's IP in v1; use only with trusted people.
- Windows guests are excluded in v1. A Windows VM needs a valid license and Microsoft's remote-access terms; add only as bring-your-own-entitlement after a licensing review. This document is not legal advice.
- Future: WireGuard exit node for egress masking, Windows and macOS lenders, GPU passthrough, persistent encrypted home volumes, quotas and credits, public marketplace with identity checks and abuse handling, optional remote-execution mode (run a job, return the result) for batch tasks.

* Windows lender limits: needs CPU virtualization enabled in BIOS/UEFI (the agent cannot change it); managed or locked-down laptops may block installs; VirtualBox is slower than KVM; Memory Integrity or Hyper-V can force a slower VirtualBox backend; 4 GB laptops host only a light guest.
* A random laptop is usable only with the owner's consent and admin approval. The agent installs a VM, not an OS on the disk, and removes everything at the end of the lease or on uninstall.
* Unverified items to test early: VirtualBox silent-install flags, `clonemedium`/`multiattach` behavior, firewall rule effectiveness for the VM process, Guacamole JSON-auth encryption, libvirt and nftables commands, jpackage `--win-service` support on your JDK, WSL2 nesting (not used in v1).

- Docker mode limits: weaker isolation than a VM; no reliable per-container disk cap on Docker Desktop; resources are capped by the Docker VM allocation, not the whole laptop; the agent container needs the Docker socket (high privilege).
- Docker Desktop licensing: free for personal use, education, non-commercial open source, and small businesses (under 250 employees and under $10M annual revenue); larger commercial use needs a paid plan. Docker Engine on Linux has no such restriction. Re-check Docker's current terms before commercial use.
- Docker Desktop on Windows uses WSL2 or Hyper-V; if the same laptop is also a VirtualBox lender, VirtualBox may fall back to the slower Hyper-V backend.
- Unverified, test early: embedded-postgres library version and builder API, Guacamole image variable names, the `minidc-desktop` image (xrdp inside containers needs tuning), `host.docker.internal` reachability from the Guacamole container on your OS, cloudflared quick-tunnel terms.

## 21. Deployment options and demo

### 21.1 Options at a glance

| Stage | Where | How |
|---|---|---|
| One-laptop demo | Your laptop, Docker Desktop | `docker compose --profile demo up --build` (server, Postgres, Guacamole, agent with DockerDriver) |
| Two-device demo | Your laptop = control server; second laptop = lender | Same Wi-Fi. Second laptop installs the native agent (VirtualBox or KVM driver) or runs the agent container |
| Trusted-group pilot | Old Linux machine, Docker Engine | Same compose file without the `demo` profile; Tailscale if networks differ |
| Later | Free-tier VM or low-cost VPS | Same compose file; add TLS |

### 21.2 Server Dockerfile (`minidc-server/Dockerfile`, build context = repo root)

```dockerfile
FROM node:20 AS ui
WORKDIR /ui
COPY minidc-ui/ .
RUN npm install && npm run build

FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
COPY --from=ui /ui/dist minidc-server/src/main/resources/static
RUN mvn -q -DskipTests -pl minidc-server -am package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/minidc-server/target/minidc-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Xmx256m","-XX:+UseSerialGC","-jar","/app/app.jar"]
```

### 21.3 Agent Dockerfile (`minidc-agent/Dockerfile`, demo mode only)

```dockerfile
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -DskipTests -pl minidc-agent -am package

FROM eclipse-temurin:21-jre
COPY --from=docker:cli /usr/local/bin/docker /usr/local/bin/docker
WORKDIR /app
COPY --from=build /src/minidc-agent/target/minidc-agent-*.jar agent.jar
ENTRYPOINT ["java","-Xmx128m","-jar","/app/agent.jar"]
```

### 21.4 `deploy/docker-compose.yml`

Check the Guacamole image variable names against the image documentation before first use.

```yaml
services:
  postgres:
    image: postgres:16
    container_name: minidc-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: minidc
      POSTGRES_USER: minidc
      POSTGRES_PASSWORD: change-me
    command: ["postgres", "-c", "shared_buffers=64MB", "-c", "max_connections=30"]
    ports: ["5432:5432"]
    volumes: ["pg-data:/var/lib/postgresql/data"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U minidc"]
      interval: 5s
      retries: 10

  guacd:
    image: guacamole/guacd
    restart: unless-stopped

  guacamole:
    image: guacamole/guacamole
    restart: unless-stopped
    depends_on: [guacd]
    environment:
      GUACD_HOSTNAME: guacd
      EXTENSIONS: auth-json
      JSON_SECRET_KEY: "00112233445566778899aabbccddeeff"
    ports: ["8081:8080"]

  server:
    build: { context: .., dockerfile: minidc-server/Dockerfile }
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/minidc
      SPRING_DATASOURCE_USERNAME: minidc
      SPRING_DATASOURCE_PASSWORD: change-me
      SPRING_PROFILES_ACTIVE: dev
      MINIDC_GUAC_SECRET: "00112233445566778899aabbccddeeff"
      MINIDC_JWT_SECRET: "change-me-change-me-change-me-32"
    ports: ["8080:8080"]

  desktop-image:
    profiles: ["demo"]
    build: { context: ./desktop }
    image: minidc-desktop:latest
    entrypoint: ["true"]

  agent:
    profiles: ["demo"]
    build: { context: .., dockerfile: minidc-agent/Dockerfile }
    restart: unless-stopped
    depends_on: [server, desktop-image]
    extra_hosts: ["host.docker.internal:host-gateway"]
    environment:
      MINIDC_SERVER_URL: ws://server:8080/ws/agent
      MINIDC_NODE_ID: "<node id from portal>"
      MINIDC_AGENT_TOKEN: "<token from portal>"
      MINIDC_HYPERVISOR: docker
      MINIDC_PROBE_HOST: host.docker.internal
      MINIDC_BIND_ADDRESS: 0.0.0.0
      MINIDC_DRY_RUN: "false"
    volumes: ["/var/run/docker.sock:/var/run/docker.sock"]

volumes:
  pg-data:
```

Notes:
- The agent container mounts the Docker socket so it can start sibling desktop containers. That socket gives near-root control of Docker. Use this only on a demo machine you control, never for strangers.
- In demo mode the node `address` registered in the portal is `host.docker.internal`, so the Guacamole container reaches the published port on the host.
- First run order: start the stack without the agent, register the lender node in the portal to get the node ID and token, put them into the compose environment, then run `docker compose --profile demo up -d agent`.

### 21.5 One-laptop demo script

1. `docker compose -f deploy/docker-compose.yml up -d --build` and open `http://localhost:8080`.
2. Log in as the dev admin and create two invite codes (Admin page).
3. Window A: register as lender with invite 1, create a node, copy the node ID and token, start the agent (`--profile demo`), wait for status ONLINE.
4. Lender: create an offer (for example 2048 MB RAM, 2 cores, 5 GB disk, 1 hour, manual approval).
5. Window B (another browser profile, phone on the same Wi-Fi, or incognito): register as borrower with invite 2.
6. Borrower: pick the offer and request a lease. Lender: approve.
7. Borrower: click Open Desktop. The Linux desktop appears in the browser.
8. Prove isolation and limits: `docker ps` shows `lease-...` with its memory limit; `docker stats --no-stream` shows usage; open a terminal in the desktop and run `free -m` to show the cap.
9. End the lease (or let it expire). Run `docker ps -a` again: the container is gone.

### 21.6 Showing the demo to someone on another network (optional)

Run a temporary tunnel container: `docker run --rm cloudflare/cloudflared tunnel --url http://host.docker.internal:8080`. It prints a temporary public URL and needs no router changes. Check Cloudflare's current free terms. Anyone with the URL can reach the portal, so keep registration invite-only, share the URL briefly, and stop the container after the demo.

### 21.7 Moving to the real VM mode

Keep the same server and Postgres. On each lender, replace the agent container with the native agent (`.deb` or `.msi`) using `hypervisor: libvirt` or `hypervisor: virtualbox`. No server code changes; only the driver differs.

### Document history

- 1.0: Linux lenders, KVM/libvirt, trusted groups, $0 stack.
- 1.1: Windows lenders through VirtualBox, `HypervisorDriver` abstraction, Windows setup, firewall, packaging, tests, and build plan; fixed merged steps in 11.3.
- 1.2: PostgreSQL 16 fixed as the database (embedded dev profile, Docker, Testcontainers), `DockerDriver` for demos, Docker Compose deployment, Dockerfiles, demo script, security, testing, and plan updates.

## 22. Repository Remote & Git Setup

```bash
echo "# miniDC" >> README.md
git init
git add README.md
git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/Eshwar-B/miniDC.git
git push -u origin main
```
