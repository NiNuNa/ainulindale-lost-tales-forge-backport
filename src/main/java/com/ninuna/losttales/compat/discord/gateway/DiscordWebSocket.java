package com.ninuna.losttales.compat.discord.gateway;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One WebSocket connection over TLS, opened with the HTTP upgrade
 * handshake and spoken in text frames: what the Gateway is. Reading
 * blocks for the next text message, answering pings and reassembling
 * fragments on the way, and returns null once the server has closed.
 * Writes are serialised so a heartbeat from another thread never lands
 * inside a frame.
 */
public final class DiscordWebSocket {

    private static final int CONNECT_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 90000;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Random RANDOM = new SecureRandom();

    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final Object writeLock = new Object();
    private volatile int closeCode = -1;
    private volatile boolean closed;

    private DiscordWebSocket(Socket socket, DataInputStream in, OutputStream out) {
        this.socket = socket;
        this.in = in;
        this.out = out;
    }

    /** Opens the connection to a {@code wss://} URI and completes the handshake. */
    public static DiscordWebSocket connect(URI uri) throws IOException {
        if (uri == null || !"wss".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("not a wss:// address: " + uri);
        }
        int port = uri.getPort() < 0 ? 443 : uri.getPort();
        SSLSocket socket = (SSLSocket)SSLSocketFactory.getDefault().createSocket();
        try {
            // The default socket checks that the certificate chain is trusted
            // and nothing else. Asking for the HTTPS identification algorithm
            // is what makes the handshake also check the certificate was
            // issued for the host being connected to; without it any
            // certificate the JVM trusts is accepted for any name, and the
            // bot token travels in the IDENTIFY frame on this socket.
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.connect(new InetSocketAddress(uri.getHost(), port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            socket.startHandshake();
            String path = uri.getRawPath() == null || uri.getRawPath().length() == 0
                    ? "/" : uri.getRawPath();
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }
            String key = DiscordWebSocketFrames.handshakeKey(RANDOM);
            String request = "GET " + path + " HTTP/1.1\r\n"
                    + "Host: " + uri.getHost() + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "User-Agent: DiscordBot (losttales)\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(UTF_8));
            out.flush();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            readHandshakeReply(in, DiscordWebSocketFrames.handshakeAccept(key));
            return new DiscordWebSocket(socket, in, out);
        } catch (IOException failure) {
            closeQuietly(socket);
            throw failure;
        } catch (RuntimeException failure) {
            closeQuietly(socket);
            throw new IOException("websocket handshake failed: " + failure, failure);
        }
    }

    /** Reads the status line and headers; refuses anything but a matching 101. */
    private static void readHandshakeReply(DataInputStream in, String expectedAccept)
            throws IOException {
        String status = readLine(in);
        if (status == null || !status.startsWith("HTTP/1.1 101")) {
            throw new IOException("websocket upgrade refused: " + status);
        }
        String accept = null;
        String line;
        while ((line = readLine(in)) != null && line.length() > 0) {
            int colon = line.indexOf(':');
            if (colon > 0 && "sec-websocket-accept".equals(
                    line.substring(0, colon).trim().toLowerCase(Locale.ROOT))) {
                accept = line.substring(colon + 1).trim();
            }
        }
        if (accept == null || !accept.equals(expectedAccept)) {
            throw new IOException("websocket handshake answered with a wrong accept key");
        }
    }

    /** One CRLF-terminated header line, without touching what follows it. */
    private static String readLine(DataInputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int value;
        while ((value = in.read()) >= 0) {
            if (previous == '\r' && value == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), UTF_8);
            }
            line.write(value);
            previous = value;
            if (line.size() > 8192) {
                throw new IOException("handshake header line too long");
            }
        }
        return null;
    }

    /**
     * The next text message, or null once the server has closed the
     * connection (the close code is then {@link #getCloseCode()}).
     * Pings are answered here; fragments are joined.
     */
    public String readText() throws IOException {
        ByteArrayOutputStream fragments = null;
        while (true) {
            DiscordWebSocketFrames.Frame frame = DiscordWebSocketFrames.read(this.in);
            if (frame.opcode == DiscordWebSocketFrames.OPCODE_CLOSE) {
                this.closeCode = frame.closeCode();
                try {
                    write(DiscordWebSocketFrames.encodeClose(1000, RANDOM));
                } catch (IOException ignored) {
                    // The server is gone either way.
                }
                close();
                return null;
            }
            if (frame.opcode == DiscordWebSocketFrames.OPCODE_PING) {
                write(DiscordWebSocketFrames.encode(
                        DiscordWebSocketFrames.OPCODE_PONG, frame.payload, RANDOM));
                continue;
            }
            if (frame.isControl()) {
                continue;
            }
            if (frame.opcode == DiscordWebSocketFrames.OPCODE_TEXT && frame.fin) {
                return frame.text();
            }
            if (frame.opcode == DiscordWebSocketFrames.OPCODE_BINARY && frame.fin) {
                // The Gateway is asked for JSON; a binary frame is not one of ours.
                continue;
            }
            if (fragments == null) {
                fragments = new ByteArrayOutputStream();
            }
            if (fragments.size() + frame.payload.length
                    > DiscordWebSocketFrames.MAX_PAYLOAD_BYTES) {
                throw new IOException("fragmented message too large");
            }
            fragments.write(frame.payload, 0, frame.payload.length);
            if (frame.fin) {
                String text = new String(fragments.toByteArray(), UTF_8);
                fragments = null;
                return text;
            }
        }
    }

    public void sendText(String text) throws IOException {
        write(DiscordWebSocketFrames.encodeText(text, RANDOM));
    }

    private void write(byte[] frame) throws IOException {
        synchronized (this.writeLock) {
            this.out.write(frame);
            this.out.flush();
        }
    }

    /** The code the server closed with; -1 while open or closed by us. */
    public int getCloseCode() {
        return this.closeCode;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public void close() {
        this.closed = true;
        closeQuietly(this.socket);
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Nothing left to do with it.
        }
    }
}
