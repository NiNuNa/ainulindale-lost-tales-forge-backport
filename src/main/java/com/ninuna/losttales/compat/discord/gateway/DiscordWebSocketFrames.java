package com.ninuna.losttales.compat.discord.gateway;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * The WebSocket wire format (RFC 6455) as the Gateway client needs it:
 * a frame written masked, the way a client must; a frame read from the
 * server, which must come unmasked; the frames of a message joined into
 * its text; and the two strings the opening handshake exchanges.
 * Whatever the protocol forbids a server to send is refused as a
 * {@link ProtocolException}. Pure byte work, no socket in sight, so it
 * can be proven without one.
 */
public final class DiscordWebSocketFrames {

    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;
    /** A Gateway payload is far below this; anything larger is not one. */
    public static final int MAX_PAYLOAD_BYTES = 4 * 1024 * 1024;
    /** The most a control frame may carry. */
    public static final int MAX_CONTROL_PAYLOAD_BYTES = 125;
    /** The close code that says the other side broke the protocol. */
    public static final int CLOSE_PROTOCOL_ERROR = 1002;
    private static final String HANDSHAKE_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DiscordWebSocketFrames() {}

    /** One frame as read: its final bit, opcode and unmasked payload. */
    public static final class Frame {
        public final boolean fin;
        public final int opcode;
        public final byte[] payload;

        public Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload == null ? new byte[0] : payload;
        }

        public boolean isControl() {
            return this.opcode >= 0x8;
        }

        /** The close code a close frame carries, or 1005 for none. */
        public int closeCode() {
            return this.opcode == OPCODE_CLOSE && this.payload.length >= 2
                    ? ((this.payload[0] & 0xFF) << 8) | (this.payload[1] & 0xFF) : 1005;
        }

        public String text() {
            return new String(this.payload, UTF_8);
        }
    }

    /** A final, masked frame of the opcode and payload, as a client sends it. */
    public static byte[] encode(int opcode, byte[] payload, Random random) {
        byte[] data = payload == null ? new byte[0] : payload;
        if (data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload too large");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 14);
        out.write(0x80 | (opcode & 0x0F));
        if (data.length <= 125) {
            out.write(0x80 | data.length);
        } else if (data.length <= 0xFFFF) {
            out.write(0x80 | 126);
            out.write((data.length >> 8) & 0xFF);
            out.write(data.length & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int)(((long)data.length >> shift) & 0xFF));
            }
        }
        byte[] mask = new byte[4];
        random.nextBytes(mask);
        out.write(mask, 0, 4);
        byte[] masked = new byte[data.length];
        for (int index = 0; index < data.length; index++) {
            masked[index] = (byte)(data[index] ^ mask[index & 3]);
        }
        out.write(masked, 0, masked.length);
        return out.toByteArray();
    }

    public static byte[] encodeText(String text, Random random) {
        return encode(OPCODE_TEXT, text.getBytes(UTF_8), random);
    }

    /** A close frame carrying the code, and nothing else. */
    public static byte[] encodeClose(int code, Random random) {
        return encode(OPCODE_CLOSE, new byte[] {(byte)(code >> 8), (byte)code}, random);
    }

    /**
     * The next frame off the stream. A server's frame comes unmasked,
     * with no extension bits and a known opcode, and a control frame
     * comes whole and carries at most {@link #MAX_CONTROL_PAYLOAD_BYTES};
     * anything else is a {@link ProtocolException}. Throws at end of
     * stream, and refuses a payload beyond {@link #MAX_PAYLOAD_BYTES}.
     */
    public static Frame read(DataInputStream in) throws IOException {
        int first = in.read();
        int second = in.read();
        if (first < 0 || second < 0) {
            throw new EOFException("connection closed");
        }
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        if ((first & 0x70) != 0) {
            throw new ProtocolException("frame with extension bits nobody agreed on");
        }
        if (!isKnown(opcode)) {
            throw new ProtocolException("frame with the reserved opcode " + opcode);
        }
        if ((second & 0x80) != 0) {
            throw new ProtocolException("masked frame from the server");
        }
        long length = second & 0x7F;
        if (opcode >= 0x8 && (!fin || length > MAX_CONTROL_PAYLOAD_BYTES)) {
            throw new ProtocolException("control frame split up or over "
                    + MAX_CONTROL_PAYLOAD_BYTES + " bytes");
        }
        if (length == 126) {
            length = in.readUnsignedShort();
        } else if (length == 127) {
            length = in.readLong();
        }
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("frame payload of " + length + " bytes refused");
        }
        byte[] payload = new byte[(int)length];
        in.readFully(payload);
        return new Frame(fin, opcode, payload);
    }

    private static boolean isKnown(int opcode) {
        return opcode == OPCODE_CONTINUATION || opcode == OPCODE_TEXT
                || opcode == OPCODE_BINARY || opcode == OPCODE_CLOSE
                || opcode == OPCODE_PING || opcode == OPCODE_PONG;
    }

    /**
     * Joins one connection's data frames into text messages, as RFC 6455
     * lays them out: a text frame, final or followed by continuation
     * frames up to a final one. Anything else in its place is a
     * {@link ProtocolException}: a continuation with no message begun, a
     * new message before the last one ended, and a binary message, which
     * a Gateway asked for JSON never sends. Control frames belong to no
     * message and are passed over.
     */
    public static final class Messages {
        private ByteArrayOutputStream fragments;

        /** The text message the frame completes, or null while one is still being joined. */
        public String accept(Frame frame) throws IOException {
            if (frame.isControl()) {
                return null;
            }
            if (frame.opcode == OPCODE_BINARY) {
                throw new ProtocolException("binary message on a JSON connection");
            }
            if (frame.opcode == OPCODE_TEXT) {
                if (this.fragments != null) {
                    throw new ProtocolException("new message inside a fragmented one");
                }
                if (frame.fin) {
                    return frame.text();
                }
                this.fragments = new ByteArrayOutputStream();
            } else if (this.fragments == null) {
                throw new ProtocolException("continuation frame with no message begun");
            }
            if (this.fragments.size() + frame.payload.length > MAX_PAYLOAD_BYTES) {
                throw new IOException("fragmented message too large");
            }
            this.fragments.write(frame.payload, 0, frame.payload.length);
            if (!frame.fin) {
                return null;
            }
            String text = new String(this.fragments.toByteArray(), UTF_8);
            this.fragments = null;
            return text;
        }
    }

    /** The random key the handshake offers, base64 of sixteen bytes. */
    public static String handshakeKey(Random random) {
        byte[] key = new byte[16];
        random.nextBytes(key);
        return base64(key);
    }

    /** The accept value a server must answer the key with. */
    public static String handshakeAccept(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return base64(sha1.digest((key + HANDSHAKE_GUID).getBytes(UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-1 is not available", missing);
        }
    }

    private static String base64(byte[] bytes) {
        return javax.xml.bind.DatatypeConverter.printBase64Binary(bytes);
    }
}
