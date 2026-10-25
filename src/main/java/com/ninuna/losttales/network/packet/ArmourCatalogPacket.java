package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.armour.ArmourCatalogue;
import com.ninuna.losttales.armour.ArmourFiles;
import com.ninuna.losttales.armour.ArmourSettings;
import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.armour.NamedSet;
import com.ninuna.losttales.armour.SetBonus;
import com.ninuna.losttales.armour.SetEffect;
import com.ninuna.losttales.armour.WeightFile;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The armour weights, named sets and weight-set numbers the server plays
 * with (CONTENT-06, CONTENT-07, PROG-18), sent as a player joins, so the
 * tooltips count sets and name bonuses as the server does. A packet that
 * cannot be read, or holds a set that would not load from a file,
 * changes nothing.
 */
public final class ArmourCatalogPacket implements IMessage {
    static final int MAX_PACKET_BYTES = 2000 * 1024;
    private static final int MAX_NAME_BYTES = ArmourFiles.MAX_NAME_LENGTH * 4;
    private static final int MAX_LORE_BYTES = ArmourFiles.MAX_LORE_LENGTH * 4;

    private ArmourCatalogue catalogue = ArmourCatalogue.EMPTY;
    private ArmourSettings settings = ArmourSettings.SHIPPED;
    private boolean malformed;

    public ArmourCatalogPacket() {}

    public ArmourCatalogPacket(ArmourCatalogue catalogue, ArmourSettings settings) {
        this.catalogue = catalogue == null ? ArmourCatalogue.EMPTY : catalogue;
        this.settings = settings == null ? ArmourSettings.SHIPPED : settings;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.catalogue = ArmourCatalogue.EMPTY;
        this.settings = ArmourSettings.SHIPPED;
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException("invalid packet size");
            }
            ArmourSettings read = new ArmourSettings(share(buf), share(buf), share(buf), share(buf));
            Map<String, ArmourWeight> materials = readWeights(buf, false);
            Map<String, ArmourWeight> items = readWeights(buf, true);
            int count = LostTalesPacketCodec.readCount(buf, ArmourCatalogue.MAX_SETS, "set");
            List<NamedSet> sets = new ArrayList<NamedSet>();
            Set<String> ids = new HashSet<String>();
            for (int index = 0; index < count; index++) {
                NamedSet set = readSet(buf);
                if (!ids.add(set.getId()) || !ArmourFiles.warningsOf(set).isEmpty()) {
                    throw new LostTalesPacketCodec.DecodeException("a set that would not load");
                }
                sets.add(set);
            }
            LostTalesPacketCodec.requireFinished(buf);
            this.catalogue = ArmourCatalogue.of(Collections.singletonList(
                    new WeightFile("losttales:server", materials, items)), sets);
            this.settings = read;
        } catch (RuntimeException exception) {
            this.catalogue = ArmourCatalogue.EMPTY;
            this.settings = ArmourSettings.SHIPPED;
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.settings.getLightSpeed());
        buf.writeDouble(this.settings.getHeavyKnockback());
        buf.writeDouble(this.settings.getSlowSmall());
        buf.writeDouble(this.settings.getSlowLarge());
        writeWeights(buf, this.catalogue.getMaterials(), ArmourFiles.MAX_MATERIAL_LENGTH);
        writeWeights(buf, this.catalogue.getItems(), ArmourFiles.MAX_ITEM_ID_LENGTH);
        LostTalesPacketCodec.writeCount(buf, this.catalogue.getSets().size(),
                ArmourCatalogue.MAX_SETS, "set");
        for (NamedSet set : this.catalogue.getSets()) {
            writeSet(buf, set);
        }
    }

    public ArmourCatalogue getCatalogue() {
        return this.catalogue;
    }

    public ArmourSettings getSettings() {
        return this.settings;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static double share(ByteBuf buf) {
        double value = buf.readDouble();
        if (Double.isNaN(value) || value < 0.0D || value > 1.0D) {
            throw new LostTalesPacketCodec.DecodeException("a share out of range");
        }
        return value;
    }

    private static void writeWeights(ByteBuf buf, Map<String, ArmourWeight> weights,
                                     int maxLength) {
        LostTalesPacketCodec.writeCount(buf, weights.size(), ArmourCatalogue.MAX_WEIGHTS,
                "weight");
        for (Map.Entry<String, ArmourWeight> entry : weights.entrySet()) {
            LostTalesPacketCodec.writeUtf8String(buf, entry.getKey(), maxLength);
            buf.writeByte(entry.getValue().ordinal());
        }
    }

    private static Map<String, ArmourWeight> readWeights(ByteBuf buf, boolean items) {
        int count = LostTalesPacketCodec.readCount(buf, ArmourCatalogue.MAX_WEIGHTS, "weight");
        Map<String, ArmourWeight> weights = new LinkedHashMap<String, ArmourWeight>();
        for (int index = 0; index < count; index++) {
            String name = LostTalesPacketCodec.readUtf8String(buf, items
                    ? ArmourFiles.MAX_ITEM_ID_LENGTH : ArmourFiles.MAX_MATERIAL_LENGTH);
            int weight = buf.readUnsignedByte();
            if (weight >= ArmourWeight.values().length
                    || !(items ? ArmourFiles.isItemId(name) : ArmourFiles.isMaterial(name))
                    || weights.put(name, ArmourWeight.values()[weight]) != null) {
                throw new LostTalesPacketCodec.DecodeException("a weight that would not load");
            }
        }
        return weights;
    }

    private static void writeSet(ByteBuf buf, NamedSet set) {
        LostTalesPacketCodec.writeUtf8String(buf, set.getId(), ArmourFiles.MAX_ID_LENGTH);
        buf.writeBoolean(set.isRelic());
        for (ArmourSlot slot : ArmourSlot.values()) {
            List<String> items = set.items(slot);
            LostTalesPacketCodec.writeCount(buf, items.size(), ArmourFiles.MAX_SLOT_ITEMS, "item");
            for (String item : items) {
                LostTalesPacketCodec.writeUtf8String(buf, item, ArmourFiles.MAX_ITEM_ID_LENGTH);
            }
        }
        LostTalesPacketCodec.writeCount(buf, set.getBonuses().size(), ArmourFiles.MAX_BONUSES,
                "bonus");
        for (SetBonus bonus : set.getBonuses()) {
            buf.writeByte(bonus.getEffect().ordinal());
            buf.writeDouble(bonus.getAmount());
            LostTalesPacketCodec.writeUtf8String(buf, bonus.getSkill(), ArmourFiles.MAX_ID_LENGTH);
        }
        LostTalesPacketCodec.writeUtf8String(buf, set.getName(), MAX_NAME_BYTES);
        for (ArmourSlot slot : ArmourSlot.values()) {
            LostTalesPacketCodec.writeUtf8String(buf, set.pieceName(slot), MAX_NAME_BYTES);
            LostTalesPacketCodec.writeUtf8String(buf, set.pieceLore(slot), MAX_LORE_BYTES);
        }
    }

    private static NamedSet readSet(ByteBuf buf) {
        String id = LostTalesPacketCodec.readUtf8String(buf, ArmourFiles.MAX_ID_LENGTH);
        boolean relic = buf.readBoolean();
        Map<ArmourSlot, List<String>> slots =
                new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            int count = LostTalesPacketCodec.readCount(buf, ArmourFiles.MAX_SLOT_ITEMS, "item");
            List<String> items = new ArrayList<String>();
            for (int index = 0; index < count; index++) {
                items.add(LostTalesPacketCodec.readUtf8String(buf,
                        ArmourFiles.MAX_ITEM_ID_LENGTH));
            }
            slots.put(slot, items);
        }
        int bonusCount = LostTalesPacketCodec.readCount(buf, ArmourFiles.MAX_BONUSES, "bonus");
        List<SetBonus> bonuses = new ArrayList<SetBonus>();
        for (int index = 0; index < bonusCount; index++) {
            int effect = buf.readUnsignedByte();
            if (effect >= SetEffect.values().length) {
                throw new LostTalesPacketCodec.DecodeException("no such effect");
            }
            double amount = buf.readDouble();
            String skill = LostTalesPacketCodec.readUtf8String(buf, ArmourFiles.MAX_ID_LENGTH);
            bonuses.add(new SetBonus(SetEffect.values()[effect], amount, skill));
        }
        String name = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
        Map<ArmourSlot, String> names = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        Map<ArmourSlot, String> lore = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        for (ArmourSlot slot : ArmourSlot.values()) {
            String pieceName = LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
            String pieceLore = LostTalesPacketCodec.readUtf8String(buf, MAX_LORE_BYTES);
            if (pieceName.length() > 0) {
                names.put(slot, pieceName);
            }
            if (pieceLore.length() > 0) {
                lore.put(slot, pieceLore);
            }
        }
        return new NamedSet(id, relic, slots, bonuses, name, names, lore);
    }

    public static final class Handler implements IMessageHandler<ArmourCatalogPacket, IMessage> {
        @Override
        public IMessage onMessage(final ArmourCatalogPacket message, MessageContext context) {
            if (message != null && !message.isMalformed() && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleArmourCatalog(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
