package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.ChatPresenceIdentity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * The status kept with a player's data reads back as it was chosen;
 * nothing, or anything this build does not know, reads as Online, so a
 * bad value only ever says a join line.
 */
public final class ChatPresenceRecordTest {

    @Test
    public void aChosenStatusIsKeptAndOnlineKeepsNothing() {
        NBTTagCompound persisted = new NBTTagCompound();
        assertEquals(ChatPresence.ONLINE, ChatPresenceRecord.read(persisted));
        ChatPresenceRecord.write(persisted, ChatPresence.INVISIBLE);
        assertEquals(ChatPresence.INVISIBLE, ChatPresenceRecord.read(persisted));
        ChatPresenceRecord.write(persisted, ChatPresence.ONLINE);
        assertFalse(persisted.hasKey(ChatPresenceRecord.TAG));
    }

    @Test
    public void anUnknownOrUnchoosableStatusReadsAsOnline() {
        NBTTagCompound persisted = new NBTTagCompound();
        persisted.setString(ChatPresenceRecord.TAG, "offline");
        assertEquals(ChatPresence.ONLINE, ChatPresenceRecord.read(persisted));
        persisted.setString(ChatPresenceRecord.TAG, "bogus");
        assertEquals(ChatPresence.ONLINE, ChatPresenceRecord.read(persisted));
        persisted.setInteger(ChatPresenceRecord.TAG, 3);
        assertEquals(ChatPresence.ONLINE, ChatPresenceRecord.read(persisted));
        assertEquals(ChatPresence.ONLINE, ChatPresenceRecord.read((NBTTagCompound) null));
    }

    /** What counts is the identity played: an Invisible account is not an Invisible character. */
    @Test
    public void theStatusOfTheIdentityPlayedCounts() {
        ChatPresenceIdentity aldric = ChatPresenceIdentity.character(UUID.randomUUID());
        Map<ChatPresenceIdentity, ChatPresence> chosen =
                new HashMap<ChatPresenceIdentity, ChatPresence>();
        chosen.put(ChatPresenceIdentity.ACCOUNT, ChatPresence.INVISIBLE);
        assertEquals(ChatPresence.ONLINE, ChatPresenceService.playedChoice(chosen, aldric));
        chosen.put(aldric, ChatPresence.INVISIBLE);
        assertEquals(ChatPresence.INVISIBLE, ChatPresenceService.playedChoice(chosen, aldric));
        assertEquals(ChatPresence.ONLINE, ChatPresenceService.playedChoice(null, aldric));
    }
}
