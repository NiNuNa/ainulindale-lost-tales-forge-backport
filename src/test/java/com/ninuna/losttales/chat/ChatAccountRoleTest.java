package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Roles are a bit set in precedence order; NONE is the absence of one. */
public final class ChatAccountRoleTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void noneOccupiesNoBitAndIsNeverTagged() {
        assertEquals(0, ChatAccountRole.NONE.bit());
        assertTrue(ChatAccountRole.NONE.isNone());
        assertEquals("", ChatAccountRole.NONE.getTagKey());
        assertEquals("", ChatAccountRole.NONE.getDisplayTag());
        assertEquals(0, ChatAccountRole.maskOf(ChatAccountRole.NONE));
        assertEquals(Collections.emptyList(), ChatAccountRole.fromMask(0));
        assertEquals(ChatAccountRole.NONE, ChatAccountRole.primary(0));
        assertEquals(ChatAccountRole.NONE, ChatAccountRole.byId("nobody"));
    }

    @Test
    public void everyRoleHasItsOwnBit() {
        int seen = 0;
        for (ChatAccountRole role : ChatAccountRole.all()) {
            assertFalse(role.isNone());
            assertEquals(0, seen & role.bit());
            assertTrue(role.getDisplayTag().length() > 0);
            seen |= role.bit();
            assertEquals(Collections.singletonList(role),
                    ChatAccountRole.fromMask(role.bit()));
            assertEquals(role, ChatAccountRole.primary(role.bit()));
            assertEquals(role, ChatAccountRole.byId(role.getId()));
        }
        assertTrue(ChatAccountRole.isValidMask(seen));
        assertFalse(ChatAccountRole.isValidMask(0x80));
        assertEquals(ChatAccountRole.TEAM.bit() | ChatAccountRole.OPERATOR.bit(), seen);
    }

    /** The team mark outranks Operator: it colours the name, both are tagged. */
    @Test
    public void theTeamMarkIsPrimaryOverOperatorAndBothAreListed() {
        int both = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR,
                ChatAccountRole.TEAM);
        assertEquals(Arrays.asList(ChatAccountRole.TEAM,
                ChatAccountRole.OPERATOR), ChatAccountRole.fromMask(both));
        assertEquals(ChatAccountRole.TEAM, ChatAccountRole.primary(both));
        assertEquals(LostTalesColors.rgb(LostTalesColors.MULBERRY),
                ChatAccountRole.TEAM.getColor());
        assertEquals(0x7C3D64, ChatAccountRole.TEAM.getColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.CRIMSON),
                ChatAccountRole.OPERATOR.getColor());
        assertTrue(ChatAccountRole.TEAM.isLocked());
        assertFalse(ChatAccountRole.OPERATOR.isLocked());
        assertEquals(1, ChatAccountRole.TEAM.bit());
        assertEquals(2, ChatAccountRole.OPERATOR.bit());
    }

    @Test
    public void unknownBitsAreIgnoredWhenReading() {
        int mask = ChatAccountRole.OPERATOR.bit() | 0x80;
        assertFalse(ChatAccountRole.isValidMask(mask));
        assertEquals(Collections.singletonList(ChatAccountRole.OPERATOR),
                ChatAccountRole.fromMask(mask));
    }

    /**
     * The one rule every account line's name colour comes from: the
     * primary role's colour, and the chat's ivory without a role.
     */
    @Test
    public void nameColourFollowsThePrimaryRole() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                ChatAccountRole.nameColor(0));
        assertEquals(ChatAccountRole.OPERATOR.getColor(),
                ChatAccountRole.nameColor(ChatAccountRole.OPERATOR.bit()));
        assertEquals(ChatAccountRole.TEAM.getColor(),
                ChatAccountRole.nameColor(ChatAccountRole.maskOf(
                        ChatAccountRole.OPERATOR,
                        ChatAccountRole.TEAM)));
    }

    /** A config role joins the catalogue with the next bit and its own look. */
    @Test
    public void aConfigRoleTakesTheNextBitAndItsRankPlacesIt() {
        ChatAccountRole moderator = ChatAccountRole.custom("moderator", "Moderator", "[Mod]",
                "Keeps the peace.", 0x123456, true, 5,
                Collections.singletonList(ChatRoleSource.opLevel(1)));
        ChatRoleCatalog.install(ChatRoleCatalog.of(
                Collections.singletonList(moderator), null, null));
        ChatAccountRole listed = ChatAccountRole.byId("moderator");
        assertFalse(listed.isNone());
        assertEquals(4, listed.bit());
        assertEquals("Moderator", listed.getDisplayName());
        assertEquals("[Mod]", listed.getDisplayTag());
        assertEquals("Keeps the peace.", listed.getDisplayDescription());
        // Rank 5 sits between the team mark (0) and the operator (10).
        assertEquals(Arrays.asList(ChatAccountRole.TEAM, listed, ChatAccountRole.OPERATOR),
                ChatAccountRole.all());
        assertEquals(listed, ChatAccountRole.primary(
                listed.bit() | ChatAccountRole.OPERATOR.bit()));
        assertTrue(ChatAccountRole.isValidMask(7));
        assertFalse(ChatAccountRole.isValidMask(8));
        // A role with no tag of its own wears its name in brackets.
        ChatAccountRole plain = ChatAccountRole.custom("builder", "Builder", "", "",
                0, false, 30, null);
        assertEquals("[Builder]", plain.getDisplayTag());
    }

    /**
     * A client's copy of the catalogue, read off the wire, has no sources
     * or members; installing it must leave the server's own untouched,
     * since an integrated server shares the JVM with its client.
     */
    @Test
    public void aClientCopyNeverReplacesTheServerCatalogue() {
        ChatRoleCatalog served = ChatRoleCatalog.builtIn();
        ChatRoleCatalog.installServer(served);
        try {
            ChatAccountRole operator = served.operator();
            assertFalse(operator.getSources().isEmpty());
            ChatAccountRole wire = ChatAccountRole.fromWire(operator.getId(),
                    operator.getBitIndex(), operator.getNameKey(), operator.getTagKey(),
                    operator.getName(), operator.getTag(), operator.getDescription(),
                    operator.getColor(), operator.isMentionable(), operator.isLocked(),
                    operator.getRank());
            assertTrue(wire.getSources().isEmpty());
            ChatRoleCatalog.install(ChatRoleCatalog.fromWire(
                    Collections.singletonList(wire)));
            ChatRoleCatalog.resetToBuiltIn();
            assertTrue(ChatRoleCatalog.server() == served);
            assertFalse(ChatRoleCatalog.server().operator().getSources().isEmpty());
        } finally {
            ChatRoleCatalog.installServer(null);
        }
    }
}
