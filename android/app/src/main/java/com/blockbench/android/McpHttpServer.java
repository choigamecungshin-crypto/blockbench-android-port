package com.blockbench.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class McpHttpServer {

    private static final int MAX_HEADER = 64 * 1024;
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private static final long REQUEST_TIMEOUT_MS = 120_000L;

    public interface Listener {
        void onRequest(String id, String request);
        void onError(String message);
    }

    private final Listener listener;

    private volatile boolean running = false;

    private ServerSocket serverSocket;
    private Thread acceptThread;

    private final ConcurrentHashMap<String, Socket> connections =
        new ConcurrentHashMap<>();

    private final ScheduledExecutorService timeoutExecutor =
        Executors.newScheduledThreadPool(1);

    public McpHttpServer(Listener listener) {
        this.listener = listener;
    }

    public synchronized void start(int port) throws IOException {
        android.util.Log.e("BlockbenchMCP", "START requested port=" + port);

        if (running) {
            android.util.Log.e("BlockbenchMCP", "Already running");
            return;
        }

        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);

        android.util.Log.e("BlockbenchMCP", "Binding 127.0.0.1:" + port);

        ss.bind(new InetSocketAddress(
            InetAddress.getByName("127.0.0.1"),
            port
        ));

        android.util.Log.e(
            "BlockbenchMCP",
            "BOUND local=" + ss.getLocalSocketAddress()
        );

        serverSocket = ss;
        running = true;

        acceptThread = new Thread(
            this::acceptLoop,
            "Blockbench-MCP-Accept"
        );

        acceptThread.start();

        android.util.Log.e(
            "BlockbenchMCP",
            "ACCEPT THREAD STARTED running=" + running
        );
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                Thread requestThread = new Thread(
                    () -> handleConnection(socket),
                    "Blockbench-MCP-Request"
                );

                requestThread.start();

            } catch (IOException e) {
                if (running) {
                    listener.onError(e.toString());
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        String id = UUID.randomUUID().toString();

        android.util.Log.e(
            "BlockbenchMCP",
            "CONNECTION accepted id=" + id +
            " remote=" + socket.getRemoteSocketAddress()
        );

        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(10_000);

            android.util.Log.e(
                "BlockbenchMCP",
                "READING HTTP id=" + id
            );

            String request = readHttpRequest(socket);

            android.util.Log.e(
                "BlockbenchMCP",
                "HTTP READ DONE id=" + id +
                " requestLength=" + (request == null ? -1 : request.length())
            );

            if (request == null) {
                closeSocket(socket);
                return;
            }

            connections.put(id, socket);

            android.util.Log.e(
                "BlockbenchMCP",
                "DISPATCHING REQUEST id=" + id
            );

            listener.onRequest(id, request);

            timeoutExecutor.schedule(() -> {
                Socket old = connections.remove(id);

                if (old != null) {
                    closeSocket(old);
                }
            }, REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            android.util.Log.e(
                "BlockbenchMCP",
                "CONNECTION ERROR id=" + id,
                e
            );

            closeSocket(socket);
        }
    }

    private String readHttpRequest(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();

        ByteArrayOutputStream headerBuffer =
            new ByteArrayOutputStream();

        int matched = 0;

        while (headerBuffer.size() < MAX_HEADER) {
            int b = in.read();

            if (b == -1) {
                return null;
            }

            headerBuffer.write(b);

            if ((matched == 0 && b == '\r') ||
                (matched == 2 && b == '\r')) {
                matched++;

            } else if ((matched == 1 && b == '\n') ||
                       (matched == 3 && b == '\n')) {
                matched++;

                if (matched == 4) {
                    break;
                }

            } else {
                matched = 0;
            }
        }

        if (matched != 4) {
            throw new IOException("HTTP header too large");
        }

        byte[] headerBytes = headerBuffer.toByteArray();

        String header = new String(
            headerBytes,
            StandardCharsets.UTF_8
        );

        int contentLength = getContentLength(header);

        if (contentLength < 0 || contentLength > MAX_BODY) {
            throw new IOException("Invalid Content-Length");
        }

        byte[] body = new byte[contentLength];

        int offset = 0;

        while (offset < contentLength) {
            int read = in.read(
                body,
                offset,
                contentLength - offset
            );

            if (read == -1) {
                throw new IOException(
                    "Unexpected end of HTTP body"
                );
            }

            offset += read;
        }

        ByteArrayOutputStream complete =
            new ByteArrayOutputStream(
                headerBytes.length + body.length
            );

        complete.write(headerBytes);
        complete.write(body);

        return new String(
            complete.toByteArray(),
            StandardCharsets.UTF_8
        );
    }

    private int getContentLength(String header) {
        String[] lines = header.split("\\r\\n");

        for (String line : lines) {
            int colon = line.indexOf(':');

            if (colon < 0) {
                continue;
            }

            String name =
                line.substring(0, colon).trim();

            if (!name.equalsIgnoreCase("Content-Length")) {
                continue;
            }

            try {
                return Integer.parseInt(
                    line.substring(colon + 1).trim()
                );

            } catch (NumberFormatException e) {
                return -1;
            }
        }

        return 0;
    }

    public void respond(String id, String response) {
        if (id == null) {
            return;
        }

        Socket socket = connections.remove(id);

        if (socket == null) {
            return;
        }

        try {
            OutputStream out = socket.getOutputStream();

            byte[] data = response == null
                ? new byte[0]
                : response.getBytes(StandardCharsets.UTF_8);

            out.write(data);
            out.flush();

        } catch (IOException ignored) {

        } finally {
            closeSocket(socket);
        }
    }

    public void closeConnection(String id) {
        if (id == null) {
            return;
        }

        Socket socket = connections.remove(id);

        if (socket != null) {
            closeSocket(socket);
        }
    }

    public synchronized void stop() {
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }

            serverSocket = null;
        }

        for (Socket socket : connections.values()) {
            closeSocket(socket);
        }

        connections.clear();
    }

    public boolean isRunning() {
        return running;
    }

    private void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
