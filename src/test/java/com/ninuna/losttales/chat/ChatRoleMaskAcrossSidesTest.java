package com.ninuna.losttales.chat;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An integrated server shares its JVM with its client, which resets the
 * installed role catalogue as it connects and is told the server's again
 * only after the login replay has gone out. A kept line wearing a config
 * role has to stay sendable through that gap, or the encoder drops the
 * connection on the first join after a restart.
 */
public final class ChatRoleMaskAcrossSidesTest {

    @After
    public void tearDown() {
        ChatRoleCatalog.installServer(null);
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void aRoleTheServerKnowsStaysValidWhileTheClientHasForgottenIt() {
        ChatRoleCatalog.installServer(ChatRoleFixtures.catalogue());
        int operator = ChatRoleFixtures.OPERATOR.bit();
        assertTrue(ChatAccountRole.isValidMask(operator));
        LostTalesChatMessagePacket kept = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, UUID.randomUUID(), "Aldric", "alice", "", 0, 0,
                "the gate holds", 1000000L, "", null, "", "", operator, false, 7L,
                ChatReplyReference.NONE, "");
        // The client arrives and forgets what it was told.
        ChatRoleCatalog.resetToBuiltIn();
        assertTrue(ChatAccountRole.isValidMask(operator));
        assertTrue(kept.isWellFormed());
        // A bit no catalogue knows is still refused.
        assertFalse(ChatAccountRole.isValidMask(1 << 20));
    }

    @Test
    public void aClientAloneKnowsOnlyWhatItWasTold() {
        ChatRoleCatalog.installServer(null);
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
        assertTrue(ChatAccountRole.isValidMask(ChatRoleFixtures.OPERATOR.bit()));
        ChatRoleCatalog.resetToBuiltIn();
        assertFalse(ChatAccountRole.isValidMask(ChatRoleFixtures.OPERATOR.bit()));
        assertTrue(ChatAccountRole.isValidMask(ChatAccountRole.TEAM.bit()));
    }
}
