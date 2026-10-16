package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.client.window.WindowLayout;
import com.ninuna.losttales.client.window.WindowLayoutStore;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Who each copy speaks as is kept per place in the layout file and comes
 * back at the next visit there; a place's conversations outlast leaving
 * it, through the save that follows.
 */
public final class ChatVoicesKeptTest {
    private static final UUID OWNER = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ARAGORN = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final UUID LEGOLAS = UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    private final ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);

    @Before
    public void setUp() {
        TwoWindowLayout.reset();
        ClientChatIdentities.clear();
        ClientCharacterRosterCache.clear();
        roster(ARAGORN, ARAGORN, LEGOLAS);
    }

    @After
    public void tearDown() {
        WindowLayout.setChangeListener(null);
        ChatLayout.reset();
        ClientChatIdentities.clear();
        ClientCharacterRosterCache.clear();
    }

    @Test
    public void aCopysVoiceIsKeptPerPlaceAndComesBack() {
        ChatLayout.restoreConversations("play.example.org");
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        List<String> written = WindowLayoutStore.describe();
        assertTrue(written.toString(), written.contains("voice\tplay.example.org\tglobal\t"
                + LEGOLAS + "\t-"));

        // A restart: the file is read afresh, and the place visited again.
        ClientChatIdentities.clear();
        WindowLayoutStore.load(written);
        ChatLayout.restoreConversations("play.example.org");
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(written, WindowLayoutStore.describe());

        // Another place follows the character played.
        ChatLayout.closeConversations();
        ClientChatIdentities.clear();
        ChatLayout.restoreConversations("other.example.org");
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);

        ChatLayout.closeConversations();
        ClientChatIdentities.clear();
        ChatLayout.restoreConversations("play.example.org");
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(global).characterId);
    }

    /** A kept character the roster no longer holds reads as the character played. */
    @Test
    public void aKeptCharacterThatIsGoneFallsBack() {
        ClientChatIdentities.readVoice(new String[] {"voice", "play.example.org", "global",
                UUID.randomUUID().toString(), "-"});
        ChatLayout.restoreConversations("play.example.org");
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT,
                ClientChatIdentities.wireKind(global));
    }

    @Test
    public void aMalformedVoiceLineIsSkipped() {
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "global", LEGOLAS.toString()});
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "global", "not-an-id", "-"});
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "npc:Barliman", LEGOLAS.toString(), "-"});
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "no such tab", LEGOLAS.toString(), "-"});
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "global", "-", "-"});
        ClientChatIdentities.readVoice(new String[] {"voice", "p", "global", LEGOLAS.toString(), "loud"});
        ClientChatIdentities.readVoice(new String[] {"voice", "", "global", LEGOLAS.toString(), "-"});
        List<String> lines = new ArrayList<String>();
        ClientChatIdentities.describeVoices(lines);
        assertEquals(Collections.<String>emptyList(), lines);
    }

    /** Leaving a place keeps its whisper tabs, even when the layout is saved right after. */
    @Test
    public void aPlacesConversationsOutliveTheSaveAfterLeaving() {
        ChatLayout.restoreConversations("play.example.org");
        ConversationPage steve = ConversationPage.whisper("Steve", "Aldric");
        assertNotNull(WindowLayout.openTab(steve, "w2"));
        ChatLayout.closeConversations();
        WindowLayoutStore.describe();
        assertFalse(WindowLayout.isOpen(steve));
        ChatLayout.restoreConversations("play.example.org");
        assertTrue(WindowLayout.isOpen(steve));
    }

    private static ClientChatIdentities.Identity identityOf(UUID characterId) {
        return new ClientChatIdentities.Identity(false, characterId,
                characterId.equals(ARAGORN) ? "Aragorn" : "Legolas", "skin");
    }

    private static void roster(UUID active, UUID... characters) {
        List<CharacterSummary> summaries = new ArrayList<CharacterSummary>();
        for (UUID id : characters) {
            summaries.add(new CharacterSummary(id, summaries.size(),
                    id.equals(ARAGORN) ? "Aragorn" : "Legolas", "human", "male",
                    "skin", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                    RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, "GONDOR", "", ""));
        }
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                OWNER, summaries.size(), active, 1L,
                Arrays.asList(summaries.toArray(new CharacterSummary[summaries.size()])),
                Collections.<DeletedCharacterSummary>emptyList()));
    }
}
