package com.ninuna.losttales.network.packet.character;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceFiles;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The server's races and capes reach a joining player whole (CHAR-38,
 * CHAR-39): every field a race file writes, a stand-in with the race it
 * stands in for, and every cape. A packet cut short, or one holding a race
 * or cape that would not load from a file, is malformed and changes nothing.
 */
public final class CharacterRaceCatalogPacketTest {

    @Test
    public void theRacesAndCapesRoundTrip() {
        List<CharacterRaceDefinition> races =
                new ArrayList<CharacterRaceDefinition>(CharacterRaceRegistry.getBundled());
        races.add(beorning());
        races.add(CharacterRaceDefinition.standIn("myserver:longbeard",
                CharacterRaceRegistry.get(CharacterRaceRegistry.DWARF)));
        List<CharacterCapeDefinition> capes =
                new ArrayList<CharacterCapeDefinition>(CharacterCapeCatalog.getBundled());
        capes.add(new CharacterCapeDefinition("myserver:bear", "Bear Cloak", "lotr:ROHAN",
                new LinkedHashSet<String>(Arrays.asList("myserver:beorning")),
                new LinkedHashSet<String>(Arrays.asList("lotr:dale"))));

        CharacterRaceCatalogPacket read = roundTrip(new CharacterRaceCatalogPacket(races, capes));

        assertFalse(read.isMalformed());
        assertEquals(8, read.getRaces().size());
        CharacterRaceDefinition beorning = read.getRaces().get(6);
        assertEquals("Beorning", beorning.getName());
        assertEquals(1.9F, beorning.getHeight(), 0.0F);
        assertEquals(22.0D, beorning.getMaxHealth(), 0.0D);
        assertEquals("lotr:dale", beorning.getAllowedFactionIds().iterator().next());
        assertEquals(Integer.valueOf(10), beorning.getStartingSkills().get("losttales:husbandry"));
        assertEquals("Beorning", beorning.getSkins().get(0).getName());
        CharacterRaceDefinition longbeard = read.getRaces().get(7);
        assertTrue(longbeard.isStandIn());
        assertEquals(CharacterRaceRegistry.DWARF, longbeard.getStandInFor());
        CharacterRaceDefinition hobbit = read.getRaces().get(3);
        assertEquals(CharacterRaceRegistry.get(CharacterRaceRegistry.HOBBIT).getSkins().size(),
                hobbit.getSkins().size());
        assertTrue(hobbit.getAllowedFactionIds().contains("lotr:bree"));
        assertEquals(28, read.getCapes().size());
        CharacterCapeDefinition bear = read.getCapes().get(27);
        assertEquals("Bear Cloak", bear.getName());
        assertTrue(bear.getRaces().contains("myserver:beorning"));
        assertTrue(bear.getFactions().contains("lotr:dale"));
    }

    @Test
    public void aPacketCutShortChangesNothing() {
        ByteBuf buffer = Unpooled.buffer();
        new CharacterRaceCatalogPacket(CharacterRaceRegistry.getBundled(),
                CharacterCapeCatalog.getBundled()).toBytes(buffer);
        CharacterRaceCatalogPacket cut = new CharacterRaceCatalogPacket();
        cut.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));

        assertTrue(cut.isMalformed());
        assertTrue(cut.getRaces().isEmpty());
        assertTrue(cut.getCapes().isEmpty());
    }

    @Test
    public void aRaceThatWouldNotLoadIsMalformed() {
        CharacterRaceDefinition giant = CharacterRaceFiles.parse(new StringReader(
                BEORNING.replace("\"height\": 1.9", "\"height\": 4")), "giant.json",
                new ArrayList<String>());

        CharacterRaceCatalogPacket read = roundTrip(new CharacterRaceCatalogPacket(
                Arrays.asList(giant), new ArrayList<CharacterCapeDefinition>()));

        assertTrue(read.isMalformed());
    }

    @Test
    public void aStandInForNoRaceIsMalformed() {
        CharacterRaceDefinition orphan = CharacterRaceDefinition.standIn("myserver:longbeard",
                CharacterRaceRegistry.get(CharacterRaceRegistry.DWARF));

        CharacterRaceCatalogPacket read = roundTrip(new CharacterRaceCatalogPacket(
                Arrays.asList(orphan), new ArrayList<CharacterCapeDefinition>()));

        assertTrue(read.isMalformed());
    }

    @Test
    public void aCapeWithAPictureNobodyHasIsMalformed() {
        CharacterRaceCatalogPacket read = roundTrip(new CharacterRaceCatalogPacket(
                CharacterRaceRegistry.getBundled(), Arrays.asList(new CharacterCapeDefinition(
                        "myserver:far", "Far", "https://example.com/cape.png", null, null))));

        assertTrue(read.isMalformed());
    }

    private static final String BEORNING = "{"
            + "\"id\": \"myserver:beorning\", \"name\": \"Beorning\", \"body\": \"human\","
            + "\"sexes\": [\"losttales:male\", \"losttales:female\"],"
            + "\"size\": {\"width\": 0.65, \"height\": 1.9},"
            + "\"health\": 22, \"speed\": 1.0, \"attack\": 3,"
            + "\"faction_categories\": [\"human\"], \"factions\": [\"lotr:dale\"],"
            + "\"skins\": [{\"group\": \"beorning\", \"name\": \"Beorning\","
            + " \"sex\": \"losttales:male\", \"texture\": \"lotr:mob/bree/bree_male\", \"count\": 4}],"
            + "\"skills\": {\"losttales:husbandry\": 10}"
            + "}";

    private static CharacterRaceDefinition beorning() {
        return CharacterRaceFiles.parse(new StringReader(BEORNING), "beorning.json",
                new ArrayList<String>());
    }

    private static CharacterRaceCatalogPacket roundTrip(CharacterRaceCatalogPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        CharacterRaceCatalogPacket read = new CharacterRaceCatalogPacket();
        read.fromBytes(buffer);
        return read;
    }
}
