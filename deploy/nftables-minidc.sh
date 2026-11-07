#!/usr/bin/env bash
set -euo pipefail

echo "Applying MiniDC nftables egress isolation rules..."
nft add table inet minidc
nft add chain inet minidc fwd '{ type filter hook forward priority 0; policy accept; }'

# Allow local DNS/DHCP to virbr-minidc gateway
nft add rule inet minidc fwd iifname "virbr-minidc" ip daddr 192.168.150.1 accept

# Block guest VM from reaching lender LAN private ranges
nft add rule inet minidc fwd iifname "virbr-minidc" ip daddr { 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16 } drop

# Block outbound SMTP and SSH to prevent spam and abuse
nft add rule inet minidc fwd iifname "virbr-minidc" tcp dport { 25, 465, 587, 22 } drop

# Block inter-VM communication
nft add rule inet minidc fwd iifname "virbr-minidc" oifname "virbr-minidc" drop

echo "MiniDC nftables egress rules configured."

