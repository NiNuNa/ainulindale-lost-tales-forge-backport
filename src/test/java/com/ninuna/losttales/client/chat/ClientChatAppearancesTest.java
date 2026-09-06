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
 * Every tab follows the active identity unless it is locked to one of
 * its own; a lock belongs to one tab, survives the active character
 * changing, and gives way when its character leaves the roster.
 */
public final class ClientChatAppearancesTest {

    private static final UUID OWNER = UUID.fromString("a0000000-0000-0000-0000-00000000000a");
    private static final UUID ARAGORN = UUID.fromString("b0000000-0000-0000-0000-00000000000b");
    private static final UUID LEGOLAS = UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    private final ChatTab global = ChatTab.of(ChatChannel.ALL);
    private final ChatTab proximity = ChatTab.of(ChatChannel.PROXIMITY);
    private final ChatTab ooc = ChatTab.of(ChatChannel.OOC);

    @Before
    public void setUp() {
        ClientChatAppearances.clear();
        ClientCharacterRosterCache.clear();
    }

    @After
    public void tearDown() {
        ClientChatAppearances.clear();
        ClientCharacterRosterCache.clear();
    }

    @Test
    public void everyTabFollowsTheActiveIdentityByDefault() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(proximity).characterId);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(ooc).characterId);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                ClientChatAppearances.wireKind(global));
        assertNull(ClientChatAppearances.wireCharacterId(global));
        assertFalse(ClientChatAppearances.isLocked(global));

        // Switching the active character switches every unlocked tab.
        roster(LEGOLAS, ARAGORN, LEGOLAS);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(proximity).characterId);

        // With nobody active, the account speaks everywhere.
        roster(null, ARAGORN, LEGOLAS);
        assertTrue(ClientChatAppearances.effectiveFor(global).account);
    }

    /** The scenario the lock exists for. */
    @Test
    public void aLockedTabKeepsItsIdentityWhileTheActiveOneChanges() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatAppearances.toggleLocked(global);
        assertTrue(ClientChatAppearances.isLocked(global));
        assertFalse(ClientChatAppearances.isLocked(proximity));

        roster(LEGOLAS, ARAGORN, LEGOLAS);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(proximity).characterId);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_CHARACTER,
                ClientChatAppearances.wireKind(global));
        assertEquals(ARAGORN, ClientChatAppearances.wireCharacterId(global));
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                ClientChatAppearances.wireKind(proximity));

        // A tab switch leaves the lock where it is.
        ClientChatAppearances.onChannelSwitched();
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(global).characterId);

        // Unlocking, Global follows the active character again.
        ClientChatAppearances.toggleLocked(global);
        assertFalse(ClientChatAppearances.isLocked(global));
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                ClientChatAppearances.wireKind(global));
    }

    @Test
    public void aPassingChoiceHoldsForItsTabUntilTheNextSwitch() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatAppearances.select(appearanceOf(LEGOLAS), global);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(proximity).characterId);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_CHARACTER,
                ClientChatAppearances.wireKind(global));
        assertTrue(ClientChatAppearances.isEffective(appearanceOf(LEGOLAS), global));
        assertFalse(ClientChatAppearances.isEffective(appearanceOf(LEGOLAS), proximity));

        ClientChatAppearances.onChannelSwitched();
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(global).characterId);

        // Choosing the account on an in-character tab, then locking it,
        // makes the account that tab's own.
        ClientChatAppearances.select(ClientChatAppearances.accountAppearance(), global);
        ClientChatAppearances.toggleLocked(global);
        ClientChatAppearances.onChannelSwitched();
        assertTrue(ClientChatAppearances.effectiveFor(global).account);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_ACCOUNT,
                ClientChatAppearances.wireKind(global));
        assertNull(ClientChatAppearances.wireCharacterId(global));

        // A choice on a locked tab changes the lock itself.
        ClientChatAppearances.select(appearanceOf(LEGOLAS), global);
        ClientChatAppearances.onChannelSwitched();
        assertTrue(ClientChatAppearances.isLocked(global));
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(global).characterId);
    }

    /** The Roadmap's item: a lock outlived by its character falls back. */
    @Test
    public void aLockNamingADeletedCharacterGivesWayToTheDefault() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatAppearances.select(appearanceOf(LEGOLAS), global);
        ClientChatAppearances.toggleLocked(global);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(global).characterId);

        roster(ARAGORN, ARAGORN);
        assertFalse(ClientChatAppearances.isLocked(global));
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(global).characterId);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_DEFAULT,
                ClientChatAppearances.wireKind(global));

        // A passing choice of a deleted character gives way the same.
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatAppearances.select(appearanceOf(LEGOLAS), proximity);
        roster(ARAGORN, ARAGORN);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(proximity).characterId);
        assertNull(ClientChatAppearances.wireCharacterId(proximity));
    }

    @Test
    public void leavingTheWorldForgetsEveryLock() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        ClientChatAppearances.toggleLocked(global);
        ClientChatAppearances.select(appearanceOf(LEGOLAS), ooc);
        ClientChatAppearances.clear();
        assertFalse(ClientChatAppearances.isLocked(global));
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(ooc).characterId);
        // Nothing to lock without a tab; nothing breaks either.
        ClientChatAppearances.toggleLocked(null);
        ClientChatAppearances.select(appearanceOf(LEGOLAS), null);
        assertFalse(ClientChatAppearances.isLocked(null));
    }

    /**
     * A conversation is spoken in as the identity it is held as: the
     * account's conversations as the account whatever is played, a
     * character's as that character, until the roster loses it.
     */
    @Test
    public void aConversationSpeaksAsTheIdentityItIsHeldAs() {
        roster(ARAGORN, ARAGORN, LEGOLAS);
        assertEquals(ChatTab.ownerKeyOf(ARAGORN),
                ClientChatAppearances.activeIdentityKey());
        ChatTab asAccount = ChatTab.whisper("Steve");
        ChatTab asLegolas = ChatTab.whisper("Steve", "",
                ChatTab.ownerKeyOf(LEGOLAS));
        assertTrue(ClientChatAppearances.effectiveFor(asAccount).account);
        assertEquals(LostTalesChatSendPacket.APPEARANCE_ACCOUNT,
                ClientChatAppearances.wireKind(asAccount));
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(asLegolas).characterId);
        assertEquals(LEGOLAS, ClientChatAppearances.wireCharacterId(asLegolas));
        // A lock on the tab does not move a conversation off its identity.
        ClientChatAppearances.select(appearanceOf(ARAGORN), asLegolas);
        ClientChatAppearances.toggleLocked(asLegolas);
        assertEquals(LEGOLAS, ClientChatAppearances.effectiveFor(asLegolas).characterId);
        // Gone from the roster, the conversation falls back to the default.
        roster(ARAGORN, ARAGORN);
        assertEquals(ARAGORN, ClientChatAppearances.effectiveFor(asLegolas).characterId);
        // With no character active, the account is the active identity.
        roster(null, ARAGORN);
        assertEquals("", ClientChatAppearances.activeIdentityKey());
        ClientCharacterRosterCache.clear();
        assertEquals("", ClientChatAppearances.activeIdentityKey());
    }

    private static ClientChatAppearances.Appearance appearanceOf(UUID characterId) {
        return new ClientChatAppearances.Appearance(false, characterId,
                characterId.equals(ARAGORN) ? "Aragorn" : "Legolas", "skin");
    }

    private static void roster(UUID active, UUID... characters) {
        java.util.List<CharacterSummary> summaries = new java.util.ArrayList<CharacterSummary>();
        for (UUID id : characters) {
            summaries.add(new CharacterSummary(id, summaries.size(),
                    id.equals(ARAGORN) ? "Aragorn" : "Legolas", "human", "male",
                    "skin", 30, "GONDOR", 1, 0L, 1L,
                    RoleplayCharacter.CURRENT_DATA_VERSION));
        }
        ClientCharacterRosterCache.acceptRoster(0, new CharacterRosterSnapshot(
                OWNER, Math.max(1, summaries.size()), active, 1L,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                summaries.isEmpty() ? Collections.<CharacterSummary>emptyList()
                        : Arrays.asList(summaries.toArray(
                                new CharacterSummary[summaries.size()]))));
    }
}
