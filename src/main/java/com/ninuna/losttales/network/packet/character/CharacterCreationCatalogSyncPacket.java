package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.sync.CharacterCreationCatalog;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Server-authoritative race-to-starting-faction option catalogue. */
public final class CharacterCreationCatalogSyncPacket implements IMessage {

    private static final int MAX_RACES = 32;
    private static final int MAX_FACTIONS_PER_RACE = 256;

    private CharacterCreationCatalog catalog;
    private boolean malformed;

    public CharacterCreationCatalogSyncPacket() {}

    public CharacterCreationCatalogSyncPacket(CharacterCreationCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        this.catalog = catalog;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            boolean available = buffer.readBoolean();
            String reason = CharacterPacketCodec.readString(
                    buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            int raceCount = buffer.readUnsignedByte();
            if (raceCount > MAX_RACES) {
                throw new CharacterPacketCodec.DecodeException("too many race entries");
            }
            LinkedHashMap<String, List<String>> factionsByRace =
                    new LinkedHashMap<String, List<String>>();
            for (int raceIndex = 0; raceIndex < raceCount; raceIndex++) {
                String raceId = CharacterPacketCodec.readString(
                        buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                if (factionsByRace.containsKey(raceId)) {
                    throw new CharacterPacketCodec.DecodeException("duplicate race entry");
                }
                int factionCount = buffer.readUnsignedShort();
                if (factionCount > MAX_FACTIONS_PER_RACE) {
                    throw new CharacterPacketCodec.DecodeException("too many faction entries");
                }
                ArrayList<String> factions = new ArrayList<String>(factionCount);
                for (int factionIndex = 0; factionIndex < factionCount; factionIndex++) {
                    String factionId = CharacterPacketCodec.readString(
                            buffer, CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
                    if (factions.contains(factionId)) {
                        throw new CharacterPacketCodec.DecodeException(
                                "duplicate faction entry");
                    }
                    factions.add(factionId);
                }
                factionsByRace.put(raceId, factions);
            }
            CharacterPacketCodec.requireFinished(buffer);
            this.catalog = new CharacterCreationCatalog(available, reason, factionsByRace);
        } catch (RuntimeException exception) {
            this.catalog = null;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (this.catalog == null) {
            throw new IllegalStateException("catalog is missing");
        }
        Map<String, List<String>> entries = this.catalog.getFactionIdsByRace();
        if (entries.size() > MAX_RACES) {
            throw new IllegalStateException("too many race entries");
        }
        buffer.writeBoolean(this.catalog.isLotrAvailable());
        CharacterPacketCodec.writeString(buffer, this.catalog.getUnavailableReason(),
                CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
        buffer.writeByte(entries.size());
        for (Map.Entry<String, List<String>> entry : entries.entrySet()) {
            CharacterPacketCodec.writeString(buffer, entry.getKey(),
                    CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            List<String> factions = entry.getValue();
            if (factions.size() > MAX_FACTIONS_PER_RACE) {
                throw new IllegalStateException("too many faction entries");
            }
            buffer.writeShort(factions.size());
            for (String factionId : factions) {
                CharacterPacketCodec.writeString(buffer, factionId,
                        CharacterPacketCodec.MAX_IDENTIFIER_BYTES);
            }
        }
    }

    public CharacterCreationCatalog getCatalog() {
        return this.catalog;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<CharacterCreationCatalogSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterCreationCatalogSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleCharacterCreationCatalogSync(message);
                }
            });
            return null;
        }
    }
}
