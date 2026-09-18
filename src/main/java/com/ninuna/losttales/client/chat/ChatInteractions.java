package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.IChatComponent;

/**
 * What a left-click on a run of a chat line does. One answer for the
 * whole chat: the hand cursor asks it to promise a click, the text
 * drawing asks it before underlining a run, and the click asks it to
 * act — so a run is never underlined without answering, never answers
 * without the hand, and never shows the hand for a click that would do
 * nothing.
 *
 * <p>A run that only carries a card — a tooltip, an item — is not a
 * click: the card shows on its own, and the run stays plain under the
 * arrow. A person is answered by the person hit test rather than here:
 * the sender's span ends on the closing bracket's glyphs, not on the
 * gap after them, and only that test knows where.</p>
 */
final class ChatInteractions {

    /** What a click on a run does. */
    enum Action {
        /** Nothing: the click falls through to the input field. */
        NONE,
        /** Nothing, and the click is spent: the chat's own furniture. */
        CONSUMED,
        /** Somebody: the sender's span, their head, or a mention. */
        PERSON,
        /** A covered spoiler is read. */
        SPOILER,
        /** A channel link brings its tab forward. */
        CHANNEL_LINK,
        /** A reply's quote leads to the message it quotes. */
        REPLY_JUMP,
        /** A reaction chip adds the reader's reaction, or takes it back. */
        REACTION,
        /** The button a reaction row ends on opens the picker to add one. */
        ADD_REACTION,
        /** An achievement opens on its page of the achievements screen. */
        ACHIEVEMENT,
        /** A shared map marker flies the map to it. */
        MARKER_SHARE,
        /** A shared quest opens its preview and may request a party join. */
        QUEST_SHARE,
        /** A web address opens. */
        LINK,
        /** A suggestion is put into the input field. */
        SUGGESTION,
        /** A command runs, or a message is said. */
        COMMAND
    }

    /** LOTR's own hover action for one of its achievements, by the name it serializes under. */
    private static final String LOTR_ACHIEVEMENT_ACTION = "show_lotr_achievement";

    private ChatInteractions() {}

    /**
     * What a click on the run does, in the order the click resolves
     * it: the chat's own furniture first, whatever the chat-links
     * option says, then what the option gates.
     */
    static Action actionOf(IChatComponent part, boolean chatLinks) {
        if (part == null || part.getChatStyle() == null) {
            return Action.NONE;
        }
        // A spoiler answers before anything else: revealing covered
        // text is the chat's own furniture, not a link, and the click
        // is spent either way so the marker's carrier never falls
        // through to the input.
        if (ChatSpoilerMarker.isMarker(part)) {
            return ChatSpoilerMarker.isRevealed(part) ? Action.CONSUMED
                    : Action.SPOILER;
        }
        if (ChatChannelLinkMarker.isMarker(part)) {
            return Action.CHANNEL_LINK;
        }
        // A person is not a link either: the head, the name, its
        // brackets and title, or a mention.
        if (ChatHeadMarker.isMarker(part)
                || ChatMentionMarker.decode(part) != null
                || ChatSenderSpan.isSenderName(part)) {
            return Action.PERSON;
        }
        if (ChatReplyMarker.isMarker(part)) {
            return Action.REPLY_JUMP;
        }
        if (ChatReactionMarker.isMarker(part)) {
            return Action.REACTION;
        }
        if (ChatReactionMarker.isAddButton(part)) {
            return Action.ADD_REACTION;
        }
        if (isAchievement(part)) {
            return Action.ACHIEVEMENT;
        }
        if (!chatLinks) {
            return Action.NONE;
        }
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        if (share != null) {
            // An item's card is its answer; a marker flies the map.
            return share.kind == ChatShareKind.MARKER ? Action.MARKER_SHARE
                    : share.kind == ChatShareKind.QUEST ? Action.QUEST_SHARE
                    : Action.CONSUMED;
        }
        // The chat's own metadata rides on click events too — a colour,
        // a channel prefix, the time behind a name, an emoji, a title,
        // the chevron, an indent, a gap — and is wide enough to be hit;
        // a click on any of it is spent rather than read as a suggestion.
        if (ChatColorMarker.isMarker(part)
                || ChatPrefixMarker.isMarker(part)
                || ChatStampMarker.isMarker(part)
                || ChatEmojiMarker.isMarker(part)
                || ChatTitleMarker.isMarker(part)
                || ChatBodyMarker.isMarker(part)
                || ChatLayoutMarker.isMarker(part)
                || ChatSpacerMarker.isMarker(part)) {
            return Action.CONSUMED;
        }
        ClickEvent event = part.getChatStyle().getChatClickEvent();
        if (event == null) {
            return Action.NONE;
        }
        if (event.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            return Action.SUGGESTION;
        }
        if (event.getAction() == ClickEvent.Action.RUN_COMMAND) {
            return Action.COMMAND;
        }
        if (event.getAction() == ClickEvent.Action.OPEN_URL) {
            return Action.LINK;
        }
        return Action.CONSUMED;
    }

    /**
     * Whether the action is a click of the run's own — what earns the
     * hand and the underline. A person is left to the person hit test.
     */
    static boolean isClick(Action action) {
        return action != null && action != Action.NONE
                && action != Action.CONSUMED && action != Action.PERSON;
    }

    /**
     * Whether the run answers a click of its own, or is a mention —
     * the one person a run answers for by itself, since a mention's
     * pixels are its own glyphs and nothing beside them. The sender's
     * span answers only through the person hit test.
     */
    static boolean answersClick(IChatComponent part, boolean chatLinks) {
        Action action = actionOf(part, chatLinks);
        if (action == Action.PERSON) {
            return ChatMentionMarker.decode(part) != null;
        }
        return isClick(action);
    }

    /**
     * Whether the run names an achievement: vanilla's own hover for
     * one, or LOTR's. Either opens the achievements screen on it.
     */
    static boolean isAchievement(IChatComponent part) {
        HoverEvent hover = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatHoverEvent();
        if (hover == null || hover.getAction() == null) {
            return false;
        }
        return hover.getAction() == HoverEvent.Action.SHOW_ACHIEVEMENT
                || LOTR_ACHIEVEMENT_ACTION.equals(
                        hover.getAction().getCanonicalName());
    }

    /**
     * Whether two runs are pieces of one element the pointer uses as
     * one: the same reply quote, channel link, spoiler, share or
     * achievement, or the same click. A bracketed achievement or share
     * is several runs — its brackets, its name, a share's icon — and
     * every one of them lights, underlines and answers with the others,
     * so the element reads as one thing wherever the pointer rests on it.
     */
    static boolean sameElement(IChatComponent one, IChatComponent other) {
        if (one == null || other == null) {
            return false;
        }
        if (ChatReplyMarker.isMarker(one)) {
            return ChatReplyMarker.isMarker(other);
        }
        if (ChatChannelLinkMarker.isMarker(one)) {
            return ChatChannelLinkMarker.sameLink(one, other);
        }
        if (ChatSpoilerMarker.isMarker(one)) {
            return ChatSpoilerMarker.sameSpoiler(one, other);
        }
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(one);
        if (share != null) {
            ChatShowcaseMarker.Data theirs = ChatShowcaseMarker.decode(other);
            return theirs != null && theirs.kind == share.kind
                    && theirs.showcaseId == share.showcaseId;
        }
        if (isAchievement(one)) {
            return isAchievement(other) && sameHover(one, other);
        }
        ClickEvent own = genuineClick(one);
        ClickEvent theirs = genuineClick(other);
        return own != null && theirs != null
                && own.getAction() == theirs.getAction()
                && own.getValue() != null
                && own.getValue().equals(theirs.getValue());
    }

    /** Whether both runs carry the same card: one action, one value. */
    private static boolean sameHover(IChatComponent one,
                                     IChatComponent other) {
        HoverEvent own = one.getChatStyle().getChatHoverEvent();
        HoverEvent theirs = other.getChatStyle().getChatHoverEvent();
        return own != null && theirs != null
                && own.getAction() == theirs.getAction()
                && own.getValue() != null && theirs.getValue() != null
                && own.getValue().getUnformattedText().equals(
                        theirs.getValue().getUnformattedText());
    }

    /**
     * The click a run answers to, or null: a link, a command, a
     * suggestion. Every marker the chat lays into a line is carried as
     * a suggestion too, and none of those is a click.
     */
    static ClickEvent genuineClick(IChatComponent part) {
        if (part == null || part.getChatStyle() == null) {
            return null;
        }
        ClickEvent click = part.getChatStyle().getChatClickEvent();
        if (click == null || ChatColorMarker.isMarker(part)
                || ChatPrefixMarker.isMarker(part)
                || ChatStampMarker.isMarker(part)
                || ChatEmojiMarker.isMarker(part)
                || ChatTitleMarker.isMarker(part)
                || ChatReplyMarker.isMarker(part)
                || ChatChannelLinkMarker.isMarker(part)
                || ChatBodyMarker.isMarker(part)
                || ChatLayoutMarker.isMarker(part)
                || ChatSpacerMarker.isMarker(part)
                || ChatSpoilerMarker.isMarker(part)
                || ChatHeadMarker.isMarker(part)
                || ChatShowcaseMarker.decode(part) != null
                || ChatMentionMarker.decode(part) != null
                || ChatReactionMarker.isMarker(part)
                || ChatReactionMarker.isAddButton(part)) {
            return null;
        }
        return click;
    }
}
