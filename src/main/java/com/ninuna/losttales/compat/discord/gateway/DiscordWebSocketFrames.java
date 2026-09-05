package com.ninuna.losttales.compat.discord.gateway;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * The WebSocket wire format (RFC 6455) as the Gateway client needs it:
 * a frame written masked, the way a client must, and a frame read from
 * the server, which sends unmasked; plus the two strings the opening
 * handshake exchanges. Pure byte work, no socket in sight, so it can be
 * proven without one.
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
     * The next frame off the stream, unmasked. A server frame carries no
     * mask; one that does is unmasked all the same. Throws at end of
     * stream, and refuses a payload beyond the bound.
     */
    public static Frame read(DataInputStream in) throws IOException {
        int first = in.read();
        int second = in.read();
        if (first < 0 || second < 0) {
            throw new EOFException("connection closed");
        }
        boolean fin = (first & 0x80) != 0;
        int opcode = first & 0x0F;
        boolean masked = (second & 0x80) != 0;
        long length = second & 0x7F;
        if (length == 126) {
            length = in.readUnsignedShort();
        } else if (length == 127) {
            length = in.readLong();
        }
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("frame payload of " + length + " bytes refused");
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            in.readFully(mask);
        }
        byte[] payload = new byte[(int)length];
        in.readFully(payload);
        if (mask != null) {
            for (int index = 0; index < payload.length; index++) {
                payload[index] = (byte)(payload[index] ^ mask[index & 3]);
            }
        }
        return new Frame(fin, opcode, payload);
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
