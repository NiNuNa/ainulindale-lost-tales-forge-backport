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
import java.util.UUID;
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
            LostTalesMapMarkerNamedAfter.UNKNOWN_PLACER_KEY,
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

    /**
     * A player's waystone is made unnamed, with no words of its own, called
     * after the character that placed it by its id; every map is sent it
     * by that character's name as it stands (WAY-01).
     */
    @Test
    public void aNewWaystoneKeepsNoWords() {
        final UUID nils = UUID.fromString("0000000a-0000-0000-0000-00000000000a");
        LostTalesMapMarkerRecord record = LostTalesMapMarkerRecord
                .createPlayerMarker("losttales:player/abc",
                        nils, 0, 1, 64, 1, UUID.randomUUID());
        assertEquals("", record.getName());
        assertEquals("character:0000000a-0000-0000-0000-00000000000a",
                record.getNamedAfter());
        assertTrue(record.isCalledAfter(nils));
        assertEquals(nils, record.getOwnerCharacterId());
        assertEquals("", record.getCategoryName());
        assertEquals("", record.getDescription());
        assertEquals("the server's own reading keeps the id",
                record.getNamedAfter(), record.toDefinition().getNamedAfter());
        assertEquals("player:Nils", record.toDefinition(names(nils, "Nils"))
                .getNamedAfter());
        assertEquals("a rename reaches it", "player:Nils the Bold",
                record.toDefinition(names(nils, "Nils the Bold"))
                        .getNamedAfter());
    }

    /** A record keeps nothing but the placing character's id: a name only travels. */
    @Test(expected = IllegalArgumentException.class)
    public void aRecordKeepsNoName() {
        LostTalesMapMarkerRecord.builder("losttales:player/abc",
                LostTalesMapMarkerSource.PLAYER_CREATED)
                .namedAfter("player:Nils")
                .ownerCharacterId(UUID.randomUUID())
                .build();
    }

    @Test
    public void aCharacterIsKeptByItsIdAndSentByItsName() {
        UUID id = UUID.fromString("1234abcd-0000-4000-8000-00000000beef");
        String kept = LostTalesMapMarkerNamedAfter.character(id);
        assertEquals("character:1234abcd-0000-4000-8000-00000000beef", kept);
        assertTrue(LostTalesMapMarkerNamedAfter.isValid(kept));
        assertTrue(LostTalesMapMarkerNamedAfter.isKept(kept));
        assertTrue(LostTalesMapMarkerNamedAfter.isKept(""));
        assertFalse(LostTalesMapMarkerNamedAfter.isKept("player:Nils"));
        assertEquals(id, LostTalesMapMarkerNamedAfter.characterId(kept));
        assertEquals("", LostTalesMapMarkerNamedAfter.character(null));
        assertFalse("only the id's own form",
                LostTalesMapMarkerNamedAfter.isValid("character:Nils"));
        assertFalse(LostTalesMapMarkerNamedAfter.isValid(
                "character:1234ABCD-0000-4000-8000-00000000BEEF"));
        assertEquals("player:Nils",
                LostTalesMapMarkerNamedAfter.shown(kept, names(id, "Nils")));
        assertEquals("every other reference is sent as it is",
                "entity:losttales.Nia", LostTalesMapMarkerNamedAfter.shown(
                        "entity:losttales.Nia", names(id, "Nils")));
        assertEquals("a character nobody can name stays as it is kept", kept,
                LostTalesMapMarkerNamedAfter.shown(kept, names(id, null)));
        assertEquals(kept, LostTalesMapMarkerNamedAfter.shown(kept, null));
        assertEquals("a name past a reference's bounds is never sent", kept,
                LostTalesMapMarkerNamedAfter.shown(kept, names(id, "a§cb")));
    }

    /** A waystone whose placing character is purged reads Waystone in every game. */
    @Test
    public void aWaystoneOfAPurgedCharacterReadsWaystone() {
        String kept = LostTalesMapMarkerNamedAfter.character(UUID.randomUUID());
        assertEquals("Waystone", LostTalesMapMarkerNamedAfter.shownName(kept));
        assertEquals("Waystone", clientMarker("", "", "", kept).getName());
        IChatComponent name = LostTalesMapMarkerNames.component(
                "losttales:player/abc", "", kept);
        assertTrue(name instanceof ChatComponentTranslation);
        assertEquals("Waystone", EnglishWords.INSTANCE.read(name));
        inject(LostTalesMapMarkerNamedAfter.UNKNOWN_PLACER_KEY + "=Wegstein\n");
        assertEquals("Wegstein", LostTalesMapMarkerNamedAfter.shownName(kept));
    }

    private static LostTalesMapMarkerNamedAfter.Names names(
            final UUID id, final String name) {
        return new LostTalesMapMarkerNamedAfter.Names() {
            @Override
            public String nameOf(UUID characterId) {
                return id.equals(characterId) ? name : null;
            }
        };
    }

    @Test
    public void theSnapshotCarriesWhatAMarkerIsCalledAfter() {
        LostTalesMapMarkerDefinition marker = definition("player:Nils");
        LostTalesMapMarkerSnapshotPacket decoded = roundTrip(
                new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                        Collections.singletonList(marker)));
        assertFalse(decoded.isMalformed());
        assertEquals("", decoded.getMarkers().get(0).getName());
        assertEquals("player:Nils", decoded.getMarkers().get(0).getNamedAfter());

        ByteBuf corrupt = Unpooled.buffer();
        new LostTalesMapMarkerSnapshotPacket(1, 0, 1,
                Collections.singletonList(marker)).toBytes(corrupt);
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
