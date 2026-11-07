#!/usr/bin/env bash
set -euo pipefail

echo "=== MiniDC Agent Linux Installer ==="

# 1. Verify CPU hardware virtualization support
if [ ! -e /dev/kvm ]; then
    echo "ERROR: /dev/kvm device not found! Enable VT-x / AMD-V in your BIOS/UEFI settings." >&2
    exit 1
fi
echo "[OK] CPU virtualization (/dev/kvm) available."

# 2. Install virtualization prerequisites
echo "Installing virtualization and cloud-init packages..."
sudo apt update
sudo apt install -y qemu-kvm libvirt-daemon-system libvirt-clients virtinst cloud-image-utils libguestfs-tools nftables

# 3. Create dedicated system user and directories
echo "Creating minidc service user and storage directories..."
if ! id "minidc" &>/dev/null; then
    sudo useradd -r -m -s /bin/bash minidc
fi

sudo usermod -aG libvirt,kvm minidc
sudo mkdir -p /var/lib/minidc/images /var/lib/minidc/leases /etc/minidc
sudo chown -R minidc:minidc /var/lib/minidc /etc/minidc

# 4. Define and start libvirt network
if [ -f "libvirt-net.xml" ]; then
    sudo virsh net-define libvirt-net.xml || true
    sudo virsh net-autostart minidc-net || true
    sudo virsh net-start minidc-net || true
fi

# 5. Install systemd unit
if [ -f "minidc-agent.service" ]; then
    sudo cp minidc-agent.service /etc/systemd/system/
    sudo systemctl daemon-reload
    sudo systemctl enable minidc-agent.service
    echo "Service installed. Edit /etc/minidc/agent.yml and run: sudo systemctl start minidc-agent"
fi

echo "=== Linux Agent installation complete ==="

