package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Every conversation goes by one code name: a channel's id, or its faction's. */
public final class ChatCodeNamesTest {

    @Before
    public void factions() {
        ChatCodeNames.installFactions(Arrays.asList(
                "lotr:gondor", "LOTR:High_Elf", "lotr:unaligned",
                "lotr:party", "lotr:bree-land", "lotr:"));
    }

    @After
    public void cleanUp() {
        ChatCodeNames.installFactions(Collections.<String>emptyList());
    }

    @Test
    public void everyBuiltInChannelIsNamedByItsFullNameInLowerCase() {
        assertEquals("global", ChatCodeNames.of(ChatChannel.GLOBAL, ""));
        assertEquals("proximity", ChatCodeNames.of(ChatChannel.PROXIMITY, ""));
        assertEquals("ooc", ChatCodeNames.of(ChatChannel.OOC, ""));
        assertEquals("party", ChatCodeNames.of(ChatChannel.PARTY, "some-party"));
        assertEquals("operator", ChatCodeNames.of(ChatChannel.OPERATOR, ""));
        assertEquals("whisper", ChatCodeNames.of(ChatChannel.WHISPER, ""));
        assertEquals("client_console", ChatCodeNames.of(ChatChannel.CLIENT_CONSOLE, ""));
        assertEquals("server_console", ChatCodeNames.of(ChatChannel.SERVER_CONSOLE, ""));
        assertNull(ChatCodeNames.of(null, ""));
    }

    @Test
    public void aFactionsChatIsNamedByTheFaction() {
        assertEquals("gondor", ChatCodeNames.of(ChatChannel.FACTION, "lotr:gondor"));
        assertEquals("high_elf", ChatCodeNames.of(ChatChannel.FACTION, "lotr:high_elf"));
        assertNull("the Faction channel alone names no conversation",
                ChatCodeNames.of(ChatChannel.FACTION, ""));
        assertNull(ChatCodeNames.of(ChatChannel.FACTION, "lotr:nowhere"));
    }

    @Test
    public void aCodeNameReadsBackAsItsConversationWhateverItsCase() {
        ChatCodeNames.Named global = ChatCodeNames.parse("Global");
        assertSame(ChatChannel.GLOBAL, global.channel);
        assertEquals("", global.scope);
        ChatCodeNames.Named gondor = ChatCodeNames.parse(" GONDOR ");
        assertSame(ChatChannel.FACTION, gondor.channel);
        assertEquals("lotr:gondor", gondor.scope);
        assertEquals("lotr:high_elf", ChatCodeNames.parse("high_elf").scope);
        assertNull("the kind of channel is no conversation",
                ChatCodeNames.parse("faction"));
        assertNull(ChatCodeNames.parse("all"));
        assertNull(ChatCodeNames.parse(""));
        assertNull(ChatCodeNames.parse(null));
        assertNull(ChatCodeNames.parse("gondorgondorgondorgondorx"));
    }

    @Test
    public void aFactionNameThatCouldNotBeTypedOrIsAChannelIsLeftOut() {
        assertTrue(ChatCodeNames.isFaction("Gondor"));
        assertFalse("a channel keeps its own name", ChatCodeNames.isFaction("party"));
        assertSame(ChatChannel.PARTY, ChatCodeNames.parse("party").channel);
        assertFalse(ChatCodeNames.isFaction("bree-land"));
        assertEquals(Arrays.asList("gondor", "high_elf", "unaligned"),
                ChatCodeNames.factionCodes());
    }
}
