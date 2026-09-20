from pathlib import Path

p = Path("android/app/src/main/java/com/blockbench/android/McpHttpServer.java")
s = p.read_text()

start = s.index("    private void handleConnection(Socket socket) {")
end = s.index("\n    private String readHttpRequest", start)

new = '''    private void handleConnection(Socket socket) {
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
'''

s = s[:start] + new + s[end:]
p.write_text(s)
