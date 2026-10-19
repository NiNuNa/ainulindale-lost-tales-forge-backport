package com.ninuna.losttales.chat.share;

import com.ninuna.losttales.chat.ChatMessageIds;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * The Server line under a quest card shared in a fellowship's conversation,
 * for each member who could join it (QUEST-25): <i>Aldric asks the
 * fellowship to join The Cheese Cache. Join · Not now</i>. Join carries the
 * card's message and token in its click event and sends the join as the
 * card's own question does; the server checks the card, the author and the
 * fellowship again, so a forged answer does nothing. Not now does nothing.
 */
public final class QuestShareInvite {
    private static final String KEY = "chat.losttales.quest.share_invite";
    private static final String PREFIX = "losttales-quest-join:";
    private static final String JOIN = "join";
    private static final String LATER = "later";

    private QuestShareInvite() {}

    /**
     * The line for one member: who shared the card and the quest's title,
     * then the two answers, in the yellow of a fellowship invitation.
     */
    public static IChatComponent line(String authorName, String questTitle,
                                      long messageId, int tokenIndex) {
        ChatComponentTranslation line = new ChatComponentTranslation(KEY,
                authorName, questTitle, answer(true, messageId, tokenIndex),
                answer(false, messageId, tokenIndex));
        line.getChatStyle().setColor(EnumChatFormatting.YELLOW);
        return line;
    }

    /** Whether the line is such an invitation: it is addressed to its reader. */
    public static boolean isNotice(IChatComponent line) {
        return line instanceof ChatComponentTranslation
                && KEY.equals(((ChatComponentTranslation) line).getKey());
    }

    private static IChatComponent answer(boolean join, long messageId, int tokenIndex) {
        ChatComponentTranslation word = new ChatComponentTranslation(join
                ? "chat.losttales.quest.share_invite.join"
                : "chat.losttales.quest.share_invite.not_now");
        word.getChatStyle()
                .setColor(join ? EnumChatFormatting.GREEN : EnumChatFormatting.GRAY)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        join ? PREFIX + JOIN + ":" + messageId + ":" + tokenIndex
                                : PREFIX + LATER));
        return word;
    }

    /** The answer a click event's value carries, or null for any other value. */
    public static Answer parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String rest = value.substring(PREFIX.length());
        if (LATER.equals(rest)) {
            return new Answer(false, ChatMessageIds.NONE, 0);
        }
        String[] fields = rest.split(":", 3);
        if (fields.length != 3 || !JOIN.equals(fields[0])) {
            return null;
        }
        try {
            long messageId = Long.parseLong(fields[1]);
            int tokenIndex = Integer.parseInt(fields[2]);
            if (!ChatMessageIds.isServerId(messageId) || tokenIndex < 0
                    || tokenIndex >= ChatShareTokenParser.MAX_TOKENS) {
                return null;
            }
            return new Answer(true, messageId, tokenIndex);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** Join, with the card it joins, or Not now. */
    public static final class Answer {
        public final boolean join;
        public final long messageId;
        public final int tokenIndex;

        Answer(boolean join, long messageId, int tokenIndex) {
            this.join = join;
            this.messageId = messageId;
            this.tokenIndex = tokenIndex;
        }
    }
}
