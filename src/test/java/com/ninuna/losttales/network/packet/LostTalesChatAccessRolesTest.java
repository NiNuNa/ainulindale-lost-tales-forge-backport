package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The access packet states the roles apart by identity — the account's
 * own, each own character's own, and each roster holder's account roles
 * and played character — and the server's Proximity radius, after
 * everything else.
 */
public final class LostTalesChatAccessRolesTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    private static LostTalesChatAccessPacket statement(int played,
            int account, Map<UUID, Integer> own,
            LostTalesChatAccessPacket.RoleHolder holder, int radius) {
        return new LostTalesChatAccessPacket(false, played,
                holder == null
                        ? Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList()
                        : Collections.singletonList(holder),
                Collections.<UUID>emptyList(), ChatRoleCatalog.current().roles(),
                LostTalesChatAccessPacket.allChannelIds(),
                LostTalesChatAccessPacket.allChannelIds(), false, false,
                Collections.<String>emptyList(), account, own, radius);
    }

    private static LostTalesChatAccessPacket roundTrip(
            LostTalesChatAccessPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    @Test
    public void rolesTravelApartByIdentity() {
        int operator = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        int team = ChatAccountRole.TEAM.bit();
        UUID character = UUID.randomUUID();
        UUID played = UUID.randomUUID();
        LostTalesChatAccessPacket decoded = roundTrip(statement(
                operator | team, operator,
                Collections.singletonMap(character, Integer.valueOf(team)),
                new LostTalesChatAccessPacket.RoleHolder("Steve",
                        operator | team, operator, played), 64));
        assertFalse(decoded.isMalformed());
        assertEquals(operator | team, decoded.getRoleMask());
        assertEquals(operator, decoded.getAccountRoleMask());
        assertEquals(Integer.valueOf(team),
                decoded.getCharacterRoleMasks().get(character));
        LostTalesChatAccessPacket.RoleHolder holder =
                decoded.getRoleHolders().get(0);
        assertEquals(operator | team, holder.getMask());
        assertEquals(operator, holder.getAccountMask());
        assertEquals(played, holder.getCharacterId());
        assertEquals(64, decoded.getProximityRadius());
    }

    /** An account mask claiming roles the played mask lacks keeps only the shared ones. */
    @Test
    public void theAccountsRolesNeverExceedThePlayedOnes() {
        int operator = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        int team = ChatAccountRole.TEAM.bit();
        LostTalesChatAccessPacket decoded = roundTrip(statement(operator,
                operator | team, Collections.<UUID, Integer>emptyMap(), null,
                0));
        assertFalse(decoded.isMalformed());
        assertEquals(operator, decoded.getAccountRoleMask());
    }


    @Test
    public void aRadiusPastTheConfigsBoundIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        statement(0, 0, Collections.<UUID, Integer>emptyMap(), null, 64)
                .toBytes(buffer);
        // The radius is the last thing written.
        buffer.setShort(buffer.writerIndex() - 2,
                LostTalesChatAccessPacket.MAX_PROXIMITY_RADIUS + 1);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getProximityRadius());
    }
}
