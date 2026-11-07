package com.minidc.agent.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lightweight TCP port forwarder routing incoming RDP traffic to isolated VM endpoints using virtual threads.
 */
public class TcpForwarder implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TcpForwarder.class);

    private final String bindAddress;
    private final int hostPort;
    private final String targetHost;
    private final int targetPort;
    private final ExecutorService executor;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public TcpForwarder(String bindAddress, int hostPort, String targetHost, int targetPort) {
        this.bindAddress = bindAddress;
        this.hostPort = hostPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public synchronized void start() throws IOException {
        if (running) {
            return;
        }
        this.serverSocket = new ServerSocket(hostPort, 50, InetAddress.getByName(bindAddress));
        this.running = true;

        executor.submit(this::acceptLoop);
        log.info("TCP forwarder started on {}:{} -> {}:{}", bindAddress, hostPort, targetHost, targetPort);
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (!running) {
                    break;
                }
                log.warn("Accept error on forwarder {}: {}", hostPort, e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (clientSocket; Socket targetSocket = new Socket(targetHost, targetPort)) {
            Thread t1 = Thread.ofVirtual().start(() -> pipe(clientSocket, targetSocket));
            Thread t2 = Thread.ofVirtual().start(() -> pipe(targetSocket, clientSocket));
            t1.join();
            t2.join();
        } catch (Exception ignored) {
        }
    }

    private void pipe(Socket src, Socket dst) {
        try (InputStream in = src.getInputStream(); OutputStream out = dst.getOutputStream()) {
            in.transferTo(out);
        } catch (IOException ignored) {
        } finally {
            closeQuietly(src);
            closeQuietly(dst);
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
        log.info("TCP forwarder stopped on port {}", hostPort);
    }

    @Override
    public void close() {
        stop();
    }

    public int getHostPort() {
        return hostPort;
    }
}

