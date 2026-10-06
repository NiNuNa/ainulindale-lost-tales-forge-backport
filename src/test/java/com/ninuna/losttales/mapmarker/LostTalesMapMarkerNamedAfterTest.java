package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A marker with no name of its own is called after its placer, a
 * creature's kind or a block, and each game words that in its own
 * language: a waystone a player has not named reads {@code Nils's
 * Waystone}, its category and description the words for its kind. Only
 * a name someone gave a marker stays as written.
 */
public final class LostTalesMapMarkerNamedAfterTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final List<String> KEYS = Arrays.asList(
            LostTalesMapMarkerNamedAfter.WAYSTONE_NAME_KEY,
            LostTalesMapMarkerNames.WAYSTONE_DESCRIPTION_KEY,
            "gui.losttales.map_marker.category.waystone",
            "gui.losttales.map_marker.category.point_of_interest",
            "gui.losttales.map_marker.category.map_marker",
            "entity.losttales.Nia.name");

    @Before
    public void readInEnglish() {
        StringBuilder english = new StringBuilder();
        for (String key : KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        inject(english.toString());
    }

    @After
    public void readInEnglishAgain() {
        readInEnglish();
    }

    @Test
    public void aReferenceIsOneOfItsKindsWithinItsBounds() {
        assertEquals("player:Nils", LostTalesMapMarkerNamedAfter.player("Nils"));
        assertEquals("entity:losttales.Nia",
                LostTalesMapMarkerNamedAfter.entity("losttales.Nia"));
        assertEquals("block:losttales:cheese_wheel",
                LostTalesMapMarkerNamedAfter.block("losttales:cheese_wheel"));
        assertTrue(LostTalesMapMarkerNamedAfter.isValid(""));
        assertTrue(LostTalesMapMarkerNamedAfter.isValid("player:Nils's"));
        assertFalse("no key is made from words",
                LostTalesMapMarkerNamedAfter.isValid("entity:a b=c"));
        assertFalse(LostTalesMapMarkerNamedAfter.isValid("player:a§cb"));
        assertFalse(LostTalesMapMarkerNamedAfter.isValid("player:"));
        assertFalse(LostTalesMapMarkerNamedAfter.isValid("someone:Nils"));
        assertEquals("", LostTalesMapMarkerNamedAfter.entity("not an id"));
    }

    @Test
    public void anUnnamedWaystoneReadsInTheGamesLanguage() {
        assertEquals("Nils's Waystone",
                LostTalesMapMarkerNamedAfter.shownName("player:Nils"));
        assertEquals("Nia", LostTalesMapMarkerNamedAfter.shownName(
                "entity:losttales.Nia"));
        inject(LostTalesMapMarkerNamedAfter.WAYSTONE_NAME_KEY
                + "=Wegstein von %s\n"
                + LostTalesMapMarkerNames.WAYSTONE_DESCRIPTION_KEY
                + "=Ein Wegstein, von einem Spieler gesetzt.\n"
                + "gui.losttales.map_marker.category.waystone=Wegstein\n");
        LostTalesMapMarkerData waystone = clientMarker("", "", "",
                "player:Nils");
        assertEquals("Wegstein von Nils", waystone.getName());
        assertEquals("", waystone.getGivenName());
        assertEquals("Ein Wegstein, von einem Spieler gesetzt.",
                waystone.getDescription());
        assertEquals("Wegstein", LostTalesMapMarkerNames.shownCategory("",
                LostTalesMapMarkerSource.PLAYER_CREATED, true));
    }

    /** What a player named their waystone stays theirs, in every language. */
    @Test
    public void aNamedWaystoneKeepsItsName() {
        inject(LostTalesMapMarkerNamedAfter.WAYSTONE_NAME_KEY + "=Wegstein von %s\n");
        LostTalesMapMarkerData named = clientMarker("The Old Gate", "Cairns",
                "Where the road bends.", "player:Nils");
        assertEquals("The Old Gate", named.getName());
        assertEquals("Cairns", named.getCategoryName());
        assertEquals("Where the road bends.", named.getDescription());
        assertEquals("Cairns", LostTalesMapMarkerNames.shownCategory(
                "Cairns", LostTalesMapMarkerSource.PLAYER_CREATED, true));
    }

    /** English reads word for word as the waystone once kept it. */
    @Test
    public void inEnglishTheDefaultsReadAsTheyAlwaysDid() {
        assertEquals("Nils's Waystone", clientMarker("", "", "",
                "player:Nils").getName());
        assertEquals("A player-placed waystone.", clientMarker("", "", "",
                "player:Nils").getDescription());
        assertEquals("Waystone", LostTalesMapMarkerNames.shownCategory("",
                LostTalesMapMarkerSource.PLAYER_CREATED, true));
        assertEquals("Point of Interest", LostTalesMapMarkerNames.shownCategory(
                "", LostTalesMapMarkerSource.CUSTOM_PRESET, true));
        assertEquals("Map Marker", LostTalesMapMarkerNames.shownCategory(
                "", LostTalesMapMarkerSource.CUSTOM_PRESET, false));
    }

    /** A server's line names an unnamed waystone by a translation each reader's game words. */
    @Test
    public void aServersLineNamesAnUnnamedMarkerByATranslation() {
        IChatComponent name = LostTalesMapMarkerNames.component(
                "losttales:player/abc", "", "player:Nils");
        assertTrue(name instanceof ChatComponentTranslation);
        assertEquals("Nils's Waystone", EnglishWords.INSTANCE.read(name));
        assertEquals("The Old Gate", EnglishWords.INSTANCE.read(
                LostTalesMapMarkerNames.component("losttales:player/abc",
                        "The Old Gate", "player:Nils")));
    }

    /** A player's waystone is made unnamed, with no words of its own. */
    @Test
    public void aNewWaystoneKeepsNoWords() {
        LostTalesMapMarkerRecord record = LostTalesMapMarkerRecord
                .createPlayerMarker("losttales:player/abc", "Nils",
                        java.util.UUID.randomUUID(), 0, 1, 64, 1,
                        java.util.UUID.randomUUID());
        assertEquals("", record.getName());
        assertEquals("player:Nils", record.getNamedAfter());
        assertEquals("", record.getCategoryName());
        assertEquals("", record.getDescription());
        assertEquals("player:Nils", record.toDefinition().getNamedAfter());
    }

    @Test
    public void theSnapshotCarriesWhatAMarkerIsCalledAfter() {
        LostTalesMapMarkerDefinition marker = definition("player:Nils");
        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(
                new LostTalesMapMarkerSnapshotPacket(
                        Collections.singletonList(marker)));
        assertFalse(decoded.isMalformed());
        assertEquals("", decoded.getMarkers().get(0).getName());
        assertEquals("player:Nils", decoded.getMarkers().get(0).getNamedAfter());

        ByteBuf corrupt = Unpooled.buffer();
        new LostTalesMapMarkerSnapshotPacket(Collections.singletonList(marker))
                .toBytes(corrupt);
        byte[] bytes = new byte[corrupt.readableBytes()];
        corrupt.readBytes(bytes);
        String text = new String(bytes, UTF_8);
        int at = text.indexOf("player:Nils");
        assertTrue(at > 0);
        bytes[at + 5] = (byte)'x';
        LostTalesMapMarkerSnapshotPacket refused =
                new LostTalesMapMarkerSnapshotPacket();
        refused.fromBytes(Unpooled.wrappedBuffer(bytes));
        assertTrue("a reference of no known kind is malformed",
                refused.isMalformed());
    }

    /** A quest giver's marker travels with its kind, never a name in the server's language. */
    @Test
    public void aGiversMarkerTravelsWithItsKind() {
        LostTalesQuestSyncPacket packet = new LostTalesQuestSyncPacket(
                null, null, null, null, "", Collections.singletonList(
                        definition("entity:losttales.Nia")), null);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesQuestSyncPacket decoded = new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("entity:losttales.Nia",
                decoded.getDynamicMapMarkers().get(0).getNamedAfter());
        assertEquals("", decoded.getDynamicMapMarkers().get(0).getName());
    }

    private static LostTalesMapMarkerDefinition definition(String namedAfter) {
        return new LostTalesMapMarkerDefinition("losttales:quest_giver_nia",
                "", "quest", "blue", "", "", false, 0, 1.0D, 64.0D, 1.0D,
                128.0D, 8.0D, true, true, false,
                LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "", 0,
                namedAfter);
    }

    private static LostTalesMapMarkerSnapshotPacket roundTrip(
            LostTalesMapMarkerSnapshotPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesMapMarkerSnapshotPacket decoded =
                new LostTalesMapMarkerSnapshotPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static LostTalesMapMarkerData clientMarker(String name,
            String category, String description, String namedAfter) {
        return new LostTalesMapMarkerData("losttales:player/abc", name, "fort",
                "white", category, description, true, 0, 1.0D, 64.0D, 1.0D,
                128.0D, 8.0D, true, true, false, true, 0,
                LostTalesMapMarkerSource.PLAYER_CREATED, "", "", namedAfter);
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
