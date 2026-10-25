package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.accessory.AccessoryCatalogue;
import com.ninuna.losttales.accessory.AccessoryDefinition;
import com.ninuna.losttales.accessory.AccessoryEffect;
import com.ninuna.losttales.accessory.AccessoryFiles;
import com.ninuna.losttales.accessory.AccessorySlotType;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The accessories the server plays with (ACC-01), sent as a player joins,
 * so the inventory's ring slot takes what the server's takes and the
 * effects it names are known. A packet that cannot be read, or holds an
 * accessory that would not load from a file, changes nothing.
 */
public final class AccessoryCatalogPacket implements IMessage {
    static final int MAX_PACKET_BYTES = 64 * 1024;

    private final List<AccessoryDefinition> definitions = new ArrayList<AccessoryDefinition>();
    private boolean malformed;

    public AccessoryCatalogPacket() {}

    public AccessoryCatalogPacket(List<AccessoryDefinition> definitions) {
        if (definitions != null) {
            this.definitions.addAll(definitions);
        }
        if (this.definitions.size() > AccessoryCatalogue.MAX_ACCESSORIES) {
            throw new IllegalArgumentException("too many accessories");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.definitions.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            int count = buf.readUnsignedShort();
            if (count > AccessoryCatalogue.MAX_ACCESSORIES) {
                throw new LostTalesPacketCodec.DecodeException("too many accessories");
            }
            List<AccessoryDefinition> read = new ArrayList<AccessoryDefinition>();
            Set<String> ids = new HashSet<String>();
            for (int index = 0; index < count; index++) {
                AccessoryDefinition definition = readDefinition(buf);
                if (!ids.add(definition.getId())
                        || !AccessoryFiles.warningsOf(definition, null).isEmpty()) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "an accessory that would not load");
                }
                read.add(definition);
            }
            LostTalesPacketCodec.requireFinished(buf);
            this.definitions.addAll(read);
        } catch (RuntimeException exception) {
            this.definitions.clear();
            this.malformed = true;
            if (buf != null) {
                buf.skipBytes(buf.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.definitions.size());
        for (AccessoryDefinition definition : this.definitions) {
            LostTalesPacketCodec.writeUtf8String(buf, definition.getId(),
                    AccessoryFiles.MAX_ID_LENGTH);
            buf.writeByte(definition.getSlotType().ordinal());
            LostTalesPacketCodec.writeUtf8String(buf, definition.getItemId(),
                    AccessoryFiles.MAX_ITEM_ID_LENGTH);
            int effects = 0;
            for (AccessoryEffect effect : definition.getEffects()) {
                effects |= 1 << effect.ordinal();
            }
            buf.writeByte(effects);
        }
    }

    public List<AccessoryDefinition> getDefinitions() {
        return Collections.unmodifiableList(this.definitions);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static AccessoryDefinition readDefinition(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf, AccessoryFiles.MAX_ID_LENGTH);
        int slot = buf.readUnsignedByte();
        if (slot >= AccessorySlotType.values().length) {
            throw new LostTalesPacketCodec.DecodeException("no such slot");
        }
        String item = LostTalesPacketCodec.readUtf8String(buf, AccessoryFiles.MAX_ITEM_ID_LENGTH);
        int bits = buf.readUnsignedByte();
        Set<AccessoryEffect> effects = EnumSet.noneOf(AccessoryEffect.class);
        for (AccessoryEffect effect : AccessoryEffect.values()) {
            if ((bits & 1 << effect.ordinal()) != 0) {
                effects.add(effect);
                bits &= ~(1 << effect.ordinal());
            }
        }
        if (bits != 0) {
            throw new LostTalesPacketCodec.DecodeException("no such effect");
        }
        return new AccessoryDefinition(id, AccessorySlotType.values()[slot], item, effects);
    }

    public static final class Handler implements IMessageHandler<AccessoryCatalogPacket, IMessage> {
        @Override
        public IMessage onMessage(final AccessoryCatalogPacket message, MessageContext context) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleAccessoryCatalog(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
