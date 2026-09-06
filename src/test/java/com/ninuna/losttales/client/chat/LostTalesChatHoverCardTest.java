package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import net.minecraft.util.StatCollector;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatHoverCardTest {

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    /**
     * The card lists every held role, highest display priority first:
     * the team mark before Operator, Operator before a config role of
     * the default rank, however the mask orders their bits.
     */
    @Test
    public void theCardListsEveryRoleByDisplayPriority() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator",
                "", "", 0xA94B54, true, 20, null);
        ChatRoleCatalog.install(ChatRoleCatalog.of(
                java.util.Arrays.asList(ChatRoleFixtures.OPERATOR, moderator), null, null));
        int held = ChatRoleCatalog.current().byId("moderator").bit()
                | ChatRoleFixtures.OPERATOR.bit() | ChatAccountRole.TEAM.bit();
        String team = StatCollector.translateToLocal(ChatAccountRole.TEAM.getNameKey());
        String operator = ChatRoleFixtures.OPERATOR.getDisplayName();
        assertEquals(team + ", " + operator + ", Moderator",
                LostTalesChatHoverCard.roleNames(held));
        assertEquals(operator, LostTalesChatHoverCard.roleNames(
                ChatRoleFixtures.OPERATOR.bit()));
        assertEquals("", LostTalesChatHoverCard.roleNames(0));
    }
    @Test
    public void hitBoundsHandleScaledAnimatedGeometry() {
        assertTrue(LostTalesChatHoverCard.contains(
                31.0F, 42.0F, 20.0F, 40.0F, 44.0F, 49.0F));
        assertFalse(LostTalesChatHoverCard.contains(
                44.0F, 42.0F, 20.0F, 40.0F, 44.0F, 49.0F));
    }

    @Test
    public void cardFlipsAndClampsAtScreenEdges() {
        assertEquals(38,
                LostTalesChatHoverCard.cardX(170, 120, 200));
        assertEquals(52,
                LostTalesChatHoverCard.cardY(110, 50, 130));
        assertEquals(4,
                LostTalesChatHoverCard.cardX(1, 120, 100));
        assertEquals(4,
                LostTalesChatHoverCard.cardY(1, 80, 70));
    }
}
