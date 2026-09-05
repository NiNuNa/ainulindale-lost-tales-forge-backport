package com.ninuna.losttales.compat.lotr.hired;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/** The tag survives the entity's data round trip and refuses what it cannot read. */
public final class LotrHiredUnitTagTest {

    private static final UUID OWNER = UUID.fromString(
            "a5000000-0000-0000-0000-00000000005a");
    private static final UUID CHARACTER = UUID.fromString(
            "b5000000-0000-0000-0000-00000000005b");

    @Test
    public void aCharacterTagRoundTripsAndKeysOnTheCharacter() {
        NBTTagCompound data = new NBTTagCompound();
        LotrHiredUnitTag.of(PlayableIdentity.character(OWNER, CHARACTER)).write(data);
        LotrHiredUnitTag read = LotrHiredUnitTag.read(data);
        assertNotNull(read);
        assertEquals(OWNER, read.getOwnerId());
        assertEquals(CHARACTER, read.getCharacterId());
        assertEquals(CHARACTER.toString(), read.identityKey());
    }

    @Test
    public void theAccountTagCarriesNoCharacterAndOverwritesOne() {
        NBTTagCompound data = new NBTTagCompound();
        LotrHiredUnitTag.of(PlayableIdentity.character(OWNER, CHARACTER)).write(data);
        LotrHiredUnitTag.of(PlayableIdentity.account(OWNER)).write(data);
        LotrHiredUnitTag read = LotrHiredUnitTag.read(data);
        assertNotNull(read);
        assertNull(read.getCharacterId());
        assertEquals(LotrHiredUnitTag.ACCOUNT_KEY, read.identityKey());
        assertFalse(data.hasKey(LotrHiredUnitTag.KEY_CHARACTER));
        assertEquals(LotrHiredUnitTag.ACCOUNT_KEY,
                LotrHiredUnitTag.identityKey(PlayableIdentity.account(OWNER)));
        assertNull(LotrHiredUnitTag.identityKey((PlayableIdentity)null));
    }

    @Test
    public void aDeletedCharacterLeavesTheAccountsTag() {
        LotrHiredUnitTag tag = LotrHiredUnitTag.of(
                PlayableIdentity.character(OWNER, CHARACTER)).asAccount();
        assertEquals(OWNER, tag.getOwnerId());
        assertNull(tag.getCharacterId());
    }

    @Test
    public void unreadableDataIsNoTag() {
        assertNull(LotrHiredUnitTag.read((NBTTagCompound)null));
        assertNull(LotrHiredUnitTag.read(new NBTTagCompound()));
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString(LotrHiredUnitTag.KEY_ACCOUNT, "not-a-uuid");
        assertNull(LotrHiredUnitTag.read(malformed));
        NBTTagCompound halfBroken = new NBTTagCompound();
        halfBroken.setString(LotrHiredUnitTag.KEY_ACCOUNT, OWNER.toString());
        halfBroken.setString(LotrHiredUnitTag.KEY_CHARACTER, "garbage");
        LotrHiredUnitTag read = LotrHiredUnitTag.read(halfBroken);
        assertNotNull(read);
        assertNull(read.getCharacterId());
    }
}
