package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesChatCardTest {

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
                "", 0xA94B54, true, 20, null, null, null);
        ChatRoleCatalog.install(ChatRoleCatalog.of(
                java.util.Arrays.asList(ChatRoleFixtures.OPERATOR, moderator), null, null));
        int held = ChatRoleCatalog.current().byId("moderator").bit()
                | ChatRoleFixtures.OPERATOR.bit() | ChatAccountRole.TEAM.bit();
        String team = ChatAccountRole.TEAM.getDisplayName();
        String operator = ChatRoleFixtures.OPERATOR.getDisplayName();
        assertEquals(team + ", " + operator + ", Moderator",
                LostTalesChatCard.roleNames(held));
        assertEquals(operator, LostTalesChatCard.roleNames(
                ChatRoleFixtures.OPERATOR.bit()));
        assertEquals("", LostTalesChatCard.roleNames(0));
    }
    @Test
    public void cardFlipsAndClampsAtScreenEdges() {
        assertEquals(38,
                LostTalesChatCard.cardX(170, 120, 200));
        assertEquals(52,
                LostTalesChatCard.cardY(110, 50, 130));
        assertEquals(4,
                LostTalesChatCard.cardX(1, 120, 100));
        assertEquals(4,
                LostTalesChatCard.cardY(1, 80, 70));
    }

    /**
     * The gap before the time behind a name ends the sender's span, so
     * the pointer on it is on nobody, as the underline stops short of
     * it; a gap with more of the sender after it — a title — is still
     * the sender, and so is nothing at the row's end.
     */
    @Test
    public void theGapBeforeTheTimeIsNotTheName() {
        java.util.List<net.minecraft.util.IChatComponent> header =
                java.util.Arrays.<net.minecraft.util.IChatComponent>asList(
                        new net.minecraft.util.ChatComponentText("NiNuNa"),
                        ChatSpacerMarker.of(4),
                        ChatStampMarker.of("6:18 PM", 20));
        assertFalse(LostTalesChatCard.spanGoesOnAfter(header, 1));
        java.util.List<net.minecraft.util.IChatComponent> titled =
                java.util.Arrays.<net.minecraft.util.IChatComponent>asList(
                        new net.minecraft.util.ChatComponentText("Aldric"),
                        ChatSpacerMarker.of(4),
                        new net.minecraft.util.ChatComponentText("the Farmer"),
                        ChatSpacerMarker.of(4),
                        ChatStampMarker.of("6:18 PM", 20));
        assertTrue(LostTalesChatCard.spanGoesOnAfter(titled, 1));
        assertFalse(LostTalesChatCard.spanGoesOnAfter(titled, 3));
        assertFalse(LostTalesChatCard.spanGoesOnAfter(
                titled.subList(0, 2), 1));
    }
}
