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
