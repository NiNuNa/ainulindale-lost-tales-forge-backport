package com.ninuna.losttales.fellowship.sync;

import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The Server line that tells a player they are invited to a fellowship, with
 * Accept and Decline to click. Each answer carries the invitation's id in
 * its click event; the client reads it back and answers as the Fellowship
 * page does, and the server checks the invitation is theirs, so a
 * forged answer does nothing.
 */
public final class FellowshipInvitationNotice {
    private static final String KEY = "chat.losttales.fellowship.invitation";
    private static final String PREFIX = "losttales-fellowship-invitation:";
    private static final String ACCEPT = "accept";
    private static final String DECLINE = "decline";

    private FellowshipInvitationNotice() {}

    /**
     * The line for the invited player: who invites them into which
     * fellowship, then the two answers. The words are the Server's own, in
     * the yellow of a join or a leave; the answers keep their green and red.
     */
    public static IChatComponent line(String inviterName, String fellowshipName,
                                      UUID invitationId) {
        ChatComponentTranslation line = new ChatComponentTranslation(KEY,
                inviterName, fellowshipName, answer(true, invitationId),
                answer(false, invitationId));
        line.getChatStyle().setColor(EnumChatFormatting.YELLOW);
        return line;
    }

    /** Whether the line is an invitation: it is addressed to its reader, as a mention is. */
    public static boolean isNotice(IChatComponent line) {
        return line instanceof ChatComponentTranslation
                && KEY.equals(((ChatComponentTranslation)line).getKey());
    }

    /** The invitation an invitation line answers, read off its Accept; null for any other line. */
    public static UUID invitationIdOf(IChatComponent line) {
        if (!isNotice(line)) {
            return null;
        }
        for (Object argument : ((ChatComponentTranslation) line).getFormatArgs()) {
            if (argument instanceof IChatComponent) {
                IChatComponent part = (IChatComponent) argument;
                ClickEvent click = part.getChatStyle() == null ? null
                        : part.getChatStyle().getChatClickEvent();
                Answer answer = click == null ? null : parse(click.getValue());
                if (answer != null) {
                    return answer.invitationId;
                }
            }
        }
        return null;
    }

    private static IChatComponent answer(boolean accept, UUID invitationId) {
        ChatComponentTranslation word = new ChatComponentTranslation(accept
                ? "chat.losttales.fellowship.invitation.accept"
                : "chat.losttales.fellowship.invitation.decline");
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
