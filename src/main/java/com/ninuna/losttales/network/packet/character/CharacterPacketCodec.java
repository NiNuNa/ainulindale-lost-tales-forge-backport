package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

/**
 * The bounds the character packet family holds its fields to, and the
 * primitives it reads and writes them with.
 *
 * <p>The limits are this family's own; the reading and writing is every
 * family's, and lives in {@link LostTalesPacketCodec}. Nothing here does
 * any work of its own, so a decode primitive can only be hardened in one
 * place — the two families kept a copy each until they drifted over
 * whether a null buffer was a truncated packet.</p>
 */
final class CharacterPacketCodec {

    static final int MAX_NAME_BYTES = 128;
    static final int MAX_DESCRIPTION_BYTES = 1024;
    static final int MAX_IDENTIFIER_BYTES = 128;
    static final int MAX_ERROR_ID_BYTES = 64;
    static final int MAX_CHARACTERS = 9;

    private CharacterPacketCodec() {}

    static String readString(ByteBuf buffer, int maximumBytes) {
        return LostTalesPacketCodec.readShortFramedString(buffer, maximumBytes);
    }

    static void writeString(ByteBuf buffer, String value, int maximumBytes) {
        LostTalesPacketCodec.writeShortFramedString(buffer, value, maximumBytes);
    }

    static UUID readUuid(ByteBuf buffer) {
        return LostTalesPacketCodec.readUuid(buffer);
    }

    static UUID readNullableUuid(ByteBuf buffer) {
        return LostTalesPacketCodec.readNullableUuid(buffer);
    }

    static void writeUuid(ByteBuf buffer, UUID value) {
        LostTalesPacketCodec.writeUuid(buffer, value);
    }

    static void writeNullableUuid(ByteBuf buffer, UUID value) {
        LostTalesPacketCodec.writeNullableUuid(buffer, value);
    }

    static void requireFinished(ByteBuf buffer) {
        LostTalesPacketCodec.requireFinished(buffer);
    }

    /** This family's name for a payload that could not be read. */
    static final class DecodeException
            extends LostTalesPacketCodec.DecodeException {
        private static final long serialVersionUID = 1L;

        DecodeException(String message) {
            super(message);
        }
    }
}
