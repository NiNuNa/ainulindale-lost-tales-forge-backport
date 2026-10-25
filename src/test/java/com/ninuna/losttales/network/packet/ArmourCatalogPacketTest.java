package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.armour.ArmourCatalogue;
import com.ninuna.losttales.armour.ArmourSettings;
import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.armour.NamedSet;
import com.ninuna.losttales.armour.SetBonus;
import com.ninuna.losttales.armour.SetEffect;
import com.ninuna.losttales.armour.WeightFile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The armour files and numbers a server sends a joining player
 * (CONTENT-06, CONTENT-07, PROG-18) read back as written, and a payload
 * that is cut short, runs on, or holds a set that would not load from a
 * file is malformed and changes nothing.
 */
public final class ArmourCatalogPacketTest {

    @Test
    public void theFilesAndNumbersReadBack() {
        ArmourSettings settings = ArmourSettings.ofPercent(10.0D, 30.0D, 2.0D, 4.0D);
        ArmourCatalogPacket read = roundTrip(new ArmourCatalogPacket(catalogue(relic()), settings));
        assertFalse(read.isMalformed());
        ArmourCatalogue catalogue = read.getCatalogue();
        assertEquals(ArmourWeight.HEAVY, catalogue.weightOf("", "LOTR_GONDOR"));
        assertEquals(ArmourWeight.LIGHT, catalogue.weightOf("losttales:moon_elf_armor_light", ""));
        assertEquals(ArmourWeight.NEITHER, catalogue.weightOf("", "LOTR_KAFTAN"));
        NamedSet set = catalogue.set("losttales:isildurs");
        assertTrue(set.isRelic());
        assertEquals(Arrays.asList("lotr:item.helmetGondor", "lotr:item.helmetGondorWinged"),
                set.items(ArmourSlot.HELMET));
        assertEquals("Isildur's Helm", set.pieceName(ArmourSlot.HELMET));
        assertEquals("Worn at the Last Alliance.", set.pieceLore(ArmourSlot.HELMET));
        assertEquals("", set.pieceName(ArmourSlot.BOOTS));
        assertEquals(2, set.getBonuses().size());
        assertEquals(SetEffect.EXPERIENCE, set.getBonuses().get(1).getEffect());
        assertEquals("losttales:lore", set.getBonuses().get(1).getSkill());
        assertEquals(0.1D, read.getSettings().getLightSpeed(), 1.0E-9D);
        assertEquals(0.3D, read.getSettings().getHeavyKnockback(), 1.0E-9D);
        assertEquals(0.02D, read.getSettings().getSlowSmall(), 1.0E-9D);
        assertEquals(0.04D, read.getSettings().getSlowLarge(), 1.0E-9D);
    }

    @Test
    public void aCutOrLongerPayloadIsMalformed() {
        ByteBuf whole = Unpooled.buffer();
        new ArmourCatalogPacket(catalogue(relic()), ArmourSettings.SHIPPED).toBytes(whole);
        ByteBuf cut = whole.copy(0, whole.readableBytes() - 3);
        ArmourCatalogPacket read = new ArmourCatalogPacket();
        read.fromBytes(cut);
        assertTrue(read.isMalformed());
        assertTrue(read.getCatalogue().getSets().isEmpty());
        ByteBuf longer = whole.copy();
        longer.writeByte(1);
        read = new ArmourCatalogPacket();
        read.fromBytes(longer);
        assertTrue(read.isMalformed());
    }

    @Test
    public void aSetThatWouldNotLoadIsMalformed() {
        Map<ArmourSlot, List<String>> slots = new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        slots.put(ArmourSlot.HELMET, Collections.singletonList("lotr:item.helmetGondor"));
        NamedSet missingSlots = new NamedSet("losttales:half", false, slots,
                Collections.singletonList(new SetBonus(SetEffect.SPEED, 0.05D, "")), "", null, null);
        assertTrue(roundTrip(new ArmourCatalogPacket(catalogue(missingSlots),
                ArmourSettings.SHIPPED)).isMalformed());
    }

    private static ArmourCatalogPacket roundTrip(ArmourCatalogPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        ArmourCatalogPacket read = new ArmourCatalogPacket();
        read.fromBytes(buffer);
        return read;
    }

    private static NamedSet relic() {
        Map<ArmourSlot, List<String>> slots = new EnumMap<ArmourSlot, List<String>>(ArmourSlot.class);
        slots.put(ArmourSlot.HELMET, Arrays.asList("lotr:item.helmetGondor",
                "lotr:item.helmetGondorWinged"));
        slots.put(ArmourSlot.CHESTPLATE, Collections.singletonList("lotr:item.bodyGondor"));
        slots.put(ArmourSlot.LEGGINGS, Collections.singletonList("lotr:item.legsGondor"));
        slots.put(ArmourSlot.BOOTS, Collections.singletonList("lotr:item.bootsGondor"));
        Map<ArmourSlot, String> names = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        names.put(ArmourSlot.HELMET, "Isildur's Helm");
        Map<ArmourSlot, String> lore = new EnumMap<ArmourSlot, String>(ArmourSlot.class);
        lore.put(ArmourSlot.HELMET, "Worn at the Last Alliance.");
        return new NamedSet("losttales:isildurs", true, slots, Arrays.asList(
                new SetBonus(SetEffect.PROTECTION, 0.1D, ""),
                new SetBonus(SetEffect.EXPERIENCE, 0.2D, "losttales:lore")), "", names, lore);
    }

    private static ArmourCatalogue catalogue(NamedSet set) {
        Map<String, ArmourWeight> materials = new LinkedHashMap<String, ArmourWeight>();
        materials.put("LOTR_GONDOR", ArmourWeight.HEAVY);
        materials.put("LOTR_KAFTAN", ArmourWeight.NEITHER);
        Map<String, ArmourWeight> items = new LinkedHashMap<String, ArmourWeight>();
        items.put("losttales:moon_elf_armor_light", ArmourWeight.LIGHT);
        return ArmourCatalogue.of(Collections.singletonList(new WeightFile("losttales:weights",
                materials, items)), Collections.singletonList(set));
    }
}
