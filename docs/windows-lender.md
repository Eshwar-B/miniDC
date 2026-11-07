# MiniDC on Windows: Lender Setup & Operations Guide

MiniDC allows Windows 10 and 11 laptops to act as compute lenders by spinning up ephemeral Ubuntu desktop VMs inside Oracle VirtualBox. It guarantees that guest activities remain strictly isolated and cannot touch the lender's personal files, operating system, or local network.

---

## 1. System Requirements & Pre-checks

Before installing the agent on Windows, ensure the machine meets these conditions:
1. **Operating System:** 64-bit Windows 10 or 11 (Home, Pro, or Enterprise).
2. **CPU Virtualization:** Intel VT-x or AMD-V must be enabled in UEFI/BIOS.
   - Verify in PowerShell: `(Get-CimInstance Win32_Processor).VirtualizationFirmwareEnabled` must return `True`.
3. **RAM:** Minimum 8 GB recommended (4 GB hosts only a 1-2 GB guest).
4. **Free Disk Space:** At least 25 GB free on drive `C:\` (5 GB for `golden.vdi` + lease overlays + 10 GB headroom).
5. **Memory Integrity / Hyper-V:** If Windows Core Isolation or Hyper-V is running, VirtualBox will use the Windows Hypervisor Platform engine. This runs slightly slower but functions cleanly.

---

## 2. Automated Agent Installation

Run PowerShell as Administrator and execute:
```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force
.\deploy\install-agent.ps1
```

### What the installer does:
1. Validates CPU virtualization, memory, and disk headroom.
2. If missing, downloads Oracle VirtualBox 7.x and runs silent installation (`--silent`).
3. Creates persistent data roots: `C:\ProgramData\MiniDC\images` and `C:\ProgramData\MiniDC\leases`.
4. Copies the golden VirtualBox image to `C:\ProgramData\MiniDC\images\golden.vdi`.

---

## 3. Network Egress & Firewall Isolation

VirtualBox uses NAT networking for guest VMs. To prevent rogue or compromised guests from probing the lender's local area network (LAN), MiniDC automatically installs outbound Windows Defender Firewall rules:

```powershell
# Blocks guest headless VM process from reaching private RFC 1918 subnets
New-NetFirewallRule -DisplayName "minidc-block-lan" -Direction Outbound -Action Block `
  -Program "C:\Program Files\Oracle\VirtualBox\VBoxHeadless.exe" `
  -RemoteAddress 10.0.0.0/8,172.16.0.0/12,192.168.0.0/16

# Blocks outbound SMTP and SSH to prevent spam / outbound scanning
New-NetFirewallRule -DisplayName "minidc-block-smtp" -Direction Outbound -Action Block `
  -Program "C:\Program Files\Oracle\VirtualBox\VBoxHeadless.exe" `
  -Protocol TCP -RemotePort 25,465,587,22
```

For incoming RDP streaming:
A dynamic rule (`minidc-in-<leaseId>`) is added for the forwarded port and immediately purged when the lease terminates.

---

## 4. VirtualBox VM Lifecycle

1. **Overlay Disk:** VirtualBox attaches `golden.vdi` as immutable / multiattach or clones a small differencing disk in `C:\ProgramData\MiniDC\leases\<leaseId>`.
2. **Guest Execution:** Started via `VBoxManage startvm lease-<id> --type headless`.
   - Audio: disabled
   - USB: disabled
   - Clipboard & Drag-and-drop: disabled
   - Shared Folders: none
3. **Termination:** Upon lease expiration:
   ```powershell
   VBoxManage controlvm lease-<id> poweroff
   VBoxManage unregistervm lease-<id> --delete
   Remove-Item -Recurse -Force "C:\ProgramData\MiniDC\leases\<leaseId>"
   ```

---

## 5. Complete Uninstallation

To cleanly purge MiniDC and restore the laptop to its original state:
1. Stop any running `lease-*` VMs in VirtualBox.
2. Remove firewall rules:
   ```powershell
   Remove-NetFirewallRule -DisplayName "minidc-*" -ErrorAction SilentlyContinue
   ```
3. Delete the MiniDC directories:
   ```powershell
   Remove-Item -Recurse -Force "C:\ProgramData\MiniDC"
   ```
4. (Optional) If VirtualBox was only used for MiniDC, uninstall it via **Settings > Apps > Installed apps** or via command line:
   ```powershell
   Get-Package -Name "*VirtualBox*" | Uninstall-Package
   ```

