package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Roles are a bit set in precedence order; NONE is the absence of one. */
public final class ChatAccountRoleTest {

    @Test
    public void noneOccupiesNoBitAndIsNeverTagged() {
        assertEquals(0, ChatAccountRole.NONE.bit());
        assertEquals("", ChatAccountRole.NONE.getTagKey());
        assertEquals(0, ChatAccountRole.maskOf(ChatAccountRole.NONE));
        assertEquals(Collections.emptyList(), ChatAccountRole.fromMask(0));
        assertEquals(ChatAccountRole.NONE, ChatAccountRole.primary(0));
    }

    @Test
    public void everyRoleHasItsOwnBit() {
        int seen = 0;
        for (ChatAccountRole role : ChatAccountRole.values()) {
            if (role == ChatAccountRole.NONE) {
                continue;
            }
            assertEquals(0, seen & role.bit());
            assertTrue(role.getTagKey().length() > 0);
            seen |= role.bit();
            assertEquals(Collections.singletonList(role),
                    ChatAccountRole.fromMask(role.bit()));
            assertEquals(role, ChatAccountRole.primary(role.bit()));
        }
        assertTrue(ChatAccountRole.isValidMask(seen));
        assertFalse(ChatAccountRole.isValidMask(0x80));
    }

    /** Developer outranks Operator: it colours the name, both are tagged. */
    @Test
    public void developerIsPrimaryOverOperatorAndBothAreListed() {
        int both = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR,
                ChatAccountRole.DEVELOPER);
        assertEquals(Arrays.asList(ChatAccountRole.DEVELOPER,
                ChatAccountRole.OPERATOR), ChatAccountRole.fromMask(both));
        assertEquals(ChatAccountRole.DEVELOPER, ChatAccountRole.primary(both));
        assertEquals(LostTalesColors.rgb(LostTalesColors.MULBERRY),
                ChatAccountRole.DEVELOPER.getColor());
        assertEquals(0x7C3D64, ChatAccountRole.DEVELOPER.getColor());
        assertEquals(LostTalesColors.rgb(LostTalesColors.CRIMSON),
                ChatAccountRole.OPERATOR.getColor());
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
        assertEquals(ChatAccountRole.DEVELOPER.getColor(),
                ChatAccountRole.nameColor(ChatAccountRole.maskOf(
                        ChatAccountRole.OPERATOR,
                        ChatAccountRole.DEVELOPER)));
    }
}
