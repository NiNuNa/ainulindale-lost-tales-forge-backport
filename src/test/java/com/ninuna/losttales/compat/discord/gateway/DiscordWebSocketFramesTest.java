package com.ninuna.losttales.compat.discord.gateway;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Frames the client writes are masked and read back whole at every length. */
public final class DiscordWebSocketFramesTest {

    private final Random random = new Random(7L);

    @Test
    public void aShortTextFrameRoundTripsMasked() throws IOException {
        byte[] frame = DiscordWebSocketFrames.encodeText("{\"op\":1}", this.random);
        assertEquals((byte)0x81, frame[0]);
        assertEquals((byte)(0x80 | 8), frame[1]);
        assertEquals(2 + 4 + 8, frame.length);
        DiscordWebSocketFrames.Frame read = DiscordWebSocketFrames.read(
                new DataInputStream(new ByteArrayInputStream(frame)));
        assertTrue(read.fin);
        assertEquals(DiscordWebSocketFrames.OPCODE_TEXT, read.opcode);
        assertEquals("{\"op\":1}", read.text());
    }

    @Test
    public void mediumAndLongLengthsUseTheirExtendedFields() throws IOException {
        byte[] medium = new byte[300];
        Arrays.fill(medium, (byte)'m');
        byte[] frame = DiscordWebSocketFrames.encode(
                DiscordWebSocketFrames.OPCODE_BINARY, medium, this.random);
        assertEquals((byte)(0x80 | 126), frame[1]);
        assertEquals(300, ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF));
        assertArrayEquals(medium, DiscordWebSocketFrames.read(
                new DataInputStream(new ByteArrayInputStream(frame))).payload);

        byte[] large = new byte[70000];
        Arrays.fill(large, (byte)'l');
        frame = DiscordWebSocketFrames.encode(DiscordWebSocketFrames.OPCODE_BINARY, large,
                this.random);
        assertEquals((byte)(0x80 | 127), frame[1]);
        assertArrayEquals(large, DiscordWebSocketFrames.read(
                new DataInputStream(new ByteArrayInputStream(frame))).payload);
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
            throw new AssertionError("end of stream should be refused");
        } catch (EOFException expected) {
            // The connection is gone.
        }
    }

    @Test
    public void aFragmentIsMarkedNotFinal() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(0x01);
        stream.write(3);
        stream.write("abc".getBytes("UTF-8"));
        stream.write(0x80);
        stream.write(3);
        stream.write("def".getBytes("UTF-8"));
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(stream.toByteArray()));
        DiscordWebSocketFrames.Frame first = DiscordWebSocketFrames.read(in);
        DiscordWebSocketFrames.Frame second = DiscordWebSocketFrames.read(in);
        assertFalse(first.fin);
        assertEquals(DiscordWebSocketFrames.OPCODE_TEXT, first.opcode);
        assertTrue(second.fin);
        assertEquals(DiscordWebSocketFrames.OPCODE_CONTINUATION, second.opcode);
    }

    @Test
    public void theHandshakeAcceptIsTheRfcExample() {
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=",
                DiscordWebSocketFrames.handshakeAccept("dGhlIHNhbXBsZSBub25jZQ=="));
        String key = DiscordWebSocketFrames.handshakeKey(this.random);
        assertEquals(24, key.length());
    }
}
