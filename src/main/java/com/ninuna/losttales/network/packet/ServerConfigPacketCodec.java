package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.config.server.ServerConfigChange;
import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import com.ninuna.losttales.config.server.ServerConfigEntry;
import com.ninuna.losttales.config.server.ServerConfigSnapshot;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * The wire form of a server config entry and of a change, shared by the
 * snapshot and the apply packets. Every string and count is bounded, and
 * a value that does not fit is a decode error rather than a truncation.
 */
final class ServerConfigPacketCodec {

    static final int MAX_ENTRIES = ServerConfigSnapshot.MAX_ENTRIES;
    static final int MAX_LIST_ITEMS = ServerConfigChangeValidator.MAX_LIST_ITEMS;
    static final int MAX_CATEGORY_BYTES = 64;
    static final int MAX_KEY_BYTES = 128;
    /** Four bytes per character is the UTF-8 worst case of the value length. */
    static final int MAX_VALUE_BYTES = ServerConfigChangeValidator.MAX_VALUE_LENGTH * 4;
    static final int MAX_BOUND_BYTES = 32;
    static final int MAX_COMMENT_BYTES = 4096;
    static final int MAX_VALID_VALUES = 64;

    private ServerConfigPacketCodec() {}

    static void writeEntry(ByteBuf buffer, ServerConfigEntry entry) {
        LostTalesPacketCodec.writeUtf8String(buffer, entry.getCategory(), MAX_CATEGORY_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, entry.getKey(), MAX_KEY_BYTES);
        buffer.writeByte(entry.getType().getCode());
        buffer.writeBoolean(entry.isList());
        writeValues(buffer, entry.getValues());
        writeValues(buffer, entry.getDefaults());
        LostTalesPacketCodec.writeUtf8String(buffer, entry.getMinValue(), MAX_BOUND_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, entry.getMaxValue(), MAX_BOUND_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, clip(entry.getComment(),
                MAX_COMMENT_BYTES / 4), MAX_COMMENT_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, entry.getLangKey(), MAX_KEY_BYTES);
        buffer.writeBoolean(entry.isSecret());
        List<String> valid = entry.getValidValues();
        LostTalesPacketCodec.writeCount(buffer, Math.min(valid.size(), MAX_VALID_VALUES),
                MAX_VALID_VALUES, "valid value");
        for (int index = 0; index < valid.size() && index < MAX_VALID_VALUES; index++) {
            LostTalesPacketCodec.writeUtf8String(buffer, valid.get(index), MAX_VALUE_BYTES);
        }
    }

    static ServerConfigEntry readEntry(ByteBuf buffer) {
        String category = LostTalesPacketCodec.readUtf8String(buffer, MAX_CATEGORY_BYTES);
        String key = LostTalesPacketCodec.readUtf8String(buffer, MAX_KEY_BYTES);
        ServerConfigEntry.Type type = ServerConfigEntry.Type.fromCode(buffer.readUnsignedByte());
        if (type == null) {
            throw new LostTalesPacketCodec.DecodeException("unknown config value type");
        }
        boolean list = buffer.readBoolean();
        List<String> values = readValues(buffer);
        List<String> defaults = readValues(buffer);
        String minimum = LostTalesPacketCodec.readUtf8String(buffer, MAX_BOUND_BYTES);
        String maximum = LostTalesPacketCodec.readUtf8String(buffer, MAX_BOUND_BYTES);
        String comment = LostTalesPacketCodec.readUtf8String(buffer, MAX_COMMENT_BYTES);
        String langKey = LostTalesPacketCodec.readUtf8String(buffer, MAX_KEY_BYTES);
        boolean secret = buffer.readBoolean();
        int validCount = LostTalesPacketCodec.readCount(buffer, MAX_VALID_VALUES, "valid value");
        List<String> valid = new ArrayList<String>(validCount);
        for (int index = 0; index < validCount; index++) {
            valid.add(LostTalesPacketCodec.readUtf8String(buffer, MAX_VALUE_BYTES));
        }
        if (category.length() == 0 || key.length() == 0) {
            throw new LostTalesPacketCodec.DecodeException("blank config key");
        }
        return new ServerConfigEntry(category, key, type, list, values, defaults,
                minimum, maximum, comment, langKey, secret, valid);
    }

    static void writeChange(ByteBuf buffer, ServerConfigChange change) {
        LostTalesPacketCodec.writeUtf8String(buffer, change.getCategory(), MAX_CATEGORY_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, change.getKey(), MAX_KEY_BYTES);
        buffer.writeBoolean(change.isList());
        writeValues(buffer, change.getValues());
    }

    static ServerConfigChange readChange(ByteBuf buffer) {
        String category = LostTalesPacketCodec.readUtf8String(buffer, MAX_CATEGORY_BYTES);
        String key = LostTalesPacketCodec.readUtf8String(buffer, MAX_KEY_BYTES);
        boolean list = buffer.readBoolean();
        List<String> values = readValues(buffer);
        if (category.length() == 0 || key.length() == 0) {
            throw new LostTalesPacketCodec.DecodeException("blank config key");
        }
        return new ServerConfigChange(category, key, list, values);
    }

    private static void writeValues(ByteBuf buffer, List<String> values) {
        LostTalesPacketCodec.writeCount(buffer, values.size(), MAX_LIST_ITEMS, "config value");
        for (String value : values) {
            LostTalesPacketCodec.writeUtf8String(buffer, value == null ? "" : value,
                    MAX_VALUE_BYTES);
        }
    }

    private static List<String> readValues(ByteBuf buffer) {
        int count = LostTalesPacketCodec.readCount(buffer, MAX_LIST_ITEMS, "config value");
        List<String> values = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            values.add(LostTalesPacketCodec.readUtf8String(buffer, MAX_VALUE_BYTES));
        }
        return values;
    }

    private static String clip(String value, int maximumCharacters) {
        return value.length() <= maximumCharacters ? value
                : value.substring(0, maximumCharacters);
    }
}
