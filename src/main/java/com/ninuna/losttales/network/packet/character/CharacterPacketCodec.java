package com.ninuna.losttales.network.packet.character;

import io.netty.buffer.ByteBuf;

import java.nio.charset.Charset;
import java.util.UUID;

/** Bounded binary helpers used only by the character packet family. */
final class CharacterPacketCodec {

    static final int MAX_NAME_BYTES = 128;
    static final int MAX_IDENTIFIER_BYTES = 128;
    static final int MAX_ERROR_ID_BYTES = 64;
    static final int MAX_CHARACTERS = 9;

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private CharacterPacketCodec() {}

    static String readString(ByteBuf buffer, int maximumBytes) {
        requireReadable(buffer, 2);
        int length = buffer.readUnsignedShort();
        if (length > maximumBytes) {
            throw new DecodeException("string length exceeds limit");
        }
        requireReadable(buffer, length);
        byte[] bytes = new byte[length];
        buffer.readBytes(bytes);
        return new String(bytes, UTF_8);
    }

    static void writeString(ByteBuf buffer, String value, int maximumBytes) {
        byte[] bytes = (value == null ? "" : value).getBytes(UTF_8);
        if (bytes.length > maximumBytes) {
            throw new IllegalArgumentException("encoded string exceeds packet limit");
        }
        buffer.writeShort(bytes.length);
        buffer.writeBytes(bytes);
    }

    static UUID readUuid(ByteBuf buffer) {
        requireReadable(buffer, 16);
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    static UUID readNullableUuid(ByteBuf buffer) {
        requireReadable(buffer, 1);
        return buffer.readBoolean() ? readUuid(buffer) : null;
    }

    static void writeUuid(ByteBuf buffer, UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("UUID must not be null");
        }
        buffer.writeLong(value.getMostSignificantBits());
        buffer.writeLong(value.getLeastSignificantBits());
    }

    static void writeNullableUuid(ByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) {
            writeUuid(buffer, value);
        }
    }

    static void requireFinished(ByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new DecodeException("unexpected trailing packet data");
        }
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
