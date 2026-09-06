package com.ninuna.losttales.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;

/**
 * Where a sender's roles show, and what colours the name: one rule for
 * every line, decided by the channel's {@link ChatPresentationMode}
 * rather than by the identity the line wears.
 *
 * <p>On an out-of-character channel — OOC &amp; Discord, Operator, the
 * Console — a line is tagged with the sender's primary role, the one of
 * highest display priority, and the name takes that role's colour; a
 * sender with no role reads in the chat's plain ivory, the colour of the
 * unassigned. On an in-character channel — Global, Proximity, Faction,
 * Party and whispers — no role is tagged whoever speaks, and the name
 * takes the worn character's faction colour; a line spoken as the
 * account, which has no faction, is unassigned and reads in ivory too.
 * Roles still exist behind an in-character line: they gate channels,
 * answer mentions and stand on the player's card. They only stop being
 * worn on the line.</p>
 *
 * <p>The server signs every routed line by this, and a client signs the
 * lines it builds for itself by the same, so a line reads the same
 * whichever side composed it. Mentions, the completion list and the
 * speech bubbles read the same rule. Free of Minecraft imports.</p>
 */
public final class ChatRolePresentation {

    private ChatRolePresentation() {}

    /** Whether the channel's lines are in character; false for null. */
    public static boolean isInCharacter(ChatChannel channel) {
        return channel != null
                && channel.getPresentation() == ChatPresentationMode.IN_CHARACTER;
    }

    /** Whether lines of the channel are tagged with their sender's primary role. */
    public static boolean showsRoles(ChatChannel channel) {
        return channel != null
                && channel.getPresentation() == ChatPresentationMode.OUT_OF_CHARACTER;
    }

    /**
     * The roles a line of the channel carries: the primary one alone
     * where roles show, none in character. One tag, never a stack.
     */
    public static int rolesShown(ChatChannel channel, int heldRoles) {
        return showsRoles(channel) ? ChatAccountRole.primary(heldRoles).bit() : 0;
    }

    /**
     * The colour the sender's name is drawn in on a line of the channel:
     * the primary role's where roles show, else the worn character's
     * faction colour, else {@link #unassignedColor()}.
     *
     * @param heldRoles    every role the sender's account holds
     * @param accountLine  whether the line wears the account rather than
     *                     a character
     * @param factionColor the worn character's faction colour; ignored
     *                     for an account line
     */
    public static int nameColor(ChatChannel channel, int heldRoles,
                                boolean accountLine, int factionColor) {
        if (showsRoles(channel)) {
            return ChatAccountRole.nameColor(heldRoles);
        }
        return accountLine ? unassignedColor() : factionColor & 0xFFFFFF;
    }

    /** The chat's plain ivory: what a name with no role and no faction reads in. */
    public static int unassignedColor() {
        return LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
    }
}
