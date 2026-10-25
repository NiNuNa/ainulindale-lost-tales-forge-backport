package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceFiles;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceSkins;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The races and capes the server plays with (CHAR-38, CHAR-39), sent as a
 * player joins, so the creator, the physics and the renderer use them. A
 * packet that cannot be read, or holds a race or cape that would not load
 * from a file, changes nothing.
 */
public final class CharacterRaceCatalogPacket implements IMessage {
    /** Under the 1 MiB a custom payload may carry in 1.7.10. */
    static final int MAX_PACKET_BYTES = 1000 * 1024;
    static final int MAX_RACES = CharacterRaceRegistry.MAX_RACES + CharacterRaceRegistry.MAX_STAND_INS;
    private static final int MAX_ID_BYTES = 128;
    private static final int MAX_WORD_BYTES = 4 * 32;
    private static final int MAX_TEXTURE_BYTES = 256;
    private static final int MAX_BODY_TYPES = 3;

    private final List<CharacterRaceDefinition> races = new ArrayList<CharacterRaceDefinition>();
    private final List<CharacterCapeDefinition> capes = new ArrayList<CharacterCapeDefinition>();
    private boolean malformed;

    public CharacterRaceCatalogPacket() {}

    public CharacterRaceCatalogPacket(List<CharacterRaceDefinition> races,
                                      List<CharacterCapeDefinition> capes) {
        if (races != null) {
            this.races.addAll(races);
        }
        if (capes != null) {
            this.capes.addAll(capes);
        }
        if (this.races.size() > MAX_RACES
                || this.capes.size() > CharacterCapeCatalog.MAX_CAPES) {
            throw new IllegalArgumentException("too many races or capes");
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.races.clear();
        this.capes.clear();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            List<CharacterRaceDefinition> readRaces = new ArrayList<CharacterRaceDefinition>();
            Set<String> raceIds = new HashSet<String>();
            int raceCount = count(buf, MAX_RACES, "race");
            for (int index = 0; index < raceCount; index++) {
                CharacterRaceDefinition race = readRace(buf);
                if (!raceIds.add(race.getId())) {
                    throw new LostTalesPacketCodec.DecodeException("a race sent twice");
                }
                if (!CharacterRaceFiles.warningsOf(race, null).isEmpty()) {
                    throw new LostTalesPacketCodec.DecodeException("a race that would not load");
                }
                readRaces.add(race);
            }
            for (CharacterRaceDefinition race : readRaces) {
                if (race.isStandIn() && !raceIds.contains(race.getStandInFor())) {
                    throw new LostTalesPacketCodec.DecodeException("a stand-in for no race");
                }
            }
            List<CharacterCapeDefinition> readCapes = new ArrayList<CharacterCapeDefinition>();
            Set<String> capeIds = new HashSet<String>();
            int capeCount = count(buf, CharacterCapeCatalog.MAX_CAPES, "cape");
            for (int index = 0; index < capeCount; index++) {
                CharacterCapeDefinition cape = readCape(buf);
                if (!capeIds.add(cape.getId())
                        || !CharacterCapeCatalog.warningsOf(cape).isEmpty()) {
                    throw new LostTalesPacketCodec.DecodeException("a cape that would not load");
                }
                readCapes.add(cape);
            }
            LostTalesPacketCodec.requireFinished(buf);
            this.races.addAll(readRaces);
            this.capes.addAll(readCapes);
        } catch (RuntimeException exception) {
            this.races.clear();
            this.capes.clear();
            this.malformed = true;
            if (buf != null) {
                buf.skipBytes(buf.readableBytes());
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeShort(this.races.size());
        for (CharacterRaceDefinition race : this.races) {
            writeRace(buf, race);
        }
        buf.writeShort(this.capes.size());
        for (CharacterCapeDefinition cape : this.capes) {
            writeCape(buf, cape);
        }
    }

    public List<CharacterRaceDefinition> getRaces() {
        return Collections.unmodifiableList(this.races);
    }

    public List<CharacterCapeDefinition> getCapes() {
        return Collections.unmodifiableList(this.capes);
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static void writeRace(ByteBuf buf, CharacterRaceDefinition race) {
        LostTalesPacketCodec.writeUtf8String(buf, race.getId(), MAX_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, race.getName(), MAX_WORD_BYTES);
        buf.writeByte(race.getBody().ordinal());
        LostTalesPacketCodec.writeUtf8String(buf, race.getLotrRaceAssociation(), MAX_ID_BYTES);
        int categories = 0;
        for (CharacterFactionCategory category : race.getAllowedFactionCategories()) {
            categories |= 1 << category.ordinal();
        }
        buf.writeByte(categories);
        writeStrings(buf, race.getAllowedFactionIds(), MAX_ID_BYTES);
        writeStrings(buf, race.getAllowedBodyTypeIds(), MAX_ID_BYTES);
        buf.writeFloat(race.getWidth());
        buf.writeFloat(race.getHeight());
        buf.writeDouble(race.getMaxHealth());
        buf.writeDouble(race.getMovementSpeedMultiplier());
        buf.writeDouble(race.getAttackDamage());
        buf.writeByte(race.getSkins().size());
        for (CharacterRaceSkins skins : race.getSkins()) {
            LostTalesPacketCodec.writeUtf8String(buf, skins.getGroup(), MAX_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buf, skins.getName(), MAX_WORD_BYTES);
            LostTalesPacketCodec.writeUtf8String(buf, skins.getBodyTypeId(), MAX_ID_BYTES);
            LostTalesPacketCodec.writeUtf8String(buf, skins.getTexture(), MAX_TEXTURE_BYTES);
            buf.writeByte(skins.getCount());
        }
        buf.writeByte(race.getStartingSkills().size());
        for (Map.Entry<String, Integer> skill : race.getStartingSkills().entrySet()) {
            LostTalesPacketCodec.writeUtf8String(buf, skill.getKey(), MAX_ID_BYTES);
            buf.writeByte(skill.getValue().intValue());
        }
        LostTalesPacketCodec.writeUtf8String(buf, race.getStandInFor(), MAX_ID_BYTES);
    }

    private static CharacterRaceDefinition readRace(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_WORD_BYTES);
        int bodyIndex = buf.readUnsignedByte();
        if (bodyIndex >= CharacterBody.values().length) {
            throw new LostTalesPacketCodec.DecodeException("no such body");
        }
        String valuesFrom = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        int categoryBits = buf.readUnsignedByte();
        Set<CharacterFactionCategory> categories =
                EnumSet.noneOf(CharacterFactionCategory.class);
        for (CharacterFactionCategory category : CharacterFactionCategory.values()) {
            if ((categoryBits & (1 << category.ordinal())) != 0) {
                categories.add(category);
                categoryBits &= ~(1 << category.ordinal());
            }
        }
        if (categoryBits != 0) {
            throw new LostTalesPacketCodec.DecodeException("no such faction category");
        }
        Set<String> factions = readStrings(buf, CharacterRaceFiles.MAX_FACTIONS, MAX_ID_BYTES);
        Set<String> bodyTypes = readStrings(buf, MAX_BODY_TYPES, MAX_ID_BYTES);
        float width = buf.readFloat();
        float height = buf.readFloat();
        double health = buf.readDouble();
        double speed = buf.readDouble();
        double attack = buf.readDouble();
        int skinCount = count8(buf, CharacterRaceFiles.MAX_SKIN_ENTRIES, "skin entry");
        List<CharacterRaceSkins> skins = new ArrayList<CharacterRaceSkins>();
        for (int index = 0; index < skinCount; index++) {
            skins.add(new CharacterRaceSkins(
                    LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES),
                    LostTalesPacketCodec.readUtf8String(buf, MAX_WORD_BYTES),
                    LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES),
                    LostTalesPacketCodec.readUtf8String(buf, MAX_TEXTURE_BYTES),
                    buf.readUnsignedByte()));
        }
        int skillCount = count8(buf, CharacterRaceFiles.MAX_STARTING_SKILLS, "skill");
        Map<String, Integer> skills = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < skillCount; index++) {
            String skill = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
            if (skills.put(skill, Integer.valueOf(buf.readUnsignedByte())) != null) {
                throw new LostTalesPacketCodec.DecodeException("a skill sent twice");
            }
        }
        String standInFor = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        CharacterBody body = CharacterBody.values()[bodyIndex];
        return new CharacterRaceDefinition(id, name, body, valuesFrom, categories, factions,
                bodyTypes, width, height, health, speed, attack, skins, skills, standInFor);
    }

    private static void writeCape(ByteBuf buf, CharacterCapeDefinition cape) {
        LostTalesPacketCodec.writeUtf8String(buf, cape.getId(), MAX_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, cape.getName(), MAX_WORD_BYTES);
        LostTalesPacketCodec.writeUtf8String(buf, cape.getPicture(), MAX_TEXTURE_BYTES);
        writeStrings(buf, cape.getRaces(), MAX_ID_BYTES);
        writeStrings(buf, cape.getFactions(), MAX_ID_BYTES);
    }

    private static CharacterCapeDefinition readCape(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf, MAX_ID_BYTES);
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_WORD_BYTES);
        String picture = LostTalesPacketCodec.readUtf8String(buf, MAX_TEXTURE_BYTES);
        Set<String> races = readStrings(buf, CharacterCapeCatalog.MAX_NAMED, MAX_ID_BYTES);
        Set<String> factions = readStrings(buf, CharacterCapeCatalog.MAX_NAMED, MAX_ID_BYTES);
        return new CharacterCapeDefinition(id, name, picture, races, factions);
    }

    private static void writeStrings(ByteBuf buf, Set<String> values, int maximumBytes) {
        buf.writeByte(values.size());
        for (String value : values) {
            LostTalesPacketCodec.writeUtf8String(buf, value, maximumBytes);
        }
    }

    private static Set<String> readStrings(ByteBuf buf, int maximum, int maximumBytes) {
        int count = count8(buf, maximum, "name");
        Set<String> values = new LinkedHashSet<String>();
        for (int index = 0; index < count; index++) {
            if (!values.add(LostTalesPacketCodec.readUtf8String(buf, maximumBytes))) {
                throw new LostTalesPacketCodec.DecodeException("a name sent twice");
            }
        }
        return values;
    }

    private static int count(ByteBuf buf, int maximum, String what) {
        int count = buf.readUnsignedShort();
        if (count > maximum) {
            throw new LostTalesPacketCodec.DecodeException("too many of " + what);
        }
        return count;
    }

    private static int count8(ByteBuf buf, int maximum, String what) {
        int count = buf.readUnsignedByte();
        if (count > maximum) {
            throw new LostTalesPacketCodec.DecodeException("too many of " + what);
        }
        return count;
    }

    /** Common-safe clientbound handler; the client's work goes through the sided proxy. */
    public static class Handler implements IMessageHandler<CharacterRaceCatalogPacket, IMessage> {
        @Override
        public IMessage onMessage(final CharacterRaceCatalogPacket message, MessageContext ctx) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleRaceCatalog(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
