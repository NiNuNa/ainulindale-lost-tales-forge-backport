package com.ninuna.losttales.party.sync;

import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The Server line that tells a player they are invited to a party, with
 * Accept and Decline to click. Each answer carries the invitation's id in
 * its click event; the client reads it back and answers as the Party
 * screen does, and the server checks the invitation is theirs, so a
 * forged answer does nothing.
 */
public final class PartyInvitationNotice {
    private static final String KEY = "chat.losttales.party.invitation";
    private static final String PREFIX = "losttales-party-invitation:";
    private static final String ACCEPT = "accept";
    private static final String DECLINE = "decline";

    private PartyInvitationNotice() {}

    /** The line for the invited player: who invites them, then the two answers. */
    public static IChatComponent line(String inviterName, UUID invitationId) {
        return new ChatComponentTranslation(KEY, inviterName,
                answer(true, invitationId), answer(false, invitationId));
    }

    /** Whether the line is an invitation: it is addressed to its reader, as a mention is. */
    public static boolean isNotice(IChatComponent line) {
        return line instanceof ChatComponentTranslation
                && KEY.equals(((ChatComponentTranslation)line).getKey());
    }

    private static IChatComponent answer(boolean accept, UUID invitationId) {
        ChatComponentTranslation word = new ChatComponentTranslation(accept
                ? "chat.losttales.party.invitation.accept"
                : "chat.losttales.party.invitation.decline");
        word.getChatStyle()
                .setColor(accept ? EnumChatFormatting.GREEN : EnumChatFormatting.RED)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + (accept ? ACCEPT : DECLINE) + ":" + invitationId));
        return word;
    }

    /** The answer a click event's value carries, or null for any other value. */
    public static Answer parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String[] fields = value.substring(PREFIX.length()).split(":", 2);
        if (fields.length != 2
                || !(ACCEPT.equals(fields[0]) || DECLINE.equals(fields[0]))) {
            return null;
        }
        try {
            return new Answer(ACCEPT.equals(fields[0]), UUID.fromString(fields[1]));
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    /** Accept or Decline, and the invitation it answers. */
    public static final class Answer {
        public final boolean accept;
        public final UUID invitationId;

        Answer(boolean accept, UUID invitationId) {
            this.accept = accept;
            this.invitationId = invitationId;
        }
    }
}
