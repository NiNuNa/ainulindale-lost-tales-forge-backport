package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Whether a world has read the account's template is one roster key newer
 * than the roster layout. A roster written before it has not read one, so
 * a world that already made a default character still takes the template
 * on the next login rather than never.
 */
public final class CharacterTemplateTakenMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "93000000-0000-0000-0000-000000000039");

    @Test
    public void aRosterWithoutTheKeyHasNotTakenTheTemplate() {
        NBTTagCompound root = new NBTTagCompound();
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.markTemplateTaken());
        CharacterNbtCodec.write(root, Collections.singletonList(roster));
        rosterTag(root).removeTag("TemplateTaken");

        assertFalse(readRoster(root).isTemplateTaken());
    }

    @Test
    public void aTakenTemplateSurvivesTheRoundTrip() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertFalse(roster.isTemplateTaken());
        assertTrue(roster.markTemplateTaken());
        // Taking it twice is not a second reading.
        assertFalse(roster.markTemplateTaken());
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root, Collections.singletonList(roster));

        assertTrue(readRoster(root).isTemplateTaken());
    }

    @Test
    public void anUntakenTemplateSurvivesTheRoundTrip() {
        NBTTagCompound root = new NBTTagCompound();
        CharacterNbtCodec.write(root,
                Collections.singletonList(new CharacterRoster(OWNER)));

        assertFalse(readRoster(root).isTemplateTaken());
    }

    private static NBTTagCompound rosterTag(NBTTagCompound root) {
        NBTTagList rosters = root.getTagList("Rosters", 10);
        assertEquals(1, rosters.tagCount());
        return rosters.getCompoundTagAt(0);
    }

    private static CharacterRoster readRoster(NBTTagCompound root) {
        CharacterNbtCodec.ReadResult result = CharacterNbtCodec.read(root);
        assertFalse(result.isReadOnly());
        CharacterRoster roster = result.getRosters().get(OWNER);
        assertNotNull(roster);
        return roster;
    }
}
