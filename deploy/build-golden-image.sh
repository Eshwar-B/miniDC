#!/usr/bin/env bash
set -euo pipefail

echo "=== Building MiniDC Golden OS Image (Ubuntu 24.04 Desktop) ==="

# 1. Download official Ubuntu Noble cloud image
if [ ! -f "noble-server-cloudimg-amd64.img" ]; then
    echo "Downloading Ubuntu 24.04 server cloud image..."
    wget -c https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img
fi

# 2. Prepare qcow2 base and expand capacity to 20G
echo "Preparing base golden.qcow2..."
cp noble-server-cloudimg-amd64.img golden.qcow2
qemu-img resize golden.qcow2 20G

# 3. Customize guest environment with desktop GUI and xrdp
echo "Customizing image packages (XFCE, xrdp, web browser, development tools)..."
virt-customize -a golden.qcow2 \
  --install xfce4,xfce4-goodies,xrdp,firefox,libreoffice,git,build-essential,python3-pip,openjdk-21-jdk,nodejs,npm \
  --run-command 'adduser xrdp ssl-cert' \
  --run-command 'echo xfce4-session > /etc/skel/.xsession' \
  --run-command 'systemctl enable xrdp' \
  --run-command 'cloud-init clean'

# 4. Generate SHA256 checksum for verification
echo "Generating SHA-256 integrity checksum..."
sha256sum golden.qcow2 > golden.qcow2.sha256

# 5. Convert to VDI format for VirtualBox Windows/Linux lenders
echo "Converting golden.qcow2 to golden.vdi for VirtualBox backend..."
qemu-img convert -f qcow2 -O vdi golden.qcow2 golden.vdi
sha256sum golden.vdi > golden.vdi.sha256

echo "=== Golden image build complete! ==="
ls -lh golden.qcow2 golden.vdi

