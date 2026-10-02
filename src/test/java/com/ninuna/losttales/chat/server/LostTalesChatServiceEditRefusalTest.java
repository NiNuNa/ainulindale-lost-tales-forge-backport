package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleConfig;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * An edit puts new words in front of a conversation's readers as a send
 * does, so its author has to be able to send there now: past the gate,
 * still in the fellowship, still speaking to the faction the line was
 * said to. Each refusal is the notice a send would be refused with.
 */
public final class LostTalesChatServiceEditRefusalTest {
    private static final UUID ALDRIC = UUID.randomUUID();
    private static final String GONDOR = "lotr:gondor";
    private static final String FELLOWSHIP_REFUSAL = "chat.losttales.channel.fellowship_unavailable";
    private static final String GATE_REFUSAL = "chat.losttales.channel.role_unavailable";

    @After
    public void tearDown() {
        ChatChannelGates.install(ChatChannelGates.defaults());
        ChatRoleCatalog.resetToBuiltIn();
    }

    /** An author who may still send where the line was said may edit it. */
    @Test
    public void anAuthorWhoMayStillSendThereMayEdit() {
        assertNull(LostTalesChatService.editRefusal(ChatChannel.GLOBAL, "",
                ChatChannelPolicy.sendRefusal(ChatChannel.GLOBAL, null, ALDRIC,
                        0, false, false), GONDOR));
        assertNull(LostTalesChatService.editRefusal(ChatChannel.FACTION, GONDOR,
                ChatChannelPolicy.sendRefusal(ChatChannel.FACTION, null, ALDRIC,
                        0, false, false), GONDOR));
    }

    /** An author who left the fellowship is refused as a send to it would be. */
    @Test
    public void anAuthorWhoLeftTheFellowshipIsRefused() {
        // The fellowship the line names is none of the played identity's now.
        assertEquals(FELLOWSHIP_REFUSAL, LostTalesChatService.editRefusal(
                ChatChannel.FELLOWSHIP, UUID.randomUUID().toString(),
                ChatChannelPolicy.sendRefusal(ChatChannel.FELLOWSHIP, null, ALDRIC,
                        0, false, false), GONDOR));
    }

    /** An author who lost the channel's role is refused by its gate. */
    @Test
    public void anAuthorWhoLostTheGateIsRefused() {
        ChatRoleCatalog catalog = ChatRoleConfig.parse(
                new String[] {ChatRoleConfig.DEFAULT_OPERATOR_ENTRY}, null,
                ChatRoleConfig.SILENT);
        ChatRoleCatalog.installServer(catalog);
        ChatChannelGates.install(ChatRoleConfig.parseGates(
                new String[] {"operator=read:operator;send:operator"}, catalog,
                ChatRoleConfig.SILENT));
        assertEquals(GATE_REFUSAL, LostTalesChatService.editRefusal(
                ChatChannel.OPERATOR, "", ChatChannelPolicy.sendRefusal(
                        ChatChannel.OPERATOR, null, ALDRIC, 0, false, false),
                GONDOR));
        assertNull(LostTalesChatService.editRefusal(ChatChannel.OPERATOR, "",
                ChatChannelPolicy.sendRefusal(ChatChannel.OPERATOR, null, ALDRIC,
                        catalog.byId("operator").bit(), false, false), GONDOR));
    }

    /** An author now speaking to another faction may not rewrite what they said to the first. */
    @Test
    public void anAuthorSpeakingToAnotherFactionIsRefused() {
        assertEquals(LostTalesChatService.EDIT_FACTION_LEFT,
                LostTalesChatService.editRefusal(ChatChannel.FACTION, GONDOR, null,
                        "lotr:mordor"));
        assertEquals(LostTalesChatService.EDIT_FACTION_LEFT,
                LostTalesChatService.editRefusal(ChatChannel.FACTION, null, null,
                        "lotr:mordor"));
    }
}
