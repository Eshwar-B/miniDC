#!/bin/bash
set -e
useradd -m -s /bin/bash -G sudo "$USER_NAME"
echo "$USER_NAME:$PASSWORD" | chpasswd
echo "xfce4-session" > "/home/$USER_NAME/.xsession"
chown "$USER_NAME:$USER_NAME" "/home/$USER_NAME/.xsession"
service dbus start || true
xrdp-sesman
exec xrdp -n
