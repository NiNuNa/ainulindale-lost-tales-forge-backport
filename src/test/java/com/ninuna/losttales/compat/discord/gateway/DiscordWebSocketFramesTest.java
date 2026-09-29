package com.ninuna.losttales.compat.discord.gateway;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Frames the client writes are masked at every length; frames the server
 * sends are read only as RFC 6455 allows a server to send them, and join
 * into text messages only as the RFC lays messages out.
 */
public final class DiscordWebSocketFramesTest {

    private final Random random = new Random(7L);

    @Test
    public void aShortTextFrameIsWrittenMasked() throws IOException {
        byte[] frame = DiscordWebSocketFrames.encodeText("{\"op\":1}", this.random);
        assertEquals((byte)0x81, frame[0]);
        assertEquals((byte)(0x80 | 8), frame[1]);
        assertEquals(2 + 4 + 8, frame.length);
        assertEquals("{\"op\":1}", new String(unmasked(frame), "UTF-8"));
    }

    @Test
    public void mediumAndLongLengthsUseTheirExtendedFields() {
        byte[] medium = new byte[300];
        Arrays.fill(medium, (byte)'m');
        byte[] frame = DiscordWebSocketFrames.encode(
                DiscordWebSocketFrames.OPCODE_BINARY, medium, this.random);
        assertEquals((byte)(0x80 | 126), frame[1]);
        assertEquals(300, ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF));
        assertArrayEquals(medium, unmasked(frame));

        byte[] large = new byte[70000];
        Arrays.fill(large, (byte)'l');
        frame = DiscordWebSocketFrames.encode(DiscordWebSocketFrames.OPCODE_BINARY, large,
                this.random);
        assertEquals((byte)(0x80 | 127), frame[1]);
        assertArrayEquals(large, unmasked(frame));
    }

    @Test
    public void unmaskedServerFramesAndCloseCodesAreRead() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        // A final text frame from the server: no mask bit, plain payload.
        stream.write(0x81);
        stream.write(5);
        stream.write("hello".getBytes("UTF-8"));
        // A close frame with code 4004.
        stream.write(0x88);
        stream.write(2);
        stream.write(4004 >> 8);
        stream.write(4004 & 0xFF);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(stream.toByteArray()));
        DiscordWebSocketFrames.Frame text = DiscordWebSocketFrames.read(in);
        assertEquals("hello", text.text());
        assertFalse(text.isControl());
        DiscordWebSocketFrames.Frame close = DiscordWebSocketFrames.read(in);
        assertTrue(close.isControl());
        assertEquals(4004, close.closeCode());
        try {
            DiscordWebSocketFrames.read(in);
            fail("end of stream should be refused");
        } catch (EOFException expected) {
            // The connection is gone.
        }
    }

    /** A server must never mask; a frame that is masked is refused. */
    @Test
    public void aMaskedServerFrameIsAProtocolError() throws IOException {
        byte[] frame = DiscordWebSocketFrames.encodeText("hello", this.random);
        assertProtocolError(frame);
    }

    /** A control frame carries at most 125 bytes and is never split up. */
    @Test
    public void aControlFrameOverItsBoundOrSplitUpIsAProtocolError() throws IOException {
        ByteArrayOutputStream big = new ByteArrayOutputStream();
        big.write(0x89);
        big.write(126);
        big.write(0);
        big.write(126);
        big.write(new byte[126]);
        assertProtocolError(big.toByteArray());

        assertProtocolError(new byte[] {(byte)0x09, 0});

        // A ping of exactly 125 bytes is allowed.
        ByteArrayOutputStream edge = new ByteArrayOutputStream();
        edge.write(0x89);
        edge.write(125);
        edge.write(new byte[125]);
        assertEquals(125, DiscordWebSocketFrames.read(new DataInputStream(
                new ByteArrayInputStream(edge.toByteArray()))).payload.length);
    }

    /** No extension was agreed on, so no extension bit and no reserved opcode may appear. */
    @Test
    public void extensionBitsAndReservedOpcodesAreProtocolErrors() throws IOException {
        assertProtocolError(new byte[] {(byte)0xC1, 0});
        assertProtocolError(new byte[] {(byte)0x83, 0});
        assertProtocolError(new byte[] {(byte)0x8B, 0});
    }

    @Test
    public void fragmentsJoinIntoOneTextMessage() throws IOException {
        DiscordWebSocketFrames.Messages messages = new DiscordWebSocketFrames.Messages();
        assertNull(messages.accept(frame(false, DiscordWebSocketFrames.OPCODE_TEXT, "abc")));
        // A control frame may come between the fragments and belongs to none.
        assertNull(messages.accept(frame(true, DiscordWebSocketFrames.OPCODE_PONG, "")));
        assertNull(messages.accept(frame(false, DiscordWebSocketFrames.OPCODE_CONTINUATION,
                "def")));
        assertEquals("abcdefghi", messages.accept(frame(true,
                DiscordWebSocketFrames.OPCODE_CONTINUATION, "ghi")));
        assertEquals("whole", messages.accept(frame(true,
                DiscordWebSocketFrames.OPCODE_TEXT, "whole")));
    }

    /**
     * A binary message is refused whole or in fragments, never read as
     * text; so is a continuation with nothing begun and a new message
     * inside one that has not ended.
     */
    @Test
    public void whatDoesNotFormATextMessageIsAProtocolError() throws IOException {
        assertRefused(new DiscordWebSocketFrames.Messages(),
                frame(false, DiscordWebSocketFrames.OPCODE_BINARY, "{\"op\":0}"));
        assertRefused(new DiscordWebSocketFrames.Messages(),
                frame(true, DiscordWebSocketFrames.OPCODE_BINARY, "{\"op\":0}"));
        assertRefused(new DiscordWebSocketFrames.Messages(),
                frame(true, DiscordWebSocketFrames.OPCODE_CONTINUATION, "tail"));
        DiscordWebSocketFrames.Messages begun = new DiscordWebSocketFrames.Messages();
        assertNull(begun.accept(frame(false, DiscordWebSocketFrames.OPCODE_TEXT, "a")));
        assertRefused(begun, frame(true, DiscordWebSocketFrames.OPCODE_TEXT, "b"));
    }

    @Test
    public void theHandshakeAcceptIsTheRfcExample() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                DiscordWebSocketFrames.handshakeAccept("dGhlIHNhbXBsZSBub25jZQ=="));
        String key = DiscordWebSocketFrames.handshakeKey(this.random);
        assertEquals(24, key.length());
    }

    /** The upgrade's answer is read with a bound on its header lines as well as their length. */
    @Test
    public void theHandshakeAnswerHasBoundedHeaders() throws IOException {
        String key = "dGhlIHNhbXBsZSBub25jZQ==";
        String accept = DiscordWebSocketFrames.handshakeAccept(key);
        StringBuilder answer = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\n")
                .append("Upgrade: websocket\r\nConnection: Upgrade\r\n")
                .append("Sec-WebSocket-Accept: ").append(accept).append("\r\n\r\n");
        DiscordWebSocket.readHandshakeReply(stream(answer.toString()), accept);

        StringBuilder flood = new StringBuilder("HTTP/1.1 101 Switching Protocols\r\n");
        for (int index = 0; index <= DiscordWebSocket.MAX_HANDSHAKE_HEADERS; index++) {
            flood.append("X-Filler-").append(index).append(": x\r\n");
        }
        flood.append("Sec-WebSocket-Accept: ").append(accept).append("\r\n\r\n");
        try {
            DiscordWebSocket.readHandshakeReply(stream(flood.toString()), accept);
            fail("an answer with endless header lines should be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("header lines"));
        }
    }

    private static DataInputStream stream(String text) throws IOException {
        return new DataInputStream(new ByteArrayInputStream(text.getBytes("UTF-8")));
    }

    private static DiscordWebSocketFrames.Frame frame(boolean fin, int opcode, String text)
            throws IOException {
        return new DiscordWebSocketFrames.Frame(fin, opcode, text.getBytes("UTF-8"));
    }

    private static void assertProtocolError(byte[] bytes) throws IOException {
        try {
            DiscordWebSocketFrames.read(new DataInputStream(new ByteArrayInputStream(bytes)));
            fail("the frame should be refused");
        } catch (ProtocolException expected) {
            // The connection is failed as a protocol error.
        }
    }

    private static void assertRefused(DiscordWebSocketFrames.Messages messages,
                                      DiscordWebSocketFrames.Frame frame) throws IOException {
        try {
            messages.accept(frame);
            fail("the frame should be refused");
        } catch (ProtocolException expected) {
            // The connection is failed as a protocol error.
        }
    }

    /** The payload of a frame as a client writes it: masked, the mask after the length. */
    private static byte[] unmasked(byte[] frame) {
        assertTrue("a client's frame is masked", (frame[1] & 0x80) != 0);
        int length = frame[1] & 0x7F;
        int at = 2;
        if (length == 126) {
            length = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
            at = 4;
        } else if (length == 127) {
            long wide = 0L;
            for (int index = 0; index < 8; index++) {
                wide = (wide << 8) | (frame[2 + index] & 0xFF);
            }
            length = (int)wide;
            at = 10;
        }
        byte[] mask = Arrays.copyOfRange(frame, at, at + 4);
        byte[] payload = Arrays.copyOfRange(frame, at + 4, at + 4 + length);
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte)(payload[index] ^ mask[index & 3]);
        }
        assertEquals(at + 4 + length, frame.length);
        return payload;
    }
}
