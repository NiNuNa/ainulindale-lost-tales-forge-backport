package com.ninuna.losttales.network.packet;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;

/**
 * Bounded packet primitives for the legacy Lost Tales packet formats.
 *
 * Strings retain Forge 1.7.10's ByteBufUtils UTF-8 wire format: a two-byte
 * maximum varint byte length followed by the UTF-8 payload. The bounded read
 * checks the declared length before allocating a byte array.
 */
final class LostTalesPacketCodec {

    static final int MAX_ACTION_BYTES = 32;
    static final int MAX_IDENTIFIER_BYTES = 128;
    // Hard decode ceiling only; the authoritative inventory size is checked at execution.
    static final int MAX_REASONABLE_INVENTORY_SLOT = 4095;
    static final int MIN_BLOCK_Y = 0;
    static final int MAX_BLOCK_Y = 255;
    static final int MAX_BLOCK_COORDINATE = 30000000;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private LostTalesPacketCodec() {}

    static String readUtf8String(ByteBuf buffer, int maximumBytes) {
        if (buffer == null || maximumBytes < 0) {
            throw new DecodeException("invalid string decoder arguments");
        }
        int length = ByteBufUtils.readVarInt(buffer, 2);
        if (length < 0 || length > maximumBytes) {
            throw new DecodeException("string length exceeds packet limit");
        }
        requireReadable(buffer, length);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, UTF_8);
    }

    static void writeUtf8String(ByteBuf buffer, String value, int maximumBytes) {
        if (buffer == null || maximumBytes < 0) {
            throw new IllegalArgumentException("invalid string encoder arguments");
        }
        byte[] bytes = (value == null ? "" : value).getBytes(UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("encoded string exceeds packet limit");
        }
        ByteBufUtils.writeVarInt(buffer, bytes.length, 2);
        buffer.writeBytes(bytes);
    }

    static void requireFinished(ByteBuf buffer) {
        if (buffer == null || buffer.isReadable()) {
            throw new DecodeException("unexpected trailing packet data");
        }
    }

    static int readCount(ByteBuf buffer, int maximum, String fieldName) {
        requireReadable(buffer, 4);
        int count = buffer.readInt();
        if (count < 0 || count > maximum) {
            throw new DecodeException("invalid " + fieldName + " count");
        }
        return count;
    }

    static void writeCount(ByteBuf buffer, int count, int maximum,
                           String fieldName) {
        if (buffer == null || count < 0 || count > maximum) {
            throw new IllegalArgumentException(
                    "invalid " + fieldName + " count");
        }
        buffer.writeInt(count);
    }

    static boolean isUtf8WithinLimit(String value, int maximumBytes) {
        return value != null && maximumBytes >= 0
                && value.getBytes(UTF_8).length <= maximumBytes;
    }

    static void discardRemaining(ByteBuf buffer) {
        if (buffer != null && buffer.isReadable()) {
            buffer.skipBytes(buffer.readableBytes());
        }
    }

    static boolean isValidBlockPosition(int x, int y, int z) {
        return y >= MIN_BLOCK_Y && y <= MAX_BLOCK_Y
                && x >= -MAX_BLOCK_COORDINATE && x <= MAX_BLOCK_COORDINATE
                && z >= -MAX_BLOCK_COORDINATE && z <= MAX_BLOCK_COORDINATE;
    }

    static boolean isReasonableInventorySlot(int slot) {
        return slot >= 0 && slot <= MAX_REASONABLE_INVENTORY_SLOT;
    }

    private static void requireReadable(ByteBuf buffer, int count) {
        if (buffer == null || count < 0 || buffer.readableBytes() < count) {
            throw new DecodeException("truncated packet");
        }
    }

    static final class DecodeException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DecodeException(String message) {
            super(message);
        }
    }
}
