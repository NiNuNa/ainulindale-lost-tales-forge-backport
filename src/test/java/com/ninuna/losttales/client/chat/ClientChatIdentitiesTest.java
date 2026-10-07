package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.network.packet.LostTalesChatSendPacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Who each copy of a conversation speaks as: its own choice in Global,
 * Faction and whispers, independent from the other copies, from gameplay
 * and from the account channels.
 */
public final class ClientChatIdentitiesTest {

    private static final UUID OWNER = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ARAGORN = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final UUID LEGOLAS = UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    private final ConversationPage global = ConversationPage.of(ChatChannel.GLOBAL);
    private final ConversationPage proximity = ConversationPage.of(ChatChannel.PROXIMITY);
    private final ConversationPage ooc = ConversationPage.of(ChatChannel.OOC);

    @Before
    public void setUp() {
        ClientChatIdentities.clear();
        ClientCharacterRosterCache.clear();
    }

    @After
    public void tearDown() {
        ClientChatIdentities.clear();
        ClientCharacterRosterCache.clear();
    }

    @Test
    public void eachCopyChoosesForItselfInGlobalFactionAndWhispers() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ConversationPage faction = ConversationPage.of(ChatChannel.FACTION);
        ConversationPage whisper = ConversationPage.whisper("Steve", "Steve");
        for (ConversationPage tab : new ConversationPage[] {global, faction, whisper}) {
            ClientChatIdentities.select(tab, identityOf(LEGOLAS));
            assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(tab).characterId);
            assertEquals(LEGOLAS, ClientChatIdentities.wireCharacterId(tab));
            ConversationPage second = tab.withInstance(2);
            assertEquals("another copy is another person",
                    ARAGORN, ClientChatIdentities.effectiveFor(second).characterId);
            assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT,
                    ClientChatIdentities.wireKind(second));
        }
        // The world around the player and the fellowship hear the character played.
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.PROXIMITY, ChatChannel.FELLOWSHIP}) {
            ConversationPage tab = ConversationPage.of(channel);
            ClientChatIdentities.select(tab, identityOf(LEGOLAS));
            assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(tab).characterId);
            assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT,
                    ClientChatIdentities.wireKind(tab));
            assertNull(ClientChatIdentities.wireCharacterId(tab));
        }
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.OPERATOR, ChatChannel.CLIENT_CONSOLE}) {
            assertTrue(ClientChatIdentities.effectiveFor(ConversationPage.of(channel)).account);
            assertEquals(LostTalesChatSendPacket.IDENTITY_ACCOUNT,
                    ClientChatIdentities.wireKind(ConversationPage.of(channel)));
            assertNull(ClientChatIdentities.wireCharacterId(ConversationPage.of(channel)));
        }
        assertEquals(ARAGORN, ClientCharacterRosterCache.getSnapshot().getActiveCharacterId());
    }

    @Test
    public void aChoiceSurvivesChannelAndGameplayChanges() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(global, identityOf(ARAGORN));
        roster(LEGOLAS, ARAGORN, LEGOLAS);
        ClientChatChannelState.select(ooc);
        ClientChatChannelState.select(global);
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(proximity).characterId);
        assertEquals(ARAGORN.toString(), ClientChatIdentities.viewIdentityKey(global));
    }

    @Test
    public void removedOrUnownedCharactersCannotRemainChosen() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        roster(ARAGORN, ARAGORN);
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
    }

    /**
     * The account is never a choice: choosing it changes nothing, and the
     * roleplaying channels speak as it only while no character is held.
     */
    @Test
    public void theAccountIsAFallbackAndNeverAChoice() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(global, ClientChatIdentities.accountIdentity());
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
        ClientCharacterRosterCache.clear();
        assertTrue(ClientChatIdentities.effectiveFor(global).account);
        assertTrue(ClientChatIdentities.effectiveFor(proximity).account);
        assertEquals("", ClientChatIdentities.viewIdentityKey(global));
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
    }

    @Test
    public void disconnectDropsEveryChoiceAndCopiesFollowThePlayedCharacter() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        ClientChatIdentities.clear();
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
        assertNull(ClientChatIdentities.wireCharacterId(global));
    }

    /** A duplicate speaks as its source; a copy opening anew as the character played. */
    @Test
    public void aDuplicateSpeaksAsItsSourceAndANewCopyAsThePlayedCharacter() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ConversationPage second = global.withInstance(2);
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        ClientChatIdentities.inherit(global, second);
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(second).characterId);
        ClientChatIdentities.forget(second);
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(second).characterId);
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(global).characterId);
    }

    /** The Narrator's voice is taken up in one copy, and only in character. */
    @Test
    public void theNarratorsVoiceIsTakenUpPerCopy() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.setNarrating(global, true);
        assertTrue(ClientChatIdentities.narratesOn(global));
        assertTrue(ClientChatIdentities.wireNarrating(global));
        assertFalse(ClientChatIdentities.narratesOn(global.withInstance(2)));
        ClientChatIdentities.setNarrating(ooc, true);
        assertFalse("an account channel never narrates",
                ClientChatIdentities.wireNarrating(ooc));
        ClientChatIdentities.select(global, identityOf(LEGOLAS));
        assertFalse("choosing a character puts the voice down",
                ClientChatIdentities.narratesOn(global));
    }

    private static ClientChatIdentities.Identity identityOf(UUID characterId) {
        return new ClientChatIdentities.Identity(false, characterId,
                characterId.equals(ARAGORN) ? "Aragorn" : "Legolas", "skin");
    }

    private static void roster(UUID active, UUID... characters) {
        java.util.List<CharacterSummary> summaries = new java.util.ArrayList<CharacterSummary>();
        for (UUID id : characters) {
            summaries.add(new CharacterSummary(id, summaries.size(),
                    id.equals(ARAGORN) ? "Aragorn" : "Legolas", "human", "male",
                    "skin", RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                    RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, "GONDOR", "", ""));
        }
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                OWNER, Math.max(1, summaries.size()), active, 1L,
                summaries.isEmpty() ? Collections.<CharacterSummary>emptyList()
                        : Arrays.asList(summaries.toArray(
                                new CharacterSummary[summaries.size()])),
                Collections.<com.ninuna.losttales.character.sync.DeletedCharacterSummary>emptyList()));
    }
}
