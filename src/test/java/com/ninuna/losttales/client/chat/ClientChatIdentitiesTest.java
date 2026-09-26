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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Shared roleplaying identity, independent from gameplay and account channels. */
public final class ClientChatIdentitiesTest {

    private static final UUID OWNER = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ARAGORN = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final UUID LEGOLAS = UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    private final ChatTab global = ChatTab.of(ChatChannel.GLOBAL);
    private final ChatTab proximity = ChatTab.of(ChatChannel.PROXIMITY);
    private final ChatTab ooc = ChatTab.of(ChatChannel.OOC);

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
    public void selectionAppliesToEveryRoleplayingChannelAndNeverToAccountChannels() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(identityOf(LEGOLAS));
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.GLOBAL, ChatChannel.PROXIMITY,
                ChatChannel.FACTION, ChatChannel.PARTY, ChatChannel.WHISPER}) {
            ChatTab tab = channel == ChatChannel.WHISPER
                    ? ChatTab.whisper("Steve", "Steve", LEGOLAS.toString()) : ChatTab.of(channel);
            assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(tab).characterId);
            assertEquals(LEGOLAS, ClientChatIdentities.wireCharacterId(tab));
        }
        assertEquals(LEGOLAS, ClientChatIdentities.effectiveFor(ChatTab.npc("Guard")).characterId);
        for (ChatChannel channel : new ChatChannel[] {ChatChannel.OOC, ChatChannel.OPERATOR, ChatChannel.CLIENT_CONSOLE}) {
            assertTrue(ClientChatIdentities.effectiveFor(ChatTab.of(channel)).account);
            assertEquals(LostTalesChatSendPacket.IDENTITY_ACCOUNT,
                    ClientChatIdentities.wireKind(ChatTab.of(channel)));
            assertNull(ClientChatIdentities.wireCharacterId(ChatTab.of(channel)));
        }
        assertEquals(ARAGORN, ClientCharacterRosterCache.getSnapshot().getActiveCharacterId());
    }

    @Test
    public void selectionSurvivesChannelAndGameplayChanges() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(identityOf(ARAGORN));
        roster(LEGOLAS, ARAGORN, LEGOLAS);
        ClientChatChannelState.select(ooc);
        ClientChatChannelState.select(global);
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(proximity).characterId);
        assertEquals(ARAGORN.toString(), ClientChatIdentities.viewIdentityKey());
    }

    @Test
    public void removedOrUnownedCharactersCannotRemainSelected() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(identityOf(LEGOLAS));
        roster(ARAGORN, ARAGORN);
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        ClientChatIdentities.select(identityOf(LEGOLAS));
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(proximity).characterId);
    }

    /**
     * The account is never a choice: choosing it changes nothing, and the
     * roleplaying channels speak as it only while no character is held.
     */
    @Test
    public void theAccountIsAFallbackAndNeverAChoice() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(ClientChatIdentities.accountIdentity());
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
        ClientCharacterRosterCache.clear();
        assertTrue(ClientChatIdentities.effectiveFor(global).account);
        assertTrue(ClientChatIdentities.effectiveFor(proximity).account);
        assertEquals("", ClientChatIdentities.viewIdentityKey());
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
    }

    @Test
    public void disconnectDropsTheChatSelectionAndInitiallyFollowsThePlayedCharacter() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatIdentities.select(identityOf(LEGOLAS));
        ClientChatIdentities.clear();
        assertEquals(ARAGORN, ClientChatIdentities.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT, ClientChatIdentities.wireKind(global));
        assertNull(ClientChatIdentities.wireCharacterId(global));
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
                    RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, 30, "GONDOR", 1, 0L, 1L,
                    RoleplayCharacter.CURRENT_DATA_VERSION, "", ""));
        }
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                OWNER, Math.max(1, summaries.size()), active, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                summaries.isEmpty() ? Collections.<CharacterSummary>emptyList()
                        : Arrays.asList(summaries.toArray(
                                new CharacterSummary[summaries.size()])),
                RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE,
                RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID, true));
    }
}
