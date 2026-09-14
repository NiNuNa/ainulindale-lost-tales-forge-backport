package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatBroadcastIdMarkers;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatEpithet;
import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.chat.ChatMentions;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.client.character.ClientCharacterRosterCache;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.network.packet.LostTalesChatUpdatePacket;
import com.ninuna.losttales.network.packet.LostTalesChatReactionSyncPacket;
import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;

/** Builds structured legacy chat components and records entry-animation time. */
public final class LostTalesChatPresentation {
    private static volatile long lastMessageNanos;
    /**
     * The newest message's own line id. What the entry animation picks
     * its lines out by: several messages can share an update counter
     * (they arrive in one tick), and the feed hands a run's lines the
     * counter they fade on rather than the one they arrived on, so the
     * counter identifies nothing on its own.
     */
    private static volatile int lastMessageChatLineId;
    private static volatile boolean hasLastMessage;
    private static volatile ChatTab lastMessageTab;
    private static int nextChatLineId = Integer.MIN_VALUE;
    /** Pinged line ids remembered: as many as the history can hold. */
    private static final int MAX_PINGED_LINES =
            LostTalesChatHistoryHooks.MAX_CAPACITY;
    private static final LinkedHashSet<Integer> pingedChatLineIds =
            new LinkedHashSet<Integer>();
    private static final int[] NO_SHOWCASES = new int[0];
    /**
     * One cue stands for every ping inside this window. A burst of
     * mentions — an achievement naming half the server, two lines
     * arriving in one tick — reads as one notification, so it sounds as
     * one; the window is short enough that pings a player would perceive
     * as separate events stay separately audible.
     */
    private static final long PING_SOUND_WINDOW_NANOS = 200L * 1000000L;
    private static long lastPingSoundNanos;
    private LostTalesChatPresentation() {}

    public static void receive(LostTalesChatMessagePacket packet) {
        receive(packet, false);
    }

    /**
     * Shows a line the server sent. A {@code replayed} line is one the
     * server is catching this player up on from its history: it is
     * filed, grouped and counted exactly as a live line is, but earns no
     * sound and confirms no pending echo, since nothing was typed for
     * it here. A message this client already holds is never shown twice,
     * whichever way it arrives.
     */
    public static void receive(LostTalesChatMessagePacket packet,
                               boolean replayed) {
        receive(packet, replayed, false);
    }

    /**
     * As above for a replayed line said {@code beforeArrival}: before
     * this player arrived. Such a line is history rather than news — it
     * stands in its tab, and the closed feed, which shows what is
     * happening, passes over it.
     */
    public static void receive(LostTalesChatMessagePacket packet,
                               boolean replayed, boolean beforeArrival) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (packet == null || packet.isMalformed() || minecraft == null
                || minecraft.ingameGUI == null) {
            return;
        }
        // The Server looks the same wherever and whenever it speaks: a
        // line of its own the server replays from the history wears
        // this client's Console colour, as a live one does.
        if (LostTalesChatMessagePacket.isSystemSender(packet.getSenderId())) {
            packet = packet.withNameColor(
                    LostTalesChatVisualStyle.asideRgb());
        }
        if (ChatMessageIds.isServerId(packet.getMessageId())
                && ClientChatMessages.get(packet.getMessageId()) != null) {
            // Held, but is it still on screen? The game clears its own
            // list whenever the main menu opens, and another mod may
            // clear it too; a message whose line is gone is shown again
            // rather than remembered into nothing.
            Integer shown = ClientChatMessageIds.chatLineIdOf(
                    packet.getMessageId());
            if (shown != null && ChatWindowLines.holdsLine(
                    minecraft.ingameGUI.getChatGUI(), shown.intValue())) {
                return;
            }
            ClientChatMessages.forget(packet.getMessageId());
        }
        ChatChannel channel = packet.getChannel();
        // A Discord member has no Minecraft account to look a skin up
        // for, and is known by the sender id the bridge signs with, not
        // by the channel: a Discord line and a player's line share OOC
        // & Discord. Whether the line wears the account is the line's
        // own word too: appearances let a character speak there and the
        // account in Global.
        if (packet.isAccountLine() && !LostTalesChatMessagePacket
                .isDiscordSender(packet.getSenderId())
                && !LostTalesChatMessagePacket.isSystemSender(
                        packet.getSenderId())) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    packet.getIdentityName());
        }
        // The name this line was signed with, and what the server says
        // it wears: that is where a mention of it takes its colour from.
        // An in-character line says nothing about roles — none are worn
        // there — so it is not allowed to forget what an out-of-character
        // line stated.
        if (ChatRolePresentation.showsRoles(channel)) {
            ClientChatAccountRoles.remember(packet.getIdentityName(),
                    packet.getRoles());
        }
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, packet.getMessage());
        // A line of the server's own that carries its component — an
        // achievement with its hover, a death, a join — is shown from
        // that component the way the live line was, the players it
        // names read as the identities they were playing.
        IChatComponent body = null;
        if (LostTalesChatMessagePacket.isSystemSender(packet.getSenderId())
                && packet.getBodyJson().length() > 0) {
            boolean[] localMentioned = new boolean[1];
            body = serverBody(minecraft, packet, localMentioned);
            if (body != null) {
                mentioned = LostTalesConfig.enableChatPings
                        && localMentioned[0];
            }
        }
        // A whisper lands in the tab of its conversation, opened on the
        // first message in the window the player is typing in; a plain
        // channel the player closed reopens the same way. A tab that is
        // hidden, or that has no window left to open in, stays closed:
        // its lines are still filed and counted unread, they still show
        // in the closed-chat feed, and the tab shows them all once it is
        // opened again.
        ChatTab tab = tabOf(packet);
        // The layout holds one entry per channel, so a conversation of a
        // scoped channel is asked about as the row entry it belongs to.
        // Asking with the conversation's own tab would find no window
        // holding it and open a second Faction tab beside the first.
        ChatTab row = ChatTab.row(tab);
        if (row != null && !ChatWindowLayout.isOpen(row)
                && !ChatWindowLayout.isHidden(row)) {
            ChatWindowLayout.openTab(row, windowIdOfSelection());
        }
        if (tab == null) {
            return;
        }
        if (tab.isWhisper() && minecraft.thePlayer != null
                && !minecraft.thePlayer.getUniqueID().equals(
                        packet.getSenderId())) {
            // Their half of the conversation says what colour it is in,
            // and which appearance the tab names them by.
            ClientChatChannelState.rememberPartnerColor(tab,
                    packet.getNameColor());
            ClientChatChannelState.rememberPartnerName(tab,
                    packet.getIdentityName(), packet.getAccountName());
        }
        if (tab.isWhisper()) {
            // Both copies say which character of the other party the
            // conversation is with, so a reply is addressed by id.
            ClientChatChannelState.rememberPartnerCharacterId(tab,
                    packet.getPartnerCharacterId());
        }
        // A replayed line older than everything the view holds is a page
        // of older history: laid in above the view's oldest line, filed
        // and remembered, but neither unread, nor a cue, nor the newest.
        if (replayed) {
            Integer above = ClientChatOlderHistory.anchorFor(minecraft, tab,
                    packet.getMessageId());
            if (above != null) {
                printOlder(minecraft, packet, tab, above.intValue(), body);
                return;
            }
        }
        // A message this client already showed is not printed again:
        // the line it is standing on becomes the real one, in place.
        int confirmed = replayed ? 0
                : confirmPendingEcho(minecraft, packet, tab);
        int chatLineId;
        receivingReplayed = replayed;
        receivingBeforeArrival = replayed && beforeArrival;
        try {
            chatLineId = confirmed != 0 ? confirmed
                    : print(minecraft, packet, tab, mentioned,
                            body == null ? ChatBodyKind.MESSAGE
                                    : ChatBodyKind.ANSWER, body);
        } finally {
            receivingReplayed = false;
            receivingBeforeArrival = false;
        }
        if (mentioned || tab.isWhisper()) {
            if (mentioned) {
                markPinged(chatLineId);
            }
            // The highlight stays for when the tab is read; the cue is
            // silenced by the tab's own preference alone — a closed tab
            // still receives — and a whisper is always a cue. A replayed
            // line was said before this player arrived and sounds no cue,
            // and neither does a whisper to a character the player is not
            // playing right now: it waits, counted, for that identity.
            if (!replayed && ChatWindowLayout.isPingAudible(tab)
                    && ClientChatChannelState.isAvailable(tab)) {
                playPingSound(minecraft);
            }
        }
    }

    /**
     * Applies what the server says has happened to a message already on
     * screen: it now reads differently, or it is gone.
     *
     * <p>An edited message is rebuilt from everything it was built
     * from and put back <em>where it stands</em>. Printing it again
     * would file it as the newest line, which is not what happened —
     * the message was said when it was said, and a conversation that
     * reordered itself around a typo would be worse than the typo. A
     * removed one is taken out of the history entirely, and the run it
     * was part of closes over the gap.</p>
     *
     * <p>Both leave the mod's own scroll alone: it belongs to the view,
     * not to vanilla's history, so a correction does not throw a reader
     * back to the present.</p>
     */
    public static void applyUpdate(LostTalesChatUpdatePacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        Integer chatLineId = ClientChatMessageIds.chatLineIdOf(
                packet.getMessageId());
        if (packet.isRemoved()) {
            ClientChatMessages.forget(packet.getMessageId());
            if (chatLineId != null
                    && ChatWindowLines.removeMessage(chat,
                            chatLineId.intValue())) {
                ChatGroupRuns.forget(chatLineId.intValue());
                LostTalesChatHistoryHooks.refresh(chat);
            }
            return;
        }
        // Words it already says are no edit: nothing is marked and
        // nothing redrawn, whichever side the word came from.
        ClientChatMessages.Remembered held =
                ClientChatMessages.get(packet.getMessageId());
        if (held != null
                && held.packet.getMessage().equals(packet.getMessage())) {
            return;
        }
        // The quotes first: every held reply to the edited message
        // re-cuts its excerpt to what the message says now, whether or
        // not the message's own line is still held.
        boolean quotesChanged = refreshQuotesOf(chat, packet.getMessageId(),
                packet.getMessage());
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(packet.getMessageId());
        if (remembered == null || chatLineId == null) {
            // Nothing left of the message itself to correct: it has
            // fallen out of the history this client keeps.
            if (quotesChanged) {
                LostTalesChatHistoryHooks.refresh(chat);
            }
            return;
        }
        LostTalesChatMessagePacket edited;
        try {
            edited = remembered.packet.withMessage(packet.getMessage());
        } catch (RuntimeException refused) {
            // The server validates before it sends; a payload this
            // client cannot rebuild is dropped rather than half-applied.
            return;
        }
        if (!rebuildInPlace(chat, chatLineId.intValue(), remembered, edited,
                true)) {
            if (quotesChanged) {
                LostTalesChatHistoryHooks.refresh(chat);
            }
            return;
        }
        ClientChatMessages.rewrite(packet.getMessageId(), edited);
        LostTalesChatHistoryHooks.refresh(chat);
    }

    /**
     * Applies the reactions the server says a message on screen wears
     * now: the line is built again where it stands with its reaction
     * row, and remembered wearing them, so a later edit keeps them. A
     * message this client no longer holds has nothing to redraw.
     */
    public static void applyReactions(LostTalesChatReactionSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        ClientChatMessages.Remembered held =
                ClientChatMessages.get(packet.getMessageId());
        if (held == null) {
            return;
        }
        LostTalesChatMessagePacket updated;
        try {
            updated = held.packet.withReactions(packet.getReactions());
        } catch (RuntimeException refused) {
            return;
        }
        ClientChatMessages.refresh(packet.getMessageId(), updated);
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        Integer chatLineId = ClientChatMessageIds.chatLineIdOf(
                packet.getMessageId());
        if (chatLineId != null && rebuildInPlace(chat, chatLineId.intValue(),
                held, updated, held.edited)) {
            LostTalesChatHistoryHooks.refresh(chat);
        }
    }

    /**
     * Builds a held message's line again from {@code packet} and puts
     * both its forms back where the old ones stood — its body shown the
     * way it was, the server's own component again for a line of the
     * server's, and marked edited when it is. False when the line is no
     * longer in the game's history.
     */
    private static boolean rebuildInPlace(GuiNewChat chat, int chatLineId,
                                          ClientChatMessages.Remembered held,
                                          LostTalesChatMessagePacket packet,
                                          boolean edited) {
        boolean grouped = !packet.getReply().exists();
        IChatComponent full = build(packet, held.tab, held.showcaseIds, false,
                held.kind, held.body == null ? null : held.body.createCopy());
        IChatComponent groupedLine = build(packet, held.tab,
                held.showcaseIds, grouped, held.kind,
                held.body == null ? null : held.body.createCopy());
        if (edited) {
            full = markEdited(full);
            groupedLine = markEdited(groupedLine);
        }
        if (!ChatWindowLines.replaceMessage(chat, chatLineId, full)) {
            return false;
        }
        ChatGroupRuns.replaceGroupedLine(chatLineId, groupedLine);
        return true;
    }

    /**
     * Whether the message drawn on a chat line can be reacted to: one
     * the server named and this client still holds, while the chat's
     * emoji are on.
     */
    static boolean isReactable(int chatLineId) {
        if (!LostTalesConfig.enableChatEmojis) {
            return false;
        }
        long messageId = ClientChatMessageIds.messageIdOf(chatLineId);
        return ChatMessageIds.isServerId(messageId)
                && ClientChatMessages.get(messageId) != null;
    }

    /**
     * Re-cuts the quote of every held reply to {@code messageId} to the
     * text it says now, rebuilding each reply's line where it stands.
     * The reply itself is untouched — the same words, and no edited
     * mark of its own unless it already wore one — only what it shows
     * of the message it answers moves, the way the server would cut a
     * fresh quote of it now. Answers whether any line changed.
     */
    private static boolean refreshQuotesOf(GuiNewChat chat, long messageId,
                                           String newText) {
        boolean changed = false;
        List<Long> replies = ClientChatMessages.replyingTo(messageId);
        for (int index = 0; index < replies.size(); index++) {
            long replyId = replies.get(index).longValue();
            ClientChatMessages.Remembered entry =
                    ClientChatMessages.get(replyId);
            Integer lineId = ClientChatMessageIds.chatLineIdOf(replyId);
            if (entry == null || lineId == null) {
                continue;
            }
            LostTalesChatMessagePacket updated;
            try {
                // Cut afresh from the new words, keeping the colour the
                // name was drawn in and the head the quote wore.
                ChatReplyReference old = entry.packet.getReply();
                updated = entry.packet.withReply(ChatReplyReference.of(
                        messageId, old.getAuthor(), newText,
                        old.getAuthorColor()).withHeadOf(old));
            } catch (RuntimeException refused) {
                continue;
            }
            IChatComponent full = build(updated, entry.tab,
                    entry.showcaseIds, false);
            // A reply never groups, so its grouped form is the full one.
            IChatComponent groupedLine = build(updated, entry.tab,
                    entry.showcaseIds, false);
            if (entry.edited) {
                full = markEdited(full);
                groupedLine = markEdited(groupedLine);
            }
            if (!ChatWindowLines.replaceMessage(chat, lineId.intValue(),
                    full)) {
                continue;
            }
            ChatGroupRuns.replaceGroupedLine(lineId.intValue(), groupedLine);
            ClientChatMessages.refresh(replyId, updated);
            changed = true;
        }
        return changed;
    }

    /**
     * Adds the quiet note that a line is not what was first said. It
     * goes on the end of the body, in the timestamp's own muted colour,
     * so it reads as something the chat is saying about the message
     * rather than something the sender wrote.
     */
    private static IChatComponent markEdited(IChatComponent line) {
        // The chat's aside tone, which the timestamps, the reply chip
        // and the typing line wear too: they read as one set.
        int color = LostTalesChatVisualStyle.asideRgb();
        ChatComponentText mark = text(
                StatCollector.translateToLocal("gui.losttales.chat.edited"),
                nearestFormatting(color), false);
        mark.getChatStyle().setItalic(Boolean.TRUE);
        return insertBeforeReactions(line, mark);
    }

    /**
     * Puts a run at the end of the line's words — ahead of its reaction
     * row when it has one, so a note about the words stays with them.
     */
    private static IChatComponent insertBeforeReactions(IChatComponent line,
                                                        IChatComponent part) {
        List<?> siblings = line.getSiblings();
        for (int index = 0; siblings != null && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (value instanceof IChatComponent
                    && ChatLayoutMarker.isRowBreak((IChatComponent)value)) {
                part.getChatStyle().setParentStyle(line.getChatStyle());
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>)siblings;
                mutable.add(index, part);
                return line;
            }
        }
        return line.appendSibling(part);
    }

    /**
     * Prints a message in full and records what a view needs to show it
     * as a continuation instead. Which views do is theirs to decide:
     * the history keeps one message, and {@link ChatGroupRuns} keeps
     * the identity it was signed with beside the grouped form of the
     * same line.
     */
    private static int print(Minecraft minecraft,
                             LostTalesChatMessagePacket packet, ChatTab tab,
                             boolean mentioned) {
        return print(minecraft, packet, tab, mentioned,
                ChatBodyKind.MESSAGE);
    }

    /** As above, with the body presented as {@code kind} says. */
    private static int print(Minecraft minecraft,
                             LostTalesChatMessagePacket packet, ChatTab tab,
                             boolean mentioned, ChatBodyKind kind) {
        return print(minecraft, packet, tab, mentioned, kind, null);
    }

    /**
     * As above with the body given as a component of its own — the
     * server's answer to a command, shown exactly as it came — rather
     * than read off the packet. Each form of the line is built over a
     * copy of it: one component under two roots would share one style
     * whose parent is whichever root came last.
     */
    private static int print(Minecraft minecraft,
                             LostTalesChatMessagePacket packet, ChatTab tab,
                             boolean mentioned, ChatBodyKind kind,
                             IChatComponent body) {
        int chatLineId = allocateChatLineId();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        // Decoded once: both forms of the line show the same showcases.
        int[] showcaseIds = decodeShowcases(packet);
        chat.printChatMessageWithOptionalDeletion(
                build(packet, tab, showcaseIds, false, kind, body),
                chatLineId);
        if (receivingBeforeArrival) {
            ageOutOfFeed(minecraft, chat, chatLineId);
        }
        rememberPrinted(chatLineId, packet, tab, showcaseIds, kind,
                body == null ? null : body.createCopy(), mentioned);
        return chatLineId;
    }

    /**
     * Prints a line the server replayed from before everything the view
     * holds, and lays it in above the view's oldest line: the game
     * wraps it as it wraps every line, at the newest end; the wrapped
     * rows and the unwrapped message are then moved to their place
     * after {@code aboveChatLineId}'s in each of the game's two lists,
     * with that line's own age so they fade in the feed as it does,
     * rather than arriving as news. Remembered like any printed line,
     * but filed as a backfill: no unread count, no divider, no cue, no
     * read mark. Dropped, not misplaced, when the game's lists cannot be
     * reached or have no room.
     */
    private static void printOlder(Minecraft minecraft,
                                   LostTalesChatMessagePacket packet, ChatTab tab,
                                   int aboveChatLineId, IChatComponent body) {
        ChatBodyKind kind = body == null ? ChatBodyKind.MESSAGE
                : ChatBodyKind.ANSWER;
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        List<ChatLine> messages = ChatWindowLines.messageHistory(chat);
        List<ChatLine> drawn;
        try {
            drawn = LostTalesChatOverlayRenderer.getDrawnLines(chat);
        } catch (IllegalAccessException unavailable) {
            drawn = null;
        }
        if (messages == null || drawn == null
                || messages.size() + 1 >= LostTalesChatHistoryHooks.capacity()) {
            return;
        }
        int anchorIndex = -1;
        for (int index = 0; index < messages.size(); index++) {
            ChatLine line = messages.get(index);
            if (line != null && line.getChatLineID() == aboveChatLineId) {
                anchorIndex = index;
                break;
            }
        }
        int anchorRowEnd = -1;
        for (int index = drawn.size() - 1; index >= 0; index--) {
            ChatLine row = drawn.get(index);
            if (row != null && row.getChatLineID() == aboveChatLineId) {
                anchorRowEnd = index;
                break;
            }
        }
        if (anchorIndex < 0 || anchorRowEnd < 0) {
            return;
        }
        int age = messages.get(anchorIndex).getUpdatedCounter();
        int chatLineId = allocateChatLineId();
        int[] showcaseIds = decodeShowcases(packet);
        int messagesBefore = messages.size();
        chat.printChatMessageWithOptionalDeletion(
                build(packet, tab, showcaseIds, false, kind, body),
                chatLineId);
        if (messages.size() != messagesBefore + 1 || messages.get(0) == null
                || messages.get(0).getChatLineID() != chatLineId) {
            // The list did not grow by the one line at its head: the
            // game trimmed or refused, and there is nothing to move.
            ChatWindowLines.noteMutated();
            return;
        }
        ChatLine added = messages.remove(0);
        messages.add(anchorIndex + 1, new ChatLine(age, added.func_151461_a(),
                chatLineId));
        List<ChatLine> rows = new ArrayList<ChatLine>();
        while (!drawn.isEmpty() && drawn.get(0) != null
                && drawn.get(0).getChatLineID() == chatLineId) {
            rows.add(drawn.remove(0));
        }
        // The rows stood newest first at the head and keep that order
        // behind the anchor's last row, so the message reads top down;
        // taking them off the head moved nothing after them, so the
        // anchor's last row is where it was found.
        int insertAt = anchorRowEnd + 1;
        for (int index = 0; index < rows.size(); index++) {
            drawn.add(insertAt + index, new ChatLine(age,
                    rows.get(index).func_151461_a(), chatLineId));
        }
        ChatWindowLines.noteMutated();
        ChatGroupRuns.remember(chatLineId, tab, packet.getSenderId(),
                packet.getIdentityName(), packet.isAccountLine(),
                packet.getTimestampMillis(), !packet.getReply().exists(),
                build(packet, tab, showcaseIds, !packet.getReply().exists(),
                        kind, body == null ? null : body.createCopy()));
        ClientChatMessageIds.remember(chatLineId, packet.getMessageId());
        ClientChatMessages.remember(packet, tab, showcaseIds, kind,
                body == null ? null : body.createCopy());
        ClientChatChannelViews.recordBackfilled(chatLineId, tab);
        ClientChatChannelViews.recordTime(chatLineId, packet.getTimestampMillis());
    }

    /**
     * Everything a printed line is remembered by: its grouped form for
     * the runs, its ids, its packet for a later rebuild, and its tab.
     * {@code groupedBody} is a copy of the body of its own, for the
     * grouped form.
     */
    private static void rememberPrinted(int chatLineId,
                                        LostTalesChatMessagePacket packet,
                                        ChatTab tab, int[] showcaseIds,
                                        ChatBodyKind kind,
                                        IChatComponent groupedBody,
                                        boolean mentioned) {
        // Taken before the grouped form claims the body as its own.
        IChatComponent keptBody = groupedBody == null ? null
                : groupedBody.createCopy();
        ChatGroupRuns.remember(chatLineId, tab, packet.getSenderId(),
                packet.getIdentityName(), packet.isAccountLine(),
                packet.getTimestampMillis(),
                // A reply keeps its header: the quote above it answers
                // for a sender the grouped form would not name.
                !packet.getReply().exists(),
                build(packet, tab, showcaseIds,
                        !packet.getReply().exists(), kind, groupedBody));
        ClientChatMessageIds.remember(chatLineId, packet.getMessageId());
        // Kept so the same line can be built again if it is edited or
        // its reactions change.
        ClientChatMessages.remember(packet, tab, showcaseIds, kind, keptBody);
        noteLinePrinted(chatLineId, tab, mentioned,
                packet.getTimestampMillis());
    }

    /**
     * A line this client signs itself, the way the server would sign
     * it: the identity the tab speaks as, its roles and its colour, the
     * account's skin remembered for the head. The LOTR title is the
     * server's to resolve, so a locally signed line carries none. Every
     * line built here — the pending echo of a message, the player's
     * half of an NPC conversation, the echo of a command — is signed
     * through this one factory.
     */
    private static LostTalesChatMessagePacket signedPacket(
            Minecraft minecraft, ChatTab tab, String message,
            List<ChatShowcase> showcases, ChatReplyReference reply,
            long messageId, long timestampMillis) {
        ClientChatIdentity.Signature signature = ClientChatIdentity.of(tab);
        LostTalesChatMessagePacket packet = new LostTalesChatMessagePacket(
                tab.getChannel(), minecraft.thePlayer.getUniqueID(),
                signature.identityName, signature.accountName, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                signature.nameColor,
                message, timestampMillis, signature.skinId,
                showcases, "", tab.isWhisper() ? tab.getPartner() : "",
                signature.roles, signature.accountLine, messageId, reply,
                tab.isWhisper() && !tab.isNpc()
                        ? tab.getPartnerIdentity() : "");
        if (signature.accountLine) {
            LostTalesCharacterHeadIconRenderer.rememberAccountSkin(
                    minecraft, packet.getSenderId(),
                    signature.accountName);
        }
        return packet;
    }

    /**
     * The player's own line in an NPC conversation: nobody is on the
     * other end, so nothing is sent; the line is signed the way the
     * server signs a whisper of theirs — the appearance the tab speaks
     * as, its roles and its colour — and filed under the NPC's tab. The
     * things the player shared were resolved by the client from its
     * own inventory and marker cache, since no server ever sees them,
     * and the quote of a reply is the client's own too: the author and
     * the text off the line it was answering, which is the only record
     * either of them has.
     */
    public static boolean echoToNpc(ChatTab tab, String message,
                                    List<ChatShowcase> showcases,
                                    ChatReplyReference reply) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || !tab.isNpc() || message == null
                || !ChatMessageValidator.isValid(message)
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null) {
            return false;
        }
        LostTalesChatMessagePacket packet = signedPacket(minecraft, tab,
                message, showcases, reply, ClientChatMessageIds.nextLocal(),
                System.currentTimeMillis());
        if (ChatWindowLayout.openTab(tab, windowIdOfSelection()) == null) {
            return false;
        }
        // An NPC conversation never passes through the server, so the
        // mention check the served channels get on receipt runs here:
        // naming yourself pings you in this tab exactly as anywhere
        // else.
        boolean mentioned = LostTalesConfig.enableChatPings
                && isLocalPlayerMentioned(minecraft, message);
        int chatLineId = print(minecraft, packet, tab, mentioned);
        if (mentioned) {
            markPinged(chatLineId);
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        return true;
    }

    /**
     * Shows a message the moment it is typed, before any server has
     * seen it, and answers with the name it was remembered under — or
     * zero when nothing was shown, which is when the caller should
     * simply send and wait.
     *
     * <p>The line is this client's own work, signed the way the server
     * would sign it, and it is faint until the server's copy arrives to
     * take its place. It carries no message id worth the name: replies,
     * edits and quotes all name a message by what the server stamped on
     * it, and until that comes back there is nothing to name. Nothing
     * is shown early unless the history can actually be reached, since
     * the promise is only kept by rewriting the line where it stands.</p>
     */
    public static long echoPending(ChatTab tab, String message,
                                   List<ChatShowcase> showcases,
                                   ChatReplyReference reply) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || tab.isNpc() || message == null
                || !ChatMessageValidator.isValid(message)
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null
                || ChatWindowLines.messageHistory(
                        minecraft.ingameGUI.getChatGUI()) == null) {
            return 0L;
        }
        LostTalesChatMessagePacket packet = signedPacket(minecraft, tab,
                message, showcases, reply, ClientChatMessageIds.nextLocal(),
                System.currentTimeMillis());
        long nonce = ClientChatPendingEchoes.nextNonce();
        // Never pinged and never sounded: naming yourself in your own
        // message is answered for by the copy that comes back, and
        // answering it twice would ring twice.
        int chatLineId = print(minecraft, packet, tab, false);
        ClientChatPendingEchoes.remember(nonce, chatLineId, packet, tab,
                decodeShowcases(packet), System.currentTimeMillis());
        return nonce;
    }

    /**
     * Turns the line a message was promised on into the message itself,
     * answering with that line, or zero when this was not a message
     * this client had already shown.
     *
     * <p>Only a line this player signed can be confirmed, and only by a
     * copy carrying the name they gave it. The line keeps its place in
     * the conversation and gains everything a delivered message has:
     * the server's id, its quote, its showcases as the server resolved
     * them.</p>
     */
    private static int confirmPendingEcho(Minecraft minecraft,
                                          LostTalesChatMessagePacket packet,
                                          ChatTab tab) {
        if (packet.getEchoNonce() == 0L || minecraft.thePlayer == null
                || !minecraft.thePlayer.getUniqueID().equals(
                        packet.getSenderId())) {
            return 0;
        }
        ClientChatPendingEchoes.Pending pending =
                ClientChatPendingEchoes.take(packet.getEchoNonce());
        if (pending == null) {
            return 0;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        int chatLineId = pending.chatLineId;
        int[] showcaseIds = decodeShowcases(packet);
        if (!ChatWindowLines.replaceMessage(chat, chatLineId,
                build(packet, tab, showcaseIds, false))) {
            // The promised line is no longer in the history to rewrite.
            // Take it out if it is anywhere at all and print the real
            // message afresh, rather than leaving the two side by side.
            ChatWindowLines.removeMessage(chat, chatLineId);
            ChatGroupRuns.forget(chatLineId);
            LostTalesChatHistoryHooks.refresh(chat);
            return 0;
        }
        ChatGroupRuns.remember(chatLineId, tab, packet.getSenderId(),
                packet.getIdentityName(), packet.isAccountLine(),
                packet.getTimestampMillis(),
                !packet.getReply().exists(),
                build(packet, tab, showcaseIds,
                        !packet.getReply().exists()));
        ClientChatMessageIds.remember(chatLineId, packet.getMessageId());
        ClientChatMessages.remember(packet, tab, showcaseIds);
        LostTalesChatHistoryHooks.refresh(chat);
        return chatLineId;
    }

    /**
     * Gives up on messages the server never answered for, marking each
     * line undelivered where it stands. A dropped message is visibly
     * dropped: leaving it faint in the history would leave it looking
     * like it was still on its way, and leaving it plain would leave it
     * looking sent.
     */
    public static void expirePendingEchoes() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        List<ClientChatPendingEchoes.Pending> gone =
                ClientChatPendingEchoes.expired(System.currentTimeMillis());
        if (gone.isEmpty()) {
            return;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        boolean changed = false;
        for (ClientChatPendingEchoes.Pending pending : gone) {
            IChatComponent line = markUndelivered(build(pending.packet,
                    pending.tab, pending.showcaseIds, false));
            if (ChatWindowLines.replaceMessage(chat, pending.chatLineId,
                    line)) {
                ChatGroupRuns.replaceGroupedLine(pending.chatLineId,
                        markUndelivered(build(pending.packet, pending.tab,
                                pending.showcaseIds, true)));
                changed = true;
            }
        }
        if (changed) {
            LostTalesChatHistoryHooks.refresh(chat);
        }
    }

    /** The note that a message never arrived, in the palette's alarm red. */
    private static IChatComponent markUndelivered(IChatComponent line) {
        int color = LostTalesColors.rgb(LostTalesColors.CRIMSON);
        ChatComponentText mark = text(
                StatCollector.translateToLocal(
                        "gui.losttales.chat.undelivered"),
                nearestFormatting(color), false);
        mark.getChatStyle().setItalic(Boolean.TRUE);
        return line.appendSibling(mark);
    }

    /**
     * The window the selected tab is in, or null with nothing selected:
     * where a reopening tab lands first, like any other reopening
     * channel.
     */
    static String windowIdOfSelection() {
        ChatWindow window = ChatWindowLayout.windowOf(
                ClientChatChannelState.getSelected());
        return window == null ? null : window.getId();
    }

    /**
     * The mention cue, only on this client and only because this client's
     * own names matched: the server never triggers it and no other client
     * can hear it. Played as a UI sound so the player's position, the
     * dimension, or a respawn in progress cannot swallow or duplicate it.
     */
    private static void playPingSound(Minecraft minecraft) {
        String sound = LostTalesConfig.chatPingSound == null
                ? "" : LostTalesConfig.chatPingSound.trim();
        if (sound.length() == 0 || minecraft.getSoundHandler() == null) {
            return;
        }
        // Audio only: every caller has already recorded its visual ping.
        // This is the one place the cue sounds, so the collapse of
        // near-simultaneous copies needs no cooldown in any producer.
        long now = System.nanoTime();
        if (lastPingSoundNanos != 0L
                && now - lastPingSoundNanos < PING_SOUND_WINDOW_NANOS) {
            return;
        }
        lastPingSoundNanos = now;
        minecraft.getSoundHandler().playSound(
                new LostTalesChatPingSound(new ResourceLocation(sound)));
    }

    /**
     * Whether the line being printed is one the server is replaying from
     * its history rather than one just said. Set around the print by
     * {@link #receive(LostTalesChatMessagePacket, boolean, boolean)} and
     * {@link #receiveConsoleEvent(ChatConsoleEvent, boolean, boolean)} on
     * the client thread, read where the line is filed and counted.
     */
    private static boolean receivingReplayed;

    /**
     * Whether the line being printed was said before this player
     * arrived: history printed while the client catches up. Set around
     * the print by {@link #receive(LostTalesChatMessagePacket, boolean,
     * boolean)} and {@link #receiveConsoleEvent(ChatConsoleEvent,
     * boolean, boolean)}; the print stamps such a line as already gone
     * from the closed feed, and it does not enter as the newest message.
     */
    private static boolean receivingBeforeArrival;

    /**
     * The server's id for the line being printed where the line's own id
     * is this client's: a console entry's, which comes from the clock
     * messages take theirs from, so the Console's read mark can move to
     * it while the line stays this client's work. Set around the print
     * by {@link #receiveConsoleEvent(ChatConsoleEvent, boolean,
     * boolean)}; none for every other line.
     */
    private static long receivingServerId = ChatMessageIds.NONE;

    /**
     * Stamps a line said before this player arrived as one the closed
     * feed has already let go: its arrival tick is set a whole fade back
     * in both of the game's lists, so the feed — which shows only what
     * arrived in its last few seconds — passes over it, while the open
     * windows, which show a line whatever its age, keep it. The game
     * puts a printed line at the head of each list, so only the head is
     * looked at.
     */
    private static void ageOutOfFeed(Minecraft minecraft, GuiNewChat chat,
                                     int chatLineId) {
        int aged = minecraft.ingameGUI.getUpdateCounter()
                - LostTalesChatOverlayRenderer.FEED_FADE_TICKS;
        restampHead(ChatWindowLines.messageHistory(chat), chatLineId, aged);
        List<ChatLine> drawn;
        try {
            drawn = LostTalesChatOverlayRenderer.getDrawnLines(chat);
        } catch (IllegalAccessException unavailable) {
            // The field was opened when the renderer loaded; without it
            // the shared list is not drawn at all, so there is nothing
            // to stamp.
            drawn = null;
        }
        restampHead(drawn, chatLineId, aged);
        ChatWindowLines.noteMutated();
    }

    /** Gives the rows at the head of {@code lines} that are the line's own that tick. */
    private static void restampHead(List<ChatLine> lines, int chatLineId,
                                    int updatedCounter) {
        if (lines == null) {
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            ChatLine line = lines.get(index);
            if (line == null || line.getChatLineID() != chatLineId) {
                return;
            }
            lines.set(index, new ChatLine(updatedCounter,
                    line.func_151461_a(), chatLineId));
        }
    }

    /** Records animation timing and the line's tab for the tab views. */
    private static void noteLinePrinted(int chatLineId, ChatTab tab,
                                        boolean mentioned,
                                        long timestampMillis) {
        // A line from before this player arrived was never news here:
        // it does not enter as the newest message.
        if (!receivingBeforeArrival) {
            lastMessageChatLineId = chatLineId;
            hasLastMessage = true;
            lastMessageNanos = System.nanoTime();
            lastMessageTab = tab;
        }
        // What the view's read mark moves to once the line is seen: the
        // line's own id, or the id of the console entry it shows.
        long serverId = ChatMessageIds.isServerId(receivingServerId)
                ? receivingServerId
                : ClientChatMessageIds.messageIdOf(chatLineId);
        ClientChatChannelViews.record(chatLineId, tab,
                ClientChatChannelState.getSelected(), mentioned,
                serverId, timestampMillis, receivingReplayed);
        ClientChatChannelViews.recordTime(chatLineId, timestampMillis);
    }

    /**
     * Decodes each validated showcase exactly once and registers it for the
     * renderer; the result maps token index to store key (-1 when the
     * server attached nothing for that token).
     */
    private static int[] decodeShowcases(LostTalesChatMessagePacket packet) {
        List<ChatShowcase> showcases = packet.getShowcases();
        if (showcases == null || showcases.isEmpty()) {
            return NO_SHOWCASES;
        }
        int[] ids = new int[ChatShareTokenParser.MAX_TOKENS];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = -1;
        }
        for (ChatShowcase showcase : showcases) {
            if (showcase.getKind() == ChatShareKind.ITEM) {
                ItemStack stack = ChatShowcase.decodeStack(
                        showcase.getStackData());
                if (stack != null) {
                    ids[showcase.getTokenIndex()] =
                            ClientChatShowcaseStore.registerItem(stack);
                }
            } else {
                ids[showcase.getTokenIndex()] =
                        ClientChatShowcaseStore.registerMarker(showcase);
            }
        }
        return ids;
    }

    /**
     * Whether the message names this player: any of their own names
     * ({@link #localMentionNames}) in every channel alike, plus the
     * name of every mentionable role they hold — in every channel too,
     * since an operator is worth calling wherever the call is made — so
     * {@code @Operator} reaches the operators and nobody else. Which
     * roles the player holds is the server's word, sent with the chat
     * access; nothing here is decided from the message.
     */
    private static boolean isLocalPlayerMentioned(
            Minecraft minecraft, String message) {
        List<String> names = localMentionNames(minecraft);
        for (ChatAccountRole role : ClientChatChannelState.localRoles()) {
            if (role.isMentionable()) {
                names.add(role.getDisplayName());
            }
        }
        return ChatMentions.mentionsAny(message, names);
    }

    /**
     * The message the pointer is resting on, by chat line id, or zero.
     * Every line of it lifts a shade while it is, the way a row does in
     * any messenger — a message is what the pointer is on, not the one
     * wrapped line under it, so the whole of it answers together. Set
     * from the screen each frame it draws.
     */
    private static int hoveredChatLineId;

    static void setHoveredLine(int chatLineId) {
        hoveredChatLineId = chatLineId;
    }

    /**
     * Whether the line can be answered: it is a line at all — a blank
     * row and a day's rule are nobody's — and the tab it lives in takes
     * messages. A message the server named is answered by its id; an
     * announcement, a death message, a console notice, a command's echo
     * or an NPC's speech is answered by quoting its words, since nothing
     * names it. One rule, asked by the message menu and by the toolbar.
     */
    static boolean isRepliable(int chatLineId) {
        return chatLineId != 0
                && ClientChatChannelState.canSend(
                        ClientChatChannelViews.tabOf(chatLineId));
    }

    /**
     * The name a reply quotes a line under: whoever signed it, else the
     * chat's own word for a line nobody signed.
     */
    static String quoteAuthorFor(String signedName) {
        return signedName != null && signedName.trim().length() > 0
                ? signedName.trim()
                : StatCollector.translateToLocal(
                        "chat.losttales.reply.unnamed");
    }

    /**
     * The head a line was signed with — its sender's face, an NPC's
     * portrait, or the mark standing for the server or the bridge — read
     * off the message as the game keeps it whole, so a grouped
     * continuation, which draws no header of its own, answers too. Null
     * for a line with no sender, or one the game no longer holds.
     */
    static ChatHeadMarker.Data headOfLine(int chatLineId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (chatLineId == 0 || minecraft == null
                || minecraft.ingameGUI == null) {
            return null;
        }
        List<ChatLine> messages = ChatWindowLines.messageHistory(
                minecraft.ingameGUI.getChatGUI());
        if (messages == null) {
            return null;
        }
        for (int index = 0; index < messages.size(); index++) {
            ChatLine message = messages.get(index);
            if (message != null && message.getChatLineID() == chatLineId) {
                return ChatHeadMarker.of(message.func_151461_a());
            }
        }
        return null;
    }

    /** Whether the line belongs to the message the pointer is on. */
    static boolean isHoveredLine(int chatLineId) {
        return hoveredChatLineId != 0 && chatLineId == hoveredChatLineId;
    }

    /**
     * The component the pointer rests on this frame, or null: what the
     * text drawing underlines when the component answers to a click or
     * carries a card, so a link, a name or a quote says it can be used
     * before it is.
     */
    private static IChatComponent hoveredComponent;
    /**
     * The drawn row the hovered run is on, and the run's place in it,
     * counted over every component the row's iterator yields. A row's
     * iterator hands out copies of its runs, so a run is never the
     * same object twice; the row itself is, and the place tells the
     * run apart from every other on it.
     */
    private static IChatComponent hoveredLine;
    private static int hoveredIndex = -1;

    static void setHoveredComponent(IChatComponent line, int index,
                                    IChatComponent component) {
        hoveredLine = component == null ? null : line;
        hoveredIndex = component == null ? -1 : index;
        hoveredComponent = component;
    }

    /** The component under the pointer this frame, or null. */
    static IChatComponent hoveredComponent() {
        return hoveredComponent;
    }

    /** Whether the run at {@code index} of {@code line} is the hovered one. */
    static boolean isHoveredRun(IChatComponent line, int index) {
        return line != null && line == hoveredLine && index == hoveredIndex;
    }

    /** Whether the hovered run is on {@code line}. */
    static boolean isHoveredLineRow(IChatComponent line) {
        return line != null && line == hoveredLine;
    }

    /**
     * The drawn row whose sender the pointer rests on this frame, or
     * null. Answered by the hover card's own hit test — the head, the
     * brackets, the name and the title, and not the gap after the
     * closing bracket — so the underline under a name, the card and
     * the hand cursor all light on the same pixels.
     */
    private static IChatComponent hoveredSenderRow;

    static void setHoveredSenderRow(IChatComponent row) {
        hoveredSenderRow = row;
    }

    /** Whether the pointer rests on the sender of {@code line}. */
    static boolean isHoveredSenderRow(IChatComponent line) {
        return line != null && line == hoveredSenderRow;
    }

    /**
     * How far each message's hover shade has crossed in, by chat line
     * id, and the frame it was last advanced on. Advanced once per
     * frame however many wrapped rows the message has, and forgotten
     * once it has crossed back out or the message has left the screen.
     */
    private static final Map<Integer, HoverFade> HOVER_FADES =
            new HashMap<Integer, HoverFade>();
    /**
     * How far each reaction chip has lit under the pointer, by message
     * and emoji, kept and forgotten the same way.
     */
    private static final Map<String, HoverFade> CHIP_FADES =
            new HashMap<String, HoverFade>();
    private static long frameIndex;
    private static long frameNanos;
    private static double frameElapsedSeconds;

    private static final class HoverFade {
        float value;
        long frame;
    }

    /**
     * Opens a frame of the chat screen: reads the clock every fade below
     * steps on, and forgets the fades of lines the last frame did not
     * draw. Called once per frame, before any window is drawn.
     */
    static void beginFrame() {
        long now = System.nanoTime();
        frameElapsedSeconds = frameNanos == 0L ? 0.0D
                : Math.min(0.25D, (now - frameNanos) / 1.0E9D);
        frameNanos = now;
        frameIndex++;
        Iterator<HoverFade> stale = HOVER_FADES.values().iterator();
        while (stale.hasNext()) {
            if (stale.next().frame < frameIndex - 1) {
                stale.remove();
            }
        }
        Iterator<HoverFade> staleChips = CHIP_FADES.values().iterator();
        while (staleChips.hasNext()) {
            if (staleChips.next().frame < frameIndex - 1) {
                staleChips.remove();
            }
        }
    }

    /**
     * The share of the pointer's shade a message wears this frame: 1
     * while the pointer rests on it, 0 while it does not, and on the way
     * between them the controls' own crossfade — so the shade and the
     * stamp it brings out come and go rather than switch. A hard cut
     * while the chat's animations are off.
     */
    static float lineHoverFade(int chatLineId, boolean hovered) {
        if (chatLineId == 0 || !LostTalesConfig.enableChatAnimations) {
            return hovered ? 1.0F : 0.0F;
        }
        Integer key = Integer.valueOf(chatLineId);
        HoverFade fade = HOVER_FADES.get(key);
        if (fade == null) {
            if (!hovered) {
                return 0.0F;
            }
            fade = new HoverFade();
            HOVER_FADES.put(key, fade);
        }
        if (fade.frame != frameIndex) {
            fade.frame = frameIndex;
            fade.value = LostTalesChatVisualStyle.hoverFade(fade.value,
                    hovered, frameElapsedSeconds);
        }
        if (fade.value <= 0.0F && !hovered) {
            HOVER_FADES.remove(key);
            return 0.0F;
        }
        return fade.value;
    }

    /**
     * The share of its lit artwork a reaction chip wears this frame, on
     * the crossfade and the clock a message's shade steps on: a chip
     * under the pointer crosses to it and back rather than switching.
     */
    static float chipHoverFade(ChatReactionMarker.Data chip,
                               boolean hovered) {
        if (chip == null || !LostTalesConfig.enableChatAnimations) {
            return hovered ? 1.0F : 0.0F;
        }
        String key = chip.messageId + ":" + chip.key;
        HoverFade fade = CHIP_FADES.get(key);
        if (fade == null) {
            if (!hovered) {
                return 0.0F;
            }
            fade = new HoverFade();
            CHIP_FADES.put(key, fade);
        }
        if (fade.frame != frameIndex) {
            fade.frame = frameIndex;
            fade.value = LostTalesChatVisualStyle.hoverFade(fade.value,
                    hovered, frameElapsedSeconds);
        }
        if (fade.value <= 0.0F && !hovered) {
            CHIP_FADES.remove(key);
            return 0.0F;
        }
        return fade.value;
    }

    /**
     * How long a jumped-to line stays lit: long enough to find with the
     * eye, short enough not to be mistaken for a state the line is in.
     */
    private static final long FLASH_NANOS = 1200L * 1000000L;
    private static int flashedChatLineId;
    private static long flashedNanos;

    /**
     * Lights a line for a moment: what a jump to a quoted message leaves
     * behind, so the message the view landed on is the one the eye finds.
     */
    static void flashLine(int chatLineId) {
        flashedChatLineId = chatLineId;
        flashedNanos = System.nanoTime();
    }

    /** How lit the line is right now, 1 to 0 as the moment passes. */
    static float flashStrength(int chatLineId) {
        if (flashedNanos == 0L || chatLineId != flashedChatLineId) {
            return 0.0F;
        }
        long elapsed = System.nanoTime() - flashedNanos;
        if (elapsed < 0L || elapsed >= FLASH_NANOS) {
            return 0.0F;
        }
        return 1.0F - elapsed / (float)FLASH_NANOS;
    }

    /** Remembers a mention so every wrapped line of it stays highlighted. */
    static void markPinged(int chatLineId) {
        pingedChatLineIds.add(Integer.valueOf(chatLineId));
        while (pingedChatLineIds.size() > MAX_PINGED_LINES) {
            Iterator<Integer> iterator = pingedChatLineIds.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    static boolean isPingedLine(int chatLineId) {
        return pingedChatLineIds.contains(Integer.valueOf(chatLineId));
    }

    static long getLastMessageNanos() {
        return lastMessageNanos;
    }

    /** Whether {@code chatLineId} is the newest message printed. */
    static boolean isLastMessage(int chatLineId) {
        return hasLastMessage && chatLineId == lastMessageChatLineId;
    }

    static ChatTab getLastMessageTab() {
        return lastMessageTab;
    }

    /**
     * The game has cleared its own message list — it does so whenever
     * the main menu opens, so every trip through it, leaving a world or
     * a server, empties the chat — and everything said about those lines
     * goes with them; the tabs, the layout, the identities and what the
     * session knows of the server stay, and the read marks say where
     * the replay's unread run begins. The line ids go on counting, so
     * nothing still holding one can mistake a new line for an old.
     */
    public static void onVanillaHistoryCleared() {
        ClientChatChannelViews.forgetLines();
        lastMessageChatLineId = 0;
        hasLastMessage = false;
        lastMessageTab = null;
        pingedChatLineIds.clear();
        flashedChatLineId = 0;
        flashedNanos = 0L;
        hoveredChatLineId = 0;
        hoveredComponent = null;
        hoveredLine = null;
        hoveredSenderRow = null;
        LostTalesChatHoverCard.unpin();
        hoveredIndex = -1;
        pendingJumpChatLineId = 0;
        lastCommandEchoTab = null;
        lastCommandEchoLineId = 0;
        lastCommandEcho = null;
        HOVER_FADES.clear();
        CHIP_FADES.clear();
        ChatSpoilerMarker.clear();
    }

    public static void clear() {
        lastMessageNanos = 0L;
        lastMessageChatLineId = 0;
        hasLastMessage = false;
        lastMessageTab = null;
        nextChatLineId = Integer.MIN_VALUE;
        pingedChatLineIds.clear();
        lastPingSoundNanos = 0L;
        flashedChatLineId = 0;
        flashedNanos = 0L;
        hoveredChatLineId = 0;
        hoveredComponent = null;
        hoveredLine = null;
        hoveredSenderRow = null;
        LostTalesChatHoverCard.unpin();
        hoveredIndex = -1;
        pendingJumpChatLineId = 0;
        lastCommandEchoTab = null;
        lastCommandEchoLineId = 0;
        lastCommandEcho = null;
        commandAnswered = false;
        ANSWERED_COMMANDS.clear();
        HOVER_FADES.clear();
        CHIP_FADES.clear();
        frameNanos = 0L;
        commandTab = null;
        commandUntilMillis = 0L;
        ChatSpoilerMarker.clear();
    }

    private static int allocateChatLineId() {
        int allocated = nextChatLineId;
        nextChatLineId = nextChatLineId == -1
                ? Integer.MIN_VALUE : nextChatLineId + 1;
        return allocated;
    }

    static IChatComponent build(LostTalesChatMessagePacket packet) {
        return build(packet, tabOf(packet), NO_SHOWCASES);
    }

    /**
     * The tab a packet would be filed under: a whisper's conversation
     * with the partner's identity, held as the character this copy says
     * it is held as; a scoped channel's line under the identity of this
     * player's that is in the conversation it was said in.
     */
    private static ChatTab tabOf(LostTalesChatMessagePacket packet) {
        ChatTab filed = fileUnder(packet);
        ClientChatContextHistory.remember(filed, packet.getMessageId());
        return filed;
    }

    private static ChatTab fileUnder(LostTalesChatMessagePacket packet) {
        if (packet.getChannel() == ChatChannel.WHISPER) {
            return ChatTab.whisper(packet.getPartner(),
                    packet.getPartnerIdentity(),
                    ChatTab.ownerKeyOf(packet.getOwnCharacterId()));
        }
        // A line of a scoped channel belongs to the conversation it was
        // said in, named by that conversation: a Gondor line is Gondor's,
        // whichever character of this player's reads it, and is never
        // shown under the Rohan one.
        return ChatTab.of(packet.getChannel(), packet.getScopeValue());
    }

    /**
     * The line as it is shown, filed under {@code tab}. The tab is the
     * caller's, not the packet's: an NPC conversation and a whisper with
     * a player of the same name are two different tabs, and only the
     * caller knows which one this line belongs to.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds) {
        return build(packet, tab, showcaseIds, false);
    }

    /**
     * As above; a <em>grouped</em> line continues its sender's run and
     * drops the repeated header — the channel prefix, tags, brackets,
     * head, name and title — keeping only the timestamp and the body,
     * which starts behind the same chevron the run's first body row
     * does, so a run reads as one voice speaking in paragraphs.
     * The prefix goes with the rest: in the closed feed the run's
     * header line already named the channel, so its continuations do
     * not say it again.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds,
                                boolean grouped) {
        return build(packet, tab, showcaseIds, grouped,
                ChatBodyKind.MESSAGE);
    }

    /**
     * As above, with the body presented as {@code kind} says: what
     * stands between the sender and the body, and whether the body is
     * read for markup, emoji, links, mentions and shares or shown
     * exactly as it is.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds,
                                boolean grouped, ChatBodyKind kind) {
        return build(packet, tab, showcaseIds, grouped, kind, null);
    }

    /**
     * As above with the body as a component of its own, for a kind
     * that shows one as it came ({@link ChatBodyKind#ANSWER}); null
     * for every other kind, whose body is read off the packet.
     */
    static IChatComponent build(LostTalesChatMessagePacket packet,
                                ChatTab tab, int[] showcaseIds,
                                boolean grouped, ChatBodyKind kind,
                                IChatComponent body) {
        IChatComponent line = buildLine(packet, tab, showcaseIds, grouped,
                kind, body);
        appendReactions(line, packet);
        return line;
    }

    /**
     * The reactions a message wears, on a row of its own under its words
     * and still a part of it: the same line id, so the row shades,
     * copies, scrolls and goes with the message. One chip per emoji,
     * in the order each was first used; nothing for a message without
     * reactions or one the server never named. An emoji the registry
     * lacks, which a Discord member brought, is a chip like any other,
     * drawn with a question mark.
     */
    private static void appendReactions(IChatComponent root,
                                        LostTalesChatMessagePacket packet) {
        ChatReactionSummary reactions = packet.getReactions();
        if (reactions.isEmpty()
                || !ChatMessageIds.isServerId(packet.getMessageId())) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        FontRenderer font = minecraft == null ? null : minecraft.fontRenderer;
        root.appendSibling(ChatLayoutMarker.rowBreak());
        boolean first = true;
        for (ChatReactionSummary.Reaction reaction : reactions.getReactions()) {
            if (!first) {
                root.appendSibling(ChatSpacerMarker.of(
                        ChatReactionMarker.BETWEEN));
            }
            first = false;
            root.appendSibling(ChatReactionMarker.create(reaction.emoji,
                    reaction.count, reaction.mine, packet.getMessageId(),
                    ChatReactionMarker.countWidth(font, reaction.count)));
        }
    }

    private static IChatComponent buildLine(LostTalesChatMessagePacket packet,
                                            ChatTab tab, int[] showcaseIds,
                                            boolean grouped, ChatBodyKind kind,
                                            IChatComponent body) {
        ChatChannel channel = packet.getChannel();
        ChatComponentText root = new ChatComponentText("");
        ChatTab named = tab == null ? tabOf(packet) : tab;
        if (grouped) {
            appendTimestamp(root, packet.getTimestampMillis());
            root.appendSibling(ChatLayoutMarker.anchor());
            appendBody(root, packet, showcaseIds, channel, kind, body);
            return root;
        }
        // A reply opens with the message it answers, on a row of its
        // own above the line.
        if (packet.getReply().exists()) {
            appendReplyQuote(root, packet.getReply());
            root.appendSibling(ChatLayoutMarker.lineBreak());
        }
        // The prefix names the tab, so it is the colour the tab is drawn
        // in: a conversation speaks in the other party's colour, every
        // other channel in its own. Nothing in the feed may name a
        // channel in a colour its tab does not use. Faction takes the
        // sender's own faction colour, which the server put on the line
        // and which is the receiver's too — the server routes faction
        // chat to members only.
        appendChannelPrefix(root, named, channel == ChatChannel.FACTION
                ? packet.getNameColor()
                : ClientChatChannelState.displayColor(named));
        // (An NPC conversation names the same partner, so its prefix
        // reads the same.)
        appendTimestamp(root, packet.getTimestampMillis());
        // Continuation lines of a wrapped message align here, under the
        // sender's opening bracket; see ChatLineWrapper.
        root.appendSibling(ChatLayoutMarker.anchor());

        // The server's word on an account line's sender: every role it
        // holds, tagged ahead of the name in the role's own colour. The
        // name's colour is the primary role's too, but that is already
        // the packet's name colour — the server set it when it built the
        // line, so nothing here decides what a role looks like. The tag
        // carries the role mention marker, so hovering it shows the
        // role's card exactly as hovering @Operator does.
        for (ChatAccountRole role : ChatAccountRole.fromMask(
                packet.getRoles())) {
            root.appendSibling(ChatMentionMarker.applyRole(
                    text(role.getDisplayTag() + " ",
                            nearestFormatting(role.getColor()), false),
                    role.getColor(), role));
        }
        // The brackets are part of the name: they answer to a hover
        // and a click exactly as it does, so the card comes up wherever
        // the pointer is over the sender. Their colour comes from the
        // head marker, which is where every part of the name takes it.
        String whisper = ChatSenderSpan.suggestionFor(packet.getAccountName());
        root.appendSibling(reply(text("<", nearestFormatting(
                packet.getNameColor()), false), whisper));
        // Two bold spaces stand in for the head; what the line actually
        // advances by is the slot ChatInlineIcons declares, which every
        // walk over the line — drawing, wrapping, hit testing — reads.
        // The spaces are only so the raw text has something there.
        ChatComponentText marker = text("  ",
                EnumChatFormatting.WHITE, true);
        // The head is the line's identity's, not the channel's: an
        // account line wears the account head wherever it was said.
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encode(packet.getSenderId(),
                                packet.isAccountLine(),
                                packet.getSkinId(), packet.getMessage(),
                                packet.getTitleColor(),
                                packet.getNameColor()))));
        root.appendSibling(marker);

        root.appendSibling(reply(text(packet.getIdentityName(),
                nearestFormatting(packet.getNameColor()), false), whisper));
        if (channel == ChatChannel.WHISPER && !packet.getIdentityName()
                .equalsIgnoreCase(packet.getAccountName())) {
            // A whisper spoken as a character still says who is behind
            // it: the account in brackets, part of the name — it answers
            // to the pointer the way the name does.
            root.appendSibling(reply(text(
                    " (" + packet.getAccountName() + ")",
                    nearestFormatting(packet.getNameColor()), false),
                    whisper));
        }
        if (packet.getTitle().length() > 0) {
            // LOTR's NPC naming, "Name, the Gondor Farmer": the epithet is
            // the sender's faction and title; an untitled sender gets no
            // comma, no "the", nothing.
            String epithet = ChatEpithet.epithet(packet.getFactionName(),
                    packet.getTitle());
            root.appendSibling(ChatTitleMarker.apply(
                    text(ChatEpithet.translate("chat.losttales.title.suffix",
                            ", the %s", epithet),
                            nearestFormatting(packet.getTitleColor()),
                            false),
                    packet.getTitleColor(), epithet));
        }
        // The closing bracket keeps the same clear space from what
        // precedes it that the opening one keeps from the head; a glyph
        // carries one pixel of that as its own trailing space.
        root.appendSibling(ChatSpacerMarker.of(
                ChatInlineIcons.NAME_GAP - 1));
        root.appendSibling(reply(text("> ", nearestFormatting(
                packet.getNameColor()), false), whisper));
        // The header ends here; the body stands on the next row.
        appendBody(root, packet, showcaseIds, channel, kind, body);
        return root;
    }

    /**
     * The body row of a line, the same for its full and its grouped
     * form: the body break carrying the sender's colour and the words
     * the kind opens the body with, then the body itself — read for
     * everything a message may carry, or shown exactly as it is — and
     * the spoiler marks, numbered over the body alone so the two forms
     * of one message agree on which spoiler is which, and so a spoiler
     * quoted by a reply above stays covered.
     */
    private static void appendBody(ChatComponentText root,
                                   LostTalesChatMessagePacket packet,
                                   int[] showcaseIds, ChatChannel channel,
                                   ChatBodyKind kind, IChatComponent body) {
        root.appendSibling(kind.opensBare()
                ? ChatLayoutMarker.bodyBreakBare(packet.getNameColor())
                : ChatLayoutMarker.bodyBreak(packet.getNameColor(),
                        bodyLabel(kind)));
        int bodyStart = root.getSiblings().size();
        if (kind.parsesBody()) {
            appendMessageBody(root, packet.getMessage(), showcaseIds,
                    channel);
            ChatSpoilerMarker.mark(root.getSiblings(), bodyStart,
                    packet.getMessageId());
        } else if (kind == ChatBodyKind.ANSWER) {
            // The server's own component, exactly as it came: its
            // colours, its links and its hover text all stand. Built
            // again without one, the words the packet kept stand in.
            root.appendSibling(body != null ? body
                    : text(packet.getMessage(), null, false));
        } else {
            // A command was not said: its slash stands where the chevron
            // stands, in the sender's colour, with the chevron's own gap
            // after it, and the rest follows in the chat's white. The
            // slash and the command are the body's own text, so a copy
            // reads the command whole; the gap is a spacer, which draws
            // nothing and adds nothing to a copy.
            String command = packet.getMessage();
            if (command.startsWith("/")) {
                root.appendSibling(ChatColorMarker.apply(text("/",
                        nearestFormatting(packet.getNameColor()), false),
                        packet.getNameColor()));
                root.appendSibling(ChatSpacerMarker.of(COMMAND_GAP));
                command = command.substring(1);
            }
            root.appendSibling(text(command, null, false));
        }
    }

    /**
     * The gap between a command echo's slash and the command: the width
     * of the space the chevron carries after itself, so the two openers
     * hold their bodies the same distance off.
     */
    static final int COMMAND_GAP = 4;

    /**
     * The words a kind opens the body with, and the one space that
     * stands between them and the body — the same space the chevron
     * carries; empty for the chevron. The space is added here rather
     * than kept in the lang file, where a trailing space is easily
     * lost.
     */
    private static String bodyLabel(ChatBodyKind kind) {
        if (kind.getLabelKey().length() == 0) {
            return "";
        }
        return ChatEpithet.translate(kind.getLabelKey(), "").trim() + " ";
    }

    /**
     * The row a reply opens with: the message it answers, quoted the
     * way that message was said — the chat's speech bubble in the
     * timestamps' quiet tone, then {@code <HEAD Name>} in the name's own
     * colour and the
     * words in the chat's ivory, so the quote reads as the line it
     * quotes rather than as a line about it. The head is the quoted
     * sender's: from the quote when it was told one — the server's cut
     * of a line it named, or this client's own of a line on its screen,
     * an NPC's portrait included — else from the line itself when this
     * client still holds it; a quote told neither names its author
     * bare. A quote of the Server wears its name in the aside tone, as
     * the Server's own lines do. Every run is the quote's — the
     * head slot included — so the whole of it answers one click and
     * lights as one. The wrapper cuts the row to one line, so a long
     * quote never pushes the answer down the window.
     */
    private static void appendReplyQuote(ChatComponentText root,
                                         ChatReplyReference reply) {
        int quiet = LostTalesColors.rgb(LostTalesColors.ROSE_BEIGE);
        int ivory = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        long id = reply.getMessageId();
        // The line as this client holds it, when it does: its head and
        // its colour are the quoted line's own, exactly as drawn.
        ClientChatMessages.Remembered held = id == ChatMessageIds.NONE
                ? null : ClientChatMessages.get(id);
        LostTalesChatMessagePacket quoted = held == null ? null
                : held.packet;
        UUID senderId = reply.hasHead() ? reply.getSenderId()
                : quoted == null ? null : quoted.getSenderId();
        boolean accountLine = reply.hasHead() ? reply.isAccountLine()
                : quoted != null && quoted.isAccountLine();
        boolean npcLine = reply.hasHead() && reply.isNpcLine();
        String skinId = reply.hasHead() ? reply.getSkinId()
                : quoted == null ? "" : quoted.getSkinId();
        // The colour the name was drawn in: the line's own when held,
        // else the one the server drew it in, which is the only answer
        // for an in-character author — a character's name belongs to a
        // character, and the account-role colours this client holds
        // are keyed by account name. Those still answer for a quote
        // from before it carried a colour; one with neither is quiet.
        int name = quoted != null ? quoted.getNameColor()
                : reply.getAuthorColor();
        if (senderId != null
                && LostTalesChatMessagePacket.isSystemSender(senderId)) {
            // The Server looks the same wherever and whenever it speaks,
            // quoted too: its name in this client's aside tone.
            name = LostTalesChatVisualStyle.asideRgb();
        }
        if (name < 0) {
            name = ClientChatAccountRoles.colorOf(reply.getAuthor());
        }
        if (name < 0) {
            name = quiet;
        }
        root.appendSibling(ChatReplyMarker.applyIcon(
                text("", null, false), quiet, id));
        root.appendSibling(ChatReplyMarker.apply(
                text("<", nearestFormatting(name), false), name, id));
        if (senderId != null) {
            root.appendSibling(ChatReplyMarker.applyHead(
                    text("  ", EnumChatFormatting.WHITE, true), name, id,
                    senderId, accountLine, npcLine, skinId));
        }
        root.appendSibling(ChatReplyMarker.apply(
                text(reply.getAuthor(), nearestFormatting(name), false),
                name, id));
        root.appendSibling(ChatSpacerMarker.of(ChatInlineIcons.NAME_GAP - 1));
        root.appendSibling(ChatReplyMarker.apply(
                text("> ", nearestFormatting(name), false), name, id));
        root.appendSibling(ChatReplyMarker.apply(
                text(reply.getExcerpt(), nearestFormatting(ivory), false),
                ivory, id));
    }

    /**
     * A component that answers to the pointer as the sender's name
     * does: the same whisper on a click, and so the same card on a
     * hover. Its colour comes from the line's head marker, which is
     * where every part of a sender's name takes it.
     */
    private static ChatComponentText reply(ChatComponentText part,
                                           String whisper) {
        part.setChatStyle(part.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, whisper)));
        return part;
    }

    /**
     * Prints a vanilla or third-party line that Lost Tales classified
     * into a channel — an achievement, a death message, command output,
     * a fast-travel countdown — with the channel prefix and timestamp
     * every other line carries and a tracked line id, so the feed names
     * its channel and the tabs can file it. The component itself is the
     * server's, untouched; no head, no mention check.
     *
     * <p>{@code audibleMentionCue} says whether a mention of this player
     * inside the line may sound as well as highlight: an announcement —
     * an achievement above all — names its player without addressing
     * them, so its mention stays visual. The highlight, the unread ping
     * count and the line's tint are the same either way.</p>
     */
    public static boolean receiveSystemLine(IChatComponent message,
                                            ChatChannel channel,
                                            boolean audibleMentionCue) {
        return receiveSystemLine(message, channel, audibleMentionCue,
                System.currentTimeMillis());
    }

    /** As above, stamped with the time the line was said rather than shown. */
    public static boolean receiveSystemLine(IChatComponent message,
                                            ChatChannel channel,
                                            boolean audibleMentionCue,
                                            long timestampMillis) {
        return receiveSystemLine(message, channel, audibleMentionCue,
                timestampMillis, ChatMessageIds.NONE);
    }

    /**
     * As above, under the id the server named the line by, or
     * {@link ChatMessageIds#NONE} for a line it never named, which is
     * named by this client alone.
     */
    public static boolean receiveSystemLine(IChatComponent message,
                                            ChatChannel channel,
                                            boolean audibleMentionCue,
                                            long timestampMillis,
                                            long messageId) {
        return receiveSystemLine(message, channel, audibleMentionCue,
                timestampMillis, true, messageId);
    }

    /**
     * The component a server line carries, read back from the JSON the
     * server kept it as, with every player it names rewritten to the
     * identity the server recorded for them — as the live line's names
     * are rewritten from the appearances this client holds — or null
     * when the JSON cannot be read, in which case the words the packet
     * kept stand in. {@code localMentioned[0]} says whether one of the
     * names was this player's own.
     */
    private static IChatComponent serverBody(Minecraft minecraft,
                                             LostTalesChatMessagePacket packet,
                                             boolean[] localMentioned) {
        IChatComponent component;
        try {
            component = IChatComponent.Serializer.func_150699_a(
                    packet.getBodyJson());
        } catch (RuntimeException unreadable) {
            return null;
        }
        if (component == null || !LostTalesConfig.enableChatPings) {
            return component;
        }
        return rewritePlayerNames(component, packet.getChannel(),
                localMentionNames(minecraft), localMentioned,
                packet.getNamedPlayers());
    }

    /**
     * The id the server stamped on a broadcast line, taken off it —
     * the id run is the server's word to this client, not a part of
     * the line — or {@link ChatMessageIds#NONE} for a line without one.
     */
    public static long takeBroadcastId(IChatComponent message) {
        if (message == null) {
            return ChatMessageIds.NONE;
        }
        List<?> siblings = message.getSiblings();
        for (int index = 0; siblings != null && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent sibling = (IChatComponent)value;
            ClickEvent click = sibling.getChatStyle() == null ? null
                    : sibling.getChatStyle().getChatClickEvent();
            if (click == null || click.getAction() != ClickEvent.Action.SUGGEST_COMMAND) {
                continue;
            }
            long id = ChatBroadcastIdMarkers.decode(click.getValue());
            if (id != ChatMessageIds.NONE) {
                siblings.remove(index);
                return id;
            }
        }
        return ChatMessageIds.NONE;
    }

    /**
     * As above; {@code mayAnswerACommand} says whether a console line
     * arriving while a command's answer is expected is taken as that
     * answer. An entry of the operator console never is: it is about
     * a command, not the command's reply.
     */
    private static boolean receiveSystemLine(IChatComponent message,
                                             ChatChannel channel,
                                             boolean audibleMentionCue,
                                             long timestampMillis,
                                             boolean mayAnswerACommand,
                                             long messageId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (message == null || channel == null || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        // A system line reopens its closed channel exactly as a player
        // message does — an achievement brings Global back, a command's
        // answer the console — unless the channel is hidden.
        ChatTab tab = ChatTab.of(channel);
        // A console line arriving while a command's answer is expected
        // is that answer: a line of the Server's own, shown where the
        // command was typed and nowhere else.
        ChatTab asked = mayAnswerACommand && channel == ChatChannel.CONSOLE
                ? commandOutputTab() : null;
        if (asked == null && !ChatWindowLayout.isOpen(tab)
                && !ChatWindowLayout.isHidden(tab)) {
            ChatWindowLayout.openTab(tab, windowIdOfSelection());
        }
        // A system line naming a player — an achievement, a death, a
        // join — names them the way a typed mention does: @Name in the
        // mention's colour, the active character's name on the
        // character channels, answering to the pointer. A line naming
        // this player highlights, and the cue sounds. The rewrite may
        // hand back a fresh component; the fresh one is what is shown.
        IChatComponent shown = message;
        boolean mentioned = false;
        if (LostTalesConfig.enableChatPings) {
            boolean[] localMentioned = new boolean[1];
            shown = rewritePlayerNames(message, channel,
                    localMentionNames(minecraft), localMentioned,
                    Collections.<ChatNamedPlayer>emptyList());
            mentioned = localMentioned[0];
        }
        long now = timestampMillis;
        // Whatever the server says is said by the Server: an
        // achievement, a death, a join, a notice, a command's answer.
        // A command's first answer quotes the command it answers, the
        // way a bot's reply on Discord names the command; the answers
        // behind it join its run.
        ChatReplyReference reply = ChatReplyReference.NONE;
        if (asked != null) {
            tab = asked;
            reply = commandEchoQuote();
        }
        int chatLineId = printServerLine(minecraft, tab, shown, mentioned,
                now, reply, messageId);
        if (asked != null && lastCommandEcho != null) {
            rememberAnswer(chatLineId, lastCommandEcho.getMessage());
        }
        if (mentioned) {
            markPinged(chatLineId);
            if (audibleMentionCue && ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        return true;
    }

    /**
     * Shows one entry of the shared operator console, once. A command
     * is a line of the Server's own — {@code @Player used /command in
     * #Channel}, the channel a link to the tab the command was typed
     * in, landing on the command itself when this client is the one
     * that typed it. Every other kind is a bracketed label saying what
     * kind of thing it is, in the kind's colour, then who did it and
     * what. Filed in the Console tab like every console line, stamped
     * with when it happened, and never a cue: the console is read, not
     * answered. The server sent it only because this player may read
     * the console; nothing here decides that.
     */
    public static void receiveConsoleEvent(ChatConsoleEvent event) {
        receiveConsoleEvent(event, false, false);
    }

    /**
     * As above for an entry the server {@code replayed}: one of the kept
     * entries a player is sent on joining. It sounds no cue even where
     * it names them, and it is filed against where this player last read
     * the Console on this server — one they had read is filed and
     * nothing more, and the first they had not stands under the unread
     * divider. One that happened {@code beforeArrival}, before this
     * player arrived, is history: it stands in the Console, never in the
     * closed feed.
     */
    public static void receiveConsoleEvent(ChatConsoleEvent event,
                                           boolean replayed,
                                           boolean beforeArrival) {
        if (event == null || !ClientChatConsoleEvents.noteShown(event.getId())) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.ingameGUI == null) {
            return;
        }
        ChatTab console = ChatTab.of(ChatChannel.CONSOLE);
        boolean[] mentioned = new boolean[1];
        IChatComponent body;
        if (event.getKind() == ChatConsoleEvent.Kind.COMMAND) {
            body = commandNotice(minecraft, event, mentioned);
        } else {
            // What happened, said plainly: who did it as a mention, then
            // the words; a warning in red. The server's own doings name
            // no actor, since the line is the Server's already.
            ChatComponentText line = new ChatComponentText("");
            String actor = event.getActor();
            if (actor.length() > 0 && !actor.equalsIgnoreCase(
                    StatCollector.translateToLocal("chat.losttales.server.name"))
                    && !"Server".equalsIgnoreCase(actor)) {
                IChatComponent mention = asMentionName(actor,
                        ChatChannel.CONSOLE, localMentionNames(minecraft),
                        mentioned, Collections.<ChatNamedPlayer>emptyList());
                line.appendSibling(mention != null ? mention
                        : text(actor, EnumChatFormatting.WHITE, false));
                line.appendSibling(text(" ", null, false));
            }
            line.appendSibling(text(event.getText(),
                    event.getSeverity() == ChatConsoleEvent.Severity.WARNING
                            ? EnumChatFormatting.RED : null, false));
            body = line;
        }
        int chatLineId;
        receivingReplayed = replayed;
        receivingBeforeArrival = replayed && beforeArrival;
        receivingServerId = event.getId();
        try {
            chatLineId = printServerLine(minecraft, console, body,
                    mentioned[0], event.getTimestampMillis(),
                    ChatReplyReference.NONE, ChatMessageIds.NONE);
        } finally {
            receivingReplayed = false;
            receivingBeforeArrival = false;
            receivingServerId = ChatMessageIds.NONE;
        }
        if (mentioned[0]) {
            markPinged(chatLineId);
            if (!replayed && ChatWindowLayout.isPingAudible(console)) {
                playPingSound(minecraft);
            }
        }
    }

    /**
     * The words of a command entry: who, the command, and where — the
     * actor as a mention when the client can place the account, this
     * player's own name a ping like any other mention, the command as
     * it was described, the tab as a link in its own colour. A command
     * run from the server's own console names no tab.
     */
    private static IChatComponent commandNotice(Minecraft minecraft,
                                                ChatConsoleEvent event,
                                                boolean[] mentioned) {
        IChatComponent actor = asMentionName(event.getActor(),
                ChatChannel.CONSOLE, localMentionNames(minecraft),
                mentioned, Collections.<ChatNamedPlayer>emptyList());
        if (actor == null) {
            actor = text(event.getActor(), null, false);
        }
        IChatComponent command = text(event.getText(), null, false);
        ChatTab typedIn = ChatTab.fromId(event.getContext());
        if (typedIn == null) {
            return sentence("chat.losttales.console.command.used.nowhere",
                    actor, command);
        }
        int color = ClientChatChannelState.displayColor(typedIn);
        ChatComponentText link = new ChatComponentText("");
        appendChannelLink(link,
                "#" + ClientChatChannelState.displayName(typedIn), color,
                event.getContext(),
                commandEchoLineFor(minecraft, event, typedIn),
                ChatMessageIds.NONE);
        return sentence("chat.losttales.console.command.used", actor,
                command, link);
    }

    /**
     * A translated pattern with its {@code %s} placeholders filled by
     * components, in order, so each piece keeps its own colour and
     * marker where a formatted string would flatten them.
     */
    private static IChatComponent sentence(String key,
                                           IChatComponent... parts) {
        String pattern = StatCollector.translateToLocal(key);
        ChatComponentText root = new ChatComponentText("");
        int from = 0;
        int next = 0;
        while (next < parts.length) {
            int at = pattern.indexOf("%s", from);
            if (at < 0) {
                break;
            }
            if (at > from) {
                root.appendSibling(text(pattern.substring(from, at), null,
                        false));
            }
            root.appendSibling(parts[next++]);
            from = at + 2;
        }
        if (from < pattern.length()) {
            root.appendSibling(text(pattern.substring(from), null, false));
        }
        return root;
    }

    /**
     * How long after a command was echoed the console's entry about it
     * may still link to the echo: the entry follows at once, and the
     * margin is for a server under load.
     */
    private static final long COMMAND_ECHO_LINK_WINDOW_MILLIS = 10000L;
    private static ChatTab lastCommandEchoTab;
    private static int lastCommandEchoLineId;
    private static long lastCommandEchoMillis;
    /** The echo as it was signed, for the quote its first answer opens with. */
    private static LostTalesChatMessagePacket lastCommandEcho;

    /**
     * The line this client echoed the command of a console entry on,
     * for the entry's link to land on: the entry names this player and
     * the tab the last command was echoed in, and follows it closely.
     * Zero for anyone else's command, or one echoed too long ago.
     */
    private static int commandEchoLineFor(Minecraft minecraft,
                                          ChatConsoleEvent event,
                                          ChatTab typedIn) {
        if (minecraft.thePlayer == null || lastCommandEchoTab == null
                || !lastCommandEchoTab.equals(typedIn)
                || !event.getActor().equalsIgnoreCase(
                        minecraft.thePlayer.getCommandSenderName())
                || System.currentTimeMillis() - lastCommandEchoMillis
                        > COMMAND_ECHO_LINK_WINDOW_MILLIS) {
            return 0;
        }
        return lastCommandEchoLineId;
    }

    /**
     * Prints a line of the Server's own — a command's answer, the
     * console's word on who ran what — into one tab, signed the way a
     * message is: the Server identity with the console mark for a head,
     * and the component exactly as it came for a body. This client's
     * own work under a local id, so it can be answered and jumped to
     * and never edited or sent. The tab reopens for it unless it is
     * hidden.
     */
    private static int printServerLine(Minecraft minecraft, ChatTab tab,
                                       IChatComponent body, boolean mentioned,
                                       long timestampMillis,
                                       ChatReplyReference reply,
                                       long messageId) {
        if (!ChatWindowLayout.isOpen(tab) && !ChatWindowLayout.isHidden(tab)) {
            ChatWindowLayout.openTab(tab, windowIdOfSelection());
        }
        return print(minecraft, serverPacket(tab, body, timestampMillis, reply,
                messageId), tab, mentioned, ChatBodyKind.ANSWER, body);
    }

    /**
     * A line signed by the server itself: the Server identity, no roles
     * and no title, its name in the Console's own colour. The packet's
     * message is what a copy or a quote reads — the body's words,
     * bounded and cleaned like any message — while what is drawn is
     * the component.
     */
    private static LostTalesChatMessagePacket serverPacket(
            ChatTab tab, IChatComponent body, long timestampMillis,
            ChatReplyReference reply, long messageId) {
        return systemPacket(LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "chat.losttales.server.name", tab, body, timestampMillis,
                reply, messageId);
    }

    /**
     * A line the client signs for itself: what the game printed with no
     * server saying it, under the Client identity, drawn exactly as the
     * Server's lines are.
     */
    static LostTalesChatMessagePacket clientPacket(
            ChatTab tab, IChatComponent body, long timestampMillis,
            ChatReplyReference reply) {
        return systemPacket(LostTalesChatMessagePacket.CLIENT_SENDER_ID,
                "chat.losttales.client.name", tab, body, timestampMillis,
                reply, ChatMessageIds.NONE);
    }

    private static LostTalesChatMessagePacket systemPacket(
            UUID senderId, String nameKey, ChatTab tab, IChatComponent body,
            long timestampMillis, ChatReplyReference reply, long messageId) {
        String name = StatCollector.translateToLocal(nameKey);
        return new LostTalesChatMessagePacket(tab.getChannel(),
                senderId, name, name, "",
                LostTalesColors.rgb(LostTalesColors.HUD_LABEL),
                LostTalesChatVisualStyle.asideRgb(),
                copyTextOf(body), timestampMillis, "", null, "",
                tab.isWhisper() ? tab.getPartner() : "", 0, true,
                // The server's own id when it named the line, so every
                // client answers the same message; this client's else.
                ChatMessageIds.isServerId(messageId) ? messageId
                        : ClientChatMessageIds.nextLocal(),
                reply == null ? ChatReplyReference.NONE : reply,
                tab.isWhisper() && !tab.isNpc()
                        ? tab.getPartnerIdentity() : "");
    }

    /**
     * A component's words as a message may carry them: formatting
     * codes and characters the chat refuses dropped, cut to a message's
     * length, and never empty.
     */
    static String copyTextOf(IChatComponent body) {
        String text = ChatMessageValidator.cleaned(
                body == null ? "" : body.getUnformattedText());
        return text.length() == 0 ? "-" : text;
    }

    /**
     * The line a quote of words alone points at, among the rows of one
     * view: the newest line older than the reply that says what the
     * quote says under the name the quote gives — the author as a reply
     * would name it, the words cut as a quote cuts them. Null when no
     * row of the view does. The rows are newest first, so older lines
     * lie past the reply's own.
     */
    static Integer quotedLineByWords(List<ChatLine> lines, int replyIndex) {
        if (lines == null || replyIndex < 0 || replyIndex >= lines.size()
                || lines.get(replyIndex) == null) {
            return null;
        }
        int replyLineId = lines.get(replyIndex).getChatLineID();
        ChatReplyReference quote = quoteOf(replyLineId,
                lines.get(replyIndex).func_151461_a());
        if (quote == null || !quote.exists()) {
            return null;
        }
        int seen = replyLineId;
        for (int index = replyIndex + 1; index < lines.size(); index++) {
            ChatLine line = lines.get(index);
            if (line == null || line.getChatLineID() == 0
                    || line.getChatLineID() == seen) {
                continue;
            }
            seen = line.getChatLineID();
            String words = LostTalesChatClipboard.messageTextOf(lines, index);
            if (!ChatReplyReference.excerptOf(words).equals(quote.getExcerpt())) {
                continue;
            }
            if (quoteAuthorFor(authorOfLine(lines, index, seen))
                    .equals(quote.getAuthor())) {
                return Integer.valueOf(seen);
            }
        }
        return null;
    }

    /**
     * The quote a reply's row opens with: from the message the row was
     * built of, else read off the drawn quote runs — the mark, the
     * author, then the words behind a colon.
     */
    private static ChatReplyReference quoteOf(int replyLineId,
                                              IChatComponent replyRow) {
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(
                ClientChatMessageIds.messageIdOf(replyLineId));
        if (remembered != null && remembered.packet.getReply().exists()) {
            return remembered.packet.getReply();
        }
        // The quote reads bubble, {@code <}, head, name, {@code >},
        // words: the name is the first run inside the brackets, the
        // words the run after the closing one.
        String author = null;
        String words = "";
        boolean closed = false;
        for (Object value : replyRow) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (!ChatReplyMarker.isMarker(part)
                    || ChatReplyMarker.isIconSlot(part)
                    || ChatReplyMarker.headOf(part) != null) {
                continue;
            }
            String run = part.getUnformattedTextForChat();
            if (author == null) {
                if (!run.equals("<")) {
                    author = run;
                }
                continue;
            }
            if (!closed) {
                closed = run.startsWith(">");
                continue;
            }
            words = run;
            break;
        }
        return author == null ? null : ChatReplyReference.unanchored(author,
                words, ChatReplyReference.NO_COLOR);
    }

    /**
     * Who signed the line on a row: the identity of the message it was
     * built of, else the name drawn after its head, else the account
     * its whisper names; empty for a line nobody signed.
     */
    private static String authorOfLine(List<ChatLine> lines, int index,
                                       int chatLineId) {
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(
                ClientChatMessageIds.messageIdOf(chatLineId));
        if (remembered != null) {
            return remembered.packet.getIdentityName();
        }
        String identity = ChatScreenMenus.messageIdentity(lines, index,
                chatLineId);
        return identity.length() > 0 ? identity
                : ChatScreenMenus.messageAccount(lines, index, chatLineId);
    }

    /**
     * How long a jump waits for the tab it moved to to be drawn before
     * it is given up: a tab is drawn on the very next frame, and a
     * jump that has not landed by then never will.
     */
    private static final long PENDING_JUMP_NANOS = 2000L * 1000000L;
    private static int pendingJumpChatLineId;
    private static long pendingJumpNanos;

    /**
     * Asks the next draw to land on a line: what a jump to a message in
     * another tab does after bringing that tab forward, since the tab's
     * rows exist only once it has been drawn.
     */
    static void requestJump(int chatLineId) {
        pendingJumpChatLineId = chatLineId;
        pendingJumpNanos = System.nanoTime();
    }

    /** The line a jump is waiting to land on, or zero. */
    static int pendingJump() {
        if (pendingJumpChatLineId != 0
                && System.nanoTime() - pendingJumpNanos > PENDING_JUMP_NANOS) {
            pendingJumpChatLineId = 0;
        }
        return pendingJumpChatLineId;
    }

    static void clearPendingJump() {
        pendingJumpChatLineId = 0;
    }

    /**
     * How long after a command was sent the console lines that arrive
     * are taken as its answer. Commands answer at once; the margin is
     * for a server under load, and is short enough that an unrelated
     * notice is rarely mistaken for an answer.
     */
    private static final long COMMAND_OUTPUT_WINDOW_MILLIS = 3000L;
    private static ChatTab commandTab;
    private static long commandUntilMillis;

    /**
     * Says a server command has just gone out from {@code tab}: console
     * lines arriving within the window are its answer, shown there as
     * lines of the Server's own.
     */
    public static void expectCommandOutput(ChatTab tab) {
        commandTab = tab;
        commandUntilMillis = System.currentTimeMillis() + COMMAND_OUTPUT_WINDOW_MILLIS;
        commandAnswered = false;
    }

    /** Whether the command last sent has had its first answer shown. */
    private static boolean commandAnswered;
    /** The command each of the Server's answer lines was the answer to. */
    private static final int MAX_REMEMBERED_ANSWERS = 256;
    private static final Map<Integer, String> ANSWERED_COMMANDS =
            new java.util.LinkedHashMap<Integer, String>();

    private static void rememberAnswer(int chatLineId, String command) {
        ANSWERED_COMMANDS.put(Integer.valueOf(chatLineId), command);
        while (ANSWERED_COMMANDS.size() > MAX_REMEMBERED_ANSWERS) {
            Iterator<Integer> oldest = ANSWERED_COMMANDS.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /**
     * The command the Server line on {@code chatLineId} answered, for
     * its card; empty for a Server line that answers no command.
     */
    static String commandAnsweredOn(int chatLineId) {
        String command = ANSWERED_COMMANDS.get(Integer.valueOf(chatLineId));
        return command == null ? "" : command;
    }

    /**
     * The quote a command's first answer opens with: the command as its
     * echo was signed — the name in its colour and the head it wore —
     * by this client's own id for it, so the quote jumps to the command.
     * The answers after the first join its run and quote nothing; a
     * command echoed nowhere is quoted by nothing.
     */
    private static ChatReplyReference commandEchoQuote() {
        if (commandAnswered) {
            return ChatReplyReference.NONE;
        }
        commandAnswered = true;
        if (lastCommandEcho == null || lastCommandEchoLineId == 0) {
            return ChatReplyReference.NONE;
        }
        return ChatReplyReference.of(lastCommandEcho.getMessageId(),
                lastCommandEcho.getIdentityName(),
                lastCommandEcho.getMessage(), lastCommandEcho.getNameColor())
                .withHead(lastCommandEcho.getSenderId(),
                        lastCommandEcho.isAccountLine(),
                        lastCommandEcho.getSkinId());
    }

    private static ChatTab commandOutputTab() {
        if (commandTab == null || System.currentTimeMillis() > commandUntilMillis) {
            commandTab = null;
            return null;
        }
        return commandTab;
    }

    /**
     * Shows the command itself where it was typed, as a line of the
     * player's own — signed by the identity the tab speaks as, exactly
     * as a message typed there would be — whose body opens with the
     * words that say it was a command. Nowhere else: the console is
     * told who ran what by the server. Local only: the server never
     * echoes a command, so the line carries a local id — a reply can
     * quote it, nothing can edit or delete it — and it is never
     * pending. It joins the identity's run like any other line of
     * theirs, and is remembered so the console's entry about it can
     * link back to it. The tab reopens for it unless it is hidden.
     */
    public static void echoCommand(ChatTab tab, String command) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || command == null || command.length() == 0
                || minecraft == null || minecraft.ingameGUI == null
                || minecraft.thePlayer == null) {
            return;
        }
        if (!ChatWindowLayout.isOpen(tab) && !ChatWindowLayout.isHidden(tab)) {
            ChatWindowLayout.openTab(tab, windowIdOfSelection());
        }
        long now = System.currentTimeMillis();
        LostTalesChatMessagePacket packet = signedPacket(minecraft, tab,
                command, null, ChatReplyReference.NONE,
                ClientChatMessageIds.nextLocal(), now);
        lastCommandEchoLineId = print(minecraft, packet, tab, false,
                ChatBodyKind.COMMAND);
        lastCommandEcho = packet;
        lastCommandEchoTab = tab;
        lastCommandEchoMillis = now;
    }

    /**
     * Adopts a line printed straight into the chat without passing
     * through the received-chat event — a game-mode change notice, a
     * saved-screenshot line, another mod's local print. The entry is
     * rebuilt in place as a console system line, so it carries the
     * channel prefix, the timestamp and a tracked line id exactly like
     * everything else the console shows; the caller lays the history
     * out again once it has adopted what it found. Only a plain print
     * (chat line id zero) is adopted: a line printed under an id of its
     * own may be replaced or deleted by that id later and must keep it.
     */
    static boolean adoptStrayLine(List<ChatLine> messages, int index) {
        if (messages == null || index < 0 || index >= messages.size()) {
            return false;
        }
        ChatLine line = messages.get(index);
        if (line == null || line.getChatLineID() != 0) {
            return false;
        }
        int chatLineId = allocateChatLineId();
        long now = System.currentTimeMillis();
        // A line the client printed for itself is the Client's: no
        // server said it. While a command's answer is expected — a game
        // mode change, say — it is that answer, and stands where the
        // command was typed quoting it; otherwise it is filed in the
        // Console. Rebuilt in place, so it keeps its turn in the history.
        ChatTab asked = commandOutputTab();
        ChatTab tab = asked != null ? asked : ChatTab.of(ChatChannel.CONSOLE);
        IChatComponent body = line.func_151461_a();
        LostTalesChatMessagePacket packet = clientPacket(tab, body, now,
                asked != null ? commandEchoQuote() : ChatReplyReference.NONE);
        messages.set(index, new ChatLine(line.getUpdatedCounter(),
                build(packet, tab, NO_SHOWCASES, false, ChatBodyKind.ANSWER,
                        body), chatLineId));
        rememberPrinted(chatLineId, packet, tab, NO_SHOWCASES,
                ChatBodyKind.ANSWER, body.createCopy(), false);
        if (asked != null && lastCommandEcho != null) {
            rememberAnswer(chatLineId, lastCommandEcho.getMessage());
        }
        if (!ChatWindowLayout.isOpen(tab) && !ChatWindowLayout.isHidden(tab)) {
            ChatWindowLayout.openTab(tab, windowIdOfSelection());
        }
        return true;
    }

    /**
     * The names a line may address this client by, the same set in every
     * channel: the account, and every character of the roster — whoever
     * this player is currently speaking as, every other name they own is
     * an alias of them, so a mention of any of their names reaches them
     * wherever it is said.
     */
    private static List<String> localMentionNames(Minecraft minecraft) {
        List<String> names = new ArrayList<String>(4);
        if (minecraft != null && minecraft.thePlayer != null) {
            names.add(minecraft.thePlayer.getCommandSenderName());
        }
        CharacterRosterSnapshot snapshot =
                ClientCharacterRosterCache.getSnapshot();
        if (snapshot != null) {
            for (CharacterSummary summary : snapshot.getCharacters()) {
                if (summary != null && summary.getName() != null
                        && summary.getName().trim().length() > 0) {
                    names.add(summary.getName().trim());
                }
            }
        }
        return names;
    }

    /**
     * Rewrites player names inside a system line into mentions: a
     * component (or a translation's bare string argument) whose whole
     * text is a name this client can place — the way an achievement, a
     * join line or a death names its player — becomes {@code @Name},
     * carrying the mention marker so it answers to the pointer exactly
     * as a typed mention does. On the character channels the account's
     * active role-playing character is named instead of the account,
     * which is the identity every ordinary line there is signed with.
     *
     * <p>Returns the component to show, which may be a fresh one: a
     * translation caches the children it renders the first time
     * anything walks it — which another handler may already have done
     * by the time this runs last — so a translation with a replaced
     * argument is rebuilt as a new instance rather than edited in
     * place. Sibling lists are walked live at render time and are
     * edited in place. {@code localMentioned[0]} is set when one of
     * this client's own names was among the replaced.</p>
     */
    private static IChatComponent rewritePlayerNames(
            IChatComponent component, ChatChannel channel,
            List<String> localNames, boolean[] localMentioned,
            List<ChatNamedPlayer> named) {
        if (component == null) {
            return null;
        }
        IChatComponent mention = asMention(component, channel, localNames,
                localMentioned, named);
        if (mention != null) {
            return mention;
        }
        List<?> siblings = component.getSiblings();
        for (int index = 0; siblings != null
                && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent sibling = (IChatComponent)value;
            IChatComponent replaced = rewritePlayerNames(sibling, channel,
                    localNames, localMentioned, named);
            if (replaced != sibling) {
                @SuppressWarnings("unchecked")
                List<Object> mutable = (List<Object>)siblings;
                mutable.set(index, replaced);
            }
        }
        if (!(component instanceof ChatComponentTranslation)) {
            return component;
        }
        ChatComponentTranslation translation =
                (ChatComponentTranslation)component;
        Object[] arguments = translation.getFormatArgs();
        if (arguments == null) {
            return component;
        }
        Object[] rewritten = new Object[arguments.length];
        boolean changed = false;
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (argument instanceof IChatComponent) {
                rewritten[index] = rewritePlayerNames(
                        (IChatComponent)argument, channel, localNames,
                        localMentioned, named);
            } else if (argument instanceof String) {
                IChatComponent mentionOf = asMentionName((String)argument,
                        channel, localNames, localMentioned, named);
                rewritten[index] = mentionOf != null ? mentionOf : argument;
            } else {
                rewritten[index] = argument;
            }
            changed |= rewritten[index] != argument;
        }
        if (!changed) {
            return component;
        }
        ChatComponentTranslation fresh = new ChatComponentTranslation(
                translation.getKey(), rewritten);
        fresh.setChatStyle(component.getChatStyle());
        // setChatStyle re-parents the siblings but not the arguments,
        // whose parent is still the fresh instance's discarded default
        // style; without this they would lose the line's inheritance.
        for (int index = 0; index < rewritten.length; index++) {
            if (rewritten[index] instanceof IChatComponent) {
                ((IChatComponent)rewritten[index]).getChatStyle()
                        .setParentStyle(fresh.getChatStyle());
            }
        }
        for (int index = 0; siblings != null
                && index < siblings.size(); index++) {
            Object value = siblings.get(index);
            if (value instanceof IChatComponent) {
                fresh.appendSibling((IChatComponent)value);
            }
        }
        return fresh;
    }

    /**
     * The mention a leaf component becomes when its whole text is a
     * placeable player name (or one of this client's own names), or
     * null. Vanilla and LOTR put the player's name in a component of
     * its own, so whole-text matching reaches exactly them without
     * splitting anybody's prose.
     */
    private static IChatComponent asMention(IChatComponent component,
                                            ChatChannel channel,
                                            List<String> localNames,
                                            boolean[] localMentioned,
                                            List<ChatNamedPlayer> named) {
        if (!(component instanceof ChatComponentText)
                || !component.getSiblings().isEmpty()) {
            return null;
        }
        return asMentionName(component.getUnformattedTextForChat(),
                channel, localNames, localMentioned, named);
    }

    /**
     * As above from a bare name, for a translation's string argument.
     * {@code named} is what the server recorded about the players a
     * replayed line names: a name this client cannot place — its
     * player gone since — is placed by that record, shown as the
     * identity they were playing in the colour that identity wore.
     */
    private static IChatComponent asMentionName(String rawText,
                                                ChatChannel channel,
                                                List<String> localNames,
                                                boolean[] localMentioned,
                                                List<ChatNamedPlayer> named) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.length() == 0) {
            return null;
        }
        boolean local = matchesAny(text, localNames);
        String account = ChatMentionColors.accountFor(text);
        ChatNamedPlayer recorded = account == null
                ? ChatNamedPlayer.find(named, text) : null;
        if (recorded != null) {
            if (local) {
                localMentioned[0] = true;
            }
            ChatComponentText piece = text("@" + recorded.getIdentityName(),
                    nearestFormatting(recorded.getNameColor()), false);
            return ChatMentionMarker.apply(piece, recorded.getNameColor(),
                    recorded.getAccount());
        }
        if (!local && account == null) {
            return null;
        }
        // Every channel signs a line with the sender's active
        // role-playing character by default; a system line naming the
        // account shows the same identity when the client knows it —
        // the character's name, in the colour the mention resolution
        // below gives every mention of that identity, so an achievement
        // names its player exactly as their lines do.
        String shown = text;
        if (account != null) {
            String characterName =
                    ChatMentionColors.characterNameFor(account);
            if (characterName != null) {
                shown = characterName;
            }
        }
        int color = ChatMentionColors.colorOf(text, channel);
        if (color < 0) {
            // One of this client's own names the public caches cannot
            // place; the shared accent stands in.
            color = LostTalesColors.rgb(LostTalesColors.HONEY);
        }
        if (local) {
            localMentioned[0] = true;
        }
        ChatComponentText piece = text("@" + shown,
                nearestFormatting(color), false);
        return account != null
                ? ChatMentionMarker.apply(piece, color, account)
                : ChatColorMarker.apply(piece, color);
    }

    private static boolean matchesAny(String text, List<String> names) {
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            if (name != null && text.equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Prints an LOTR NPC speech line styled like a player message, the
     * name in the colour the caller resolved — the NPC's faction colour,
     * like a role-playing character's — so the line, the tab and the
     * conversation read as one; the body stays the plain ivory.
     */
    public static boolean receiveNpcSpeech(ChatTab tab, UUID npcId,
                                           String npcName,
                                           String texturePath,
                                           String message,
                                           int nameColor,
                                           String factionName) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (tab == null || npcId == null || npcName == null
                || npcName.length() == 0 || message == null
                || message.length() == 0 || minecraft == null
                || minecraft.ingameGUI == null) {
            return false;
        }
        // The tab wears the same portrait the line is drawn with, and
        // is named in the same colour the NPC's own name is; the
        // faction is kept for the NPC's hover card.
        ChatChannelIcons.rememberNpcPortrait(tab, texturePath);
        ChatChannelIcons.rememberNpcFaction(npcId, factionName);
        ClientChatChannelState.rememberPartnerColor(tab,
                nameColor & 0xFFFFFF);
        if (tab.isWhisper()) {
            if (!ChatWindowLayout.isOpen(tab)
                    && ChatWindowLayout.isHidden(tab)) {
                // A hidden conversation stays closed; the speech falls
                // back to LOTR's own chat line rather than vanishing.
                return false;
            }
            if (ChatWindowLayout.openTab(tab, windowIdOfSelection())
                    == null) {
                return false;
            }
        }
        // An NPC speaking this player's name is addressing them: the
        // name reads as the mention it is, the line highlights, and the
        // cue sounds. An NPC keeps a run exactly as a player does: its
        // next line inside the window drops the repeated header.
        List<String> localNames = localMentionNames(minecraft);
        boolean mentioned = LostTalesConfig.enableChatPings
                && ChatMentions.mentionsAny(ChatMentions.mentionNames(
                        message, localNames), localNames);
        int chatLineId = allocateChatLineId();
        long now = System.currentTimeMillis();
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        chat.printChatMessageWithOptionalDeletion(
                buildNpcSpeech(tab, npcId, npcName,
                        texturePath, message, now, nameColor, false),
                chatLineId);
        ChatGroupRuns.remember(chatLineId, tab, npcId, npcName, false, now,
                true, buildNpcSpeech(tab, npcId, npcName, texturePath,
                        message, now, nameColor, true));
        // An NPC's speech is this client's own line too: nobody else
        // sees it, so it is named locally like the player's replies.
        ClientChatMessageIds.remember(chatLineId,
                ClientChatMessageIds.nextLocal());
        noteLinePrinted(chatLineId, tab, mentioned, now);
        if (mentioned) {
            markPinged(chatLineId);
            if (ChatWindowLayout.isPingAudible(tab)) {
                playPingSound(minecraft);
            }
        }
        return true;
    }

    static IChatComponent buildNpcSpeech(ChatTab tab, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis,
                                         int nameColor) {
        return buildNpcSpeech(tab, npcId, npcName, texturePath, message,
                timestampMillis, nameColor, false);
    }

    /**
     * As above; a <em>grouped</em> line continues the NPC's run and
     * drops the repeated header, exactly as a player's grouped line
     * does.
     */
    static IChatComponent buildNpcSpeech(ChatTab tab, UUID npcId,
                                         String npcName,
                                         String texturePath,
                                         String message,
                                         long timestampMillis,
                                         int nameColor, boolean grouped) {
        ChatComponentText root = new ChatComponentText("");
        if (grouped) {
            // A grouped line drops the channel prefix with the rest of
            // the header, so the feed's run names its channel once.
            appendTimestamp(root, timestampMillis);
            root.appendSibling(ChatLayoutMarker.anchor());
            root.appendSibling(ChatLayoutMarker.bodyBreak(nameColor));
            appendMessageBody(root, ChatMentions.mentionNames(message,
                            localMentionNames(Minecraft.getMinecraft())),
                    NO_SHOWCASES, ChatChannel.WHISPER);
            return root;
        }
        appendChannelPrefix(root, tab,
                ClientChatChannelState.displayColor(tab));
        appendTimestamp(root, timestampMillis);
        root.appendSibling(ChatLayoutMarker.anchor());
        nameColor &= 0xFFFFFF;
        int bodyColor = LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
        root.appendSibling(ChatColorMarker.apply(
                text("<", nearestFormatting(nameColor), false),
                nameColor));
        ChatComponentText marker = text("  ",
                EnumChatFormatting.WHITE, true);
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encodeNpc(npcId, texturePath,
                                message, bodyColor, nameColor))));
        root.appendSibling(marker);
        root.appendSibling(ChatColorMarker.apply(
                text(npcName, nearestFormatting(nameColor), false),
                nameColor));
        root.appendSibling(ChatColorMarker.apply(
                text("> ", nearestFormatting(nameColor), false),
                nameColor));
        root.appendSibling(ChatLayoutMarker.bodyBreak(nameColor));
        // The player's bare name in the speech is shown as the mention
        // it is; the head marker above keeps the words as spoken, so a
        // copy is untouched.
        appendMessageBody(root, ChatMentions.mentionNames(message,
                        localMentionNames(Minecraft.getMinecraft())),
                NO_SHOWCASES, ChatChannel.WHISPER);
        return root;
    }

    /**
     * Channel label as the receiving client names it, carried on prefix
     * markers so the renderer can drop it while the chat screen is open
     * (the tabs separate channels there) and show it in the closed HUD's
     * combined feed. Faction shows the local active character's faction
     * name, which is the sender's faction too because the server only
     * routes faction chat to members. A conversation names its
     * <em>channel</em>, not its partner: the line itself already opens
     * with the sender, so the feed reads {@code Whisper: <Name> ...}
     * instead of saying the name twice.
     */
    private static void appendChannelPrefix(ChatComponentText root,
                                            ChatTab tab,
                                            int channelColor) {
        String label = tab != null && tab.isWhisper()
                ? ChatChannel.WHISPER.getDisplayName()
                : ClientChatChannelState.displayName(tab);
        root.appendSibling(ChatPrefixMarker.channel(
                text(label, nearestFormatting(channelColor), false),
                channelColor));
        root.appendSibling(ChatPrefixMarker.channel(
                text(": ", nearestFormatting(channelColor), false),
                channelColor));
    }

    /**
     * {@code [HH:mm] } in the Console's rose grey — a quiet tone a step
     * below the sand body text — with the time itself — digits and their
     * colon — italic; the brackets stay upright. Marked as a timestamp
     * run, so the closed feed leaves it out: the feed is a glance at
     * what was just said, not a log to read times off.
     */
    private static void appendTimestamp(ChatComponentText root,
                                        long timestampMillis) {
        if (!LostTalesConfig.showChatTimestamps) {
            return;
        }
        String formatted = "[" + ChatTimestampFormatter.format(
                timestampMillis) + "] ";
        // The chat's aside tone: what is said about a line rather than
        // in it.
        int color = LostTalesChatVisualStyle.asideRgb();
        int index = 0;
        while (index < formatted.length()) {
            boolean time = isTimeCharacter(formatted.charAt(index));
            int end = index;
            while (end < formatted.length() && isTimeCharacter(
                    formatted.charAt(end)) == time) {
                end++;
            }
            ChatComponentText run = text(formatted.substring(index, end),
                    nearestFormatting(color), false);
            if (time) {
                run.getChatStyle().setItalic(Boolean.TRUE);
            }
            root.appendSibling(ChatPrefixMarker.timestamp(run, color));
            index = end;
        }
    }

    private static boolean isTimeCharacter(char character) {
        return Character.isDigit(character) || character == ':';
    }

    /**
     * Share tokens with a validated payload become an icon slot plus the
     * bracketed name; every other token stays the literal text the sender
     * typed. Emoji markers replace their shortcode in the displayed
     * component only; the head marker's copy text and the wire format keep
     * the raw message, so copying and unsupported setups degrade to plain
     * text.
     */
    private static void appendMessageBody(
            ChatComponentText root, String message, int[] showcaseIds,
            ChatChannel channel) {
        List<ChatShareTokenParser.Token> tokens = showcaseIds.length == 0
                ? Collections.<ChatShareTokenParser.Token>emptyList()
                : ChatShareTokenParser.parse(message);
        int literalStart = 0;
        for (int index = 0; index < tokens.size()
                && index < showcaseIds.length; index++) {
            ChatShareTokenParser.Token token = tokens.get(index);
            if (!hasPayload(token.kind, showcaseIds[index])) {
                continue;
            }
            if (literalStart < token.start) {
                appendStyledText(root, message.substring(
                        literalStart, token.start), channel);
            }
            appendShowcase(root, token.kind, showcaseIds[index]);
            literalStart = token.end;
        }
        if (literalStart < message.length()) {
            appendStyledText(root, message.substring(literalStart),
                    channel);
        }
    }

    private static boolean hasPayload(ChatShareKind kind, int showcaseId) {
        return kind == ChatShareKind.ITEM
                ? ClientChatShowcaseStore.getItem(showcaseId) != null
                : ClientChatShowcaseStore.getMarker(showcaseId) != null;
    }

    private static boolean appendShowcase(ChatComponentText root,
                                          ChatShareKind kind,
                                          int showcaseId) {
        if (kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(showcaseId);
            if (stack == null) {
                return false;
            }
            EnumRarity rarity = stack.getRarity();
            EnumChatFormatting rarityFormatting = rarity == null
                    || rarity.rarityColor == null
                    ? EnumChatFormatting.WHITE : rarity.rarityColor;
            int rgb = rarityRgb(rarityFormatting);
            String name = ChatShareTokenParser.plainName(
                    stack.getDisplayName());
            appendShowcaseParts(root, kind, showcaseId, name,
                    rarityFormatting, rgb);
            return true;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(showcaseId);
        if (marker == null) {
            return false;
        }
        // The brackets and the name read in the marker's text colour —
        // white becomes the chat's ivory — while the icon itself keeps
        // the marker's exact artwork colour.
        int rgb = ChatInlineIcons.markerTextRgb(marker.colorName);
        appendShowcaseParts(root, kind, showcaseId,
                ChatShareTokenParser.plainName(marker.name),
                nearestFormatting(rgb), rgb);
        return true;
    }

    private static void appendShowcaseParts(ChatComponentText root,
                                            ChatShareKind kind,
                                            int showcaseId, String name,
                                            EnumChatFormatting nearest,
                                            int rgb) {
        root.appendSibling(ChatShowcaseMarker.createText(
                kind, showcaseId, "[", nearest, rgb));
        root.appendSibling(ChatShowcaseMarker.createIcon(kind, showcaseId));
        root.appendSibling(ChatShowcaseMarker.createText(
                kind, showcaseId, " " + name + "]", nearest, rgb));
    }

    private static void appendStyledText(ChatComponentText root,
                                         String rawText,
                                         ChatChannel channel) {
        // The markup is the only styling a player's words carry: an
        // ampersand is an ampersand, and a section sign never reaches the
        // wire (ChatMessageValidator refuses it).
        String displayed = rawText;
        if (!ChatMarkdown.hasMarkup(displayed)) {
            appendEmojiRuns(root, displayed, channel);
            return;
        }
        for (ChatMarkdown.Span span : ChatMarkdown.parse(displayed)) {
            if (span.isPlain()) {
                appendEmojiRuns(root, span.getText(), channel);
                continue;
            }
            if (span.isCode()) {
                // Quoted text is shown as typed: no emoji, no link, no
                // mention, since reading it again is the one thing
                // quoting it says not to do.
                root.appendSibling(marked(text(span.getText(),
                        EnumChatFormatting.GRAY, false), span));
                continue;
            }
            // The marks ride the runs the ordinary passes produce, so a
            // mention or an emoji inside a bold span is still a mention
            // or an emoji, and bold besides.
            ChatComponentText held = new ChatComponentText("");
            appendEmojiRuns(held, span.getText(), channel);
            List<IChatComponent> parts = new ArrayList<IChatComponent>(
                    held.getSiblings());
            for (int index = 0; index < parts.size(); index++) {
                root.appendSibling(marked(parts.get(index), span));
            }
        }
    }

    /** The emoji, link and mention passes over one run of body text. */
    private static void appendEmojiRuns(ChatComponentText root,
                                        String text, ChatChannel channel) {
        if (!LostTalesConfig.enableChatEmojis) {
            appendLinksAndMentions(root, text, channel);
            return;
        }
        for (ChatEmojiParser.Segment segment
                : ChatEmojiParser.split(text)) {
            if (segment.isEmoji()) {
                root.appendSibling(ChatEmojiMarker.create(
                        segment.getEmoji()));
            } else {
                appendLinksAndMentions(root, segment.getText(), channel);
            }
        }
    }

    /**
     * Puts a span's marks on one of its runs. Vanilla's own decorations
     * carry them, so the wrapper measures and the renderer draws them
     * without knowing markup exists at all. A spoiler is drawn
     * obfuscated — Minecraft's own way of showing text that is there but
     * not to be read.
     */
    private static IChatComponent marked(IChatComponent part,
                                         ChatMarkdown.Span span) {
        ChatStyle style = part.getChatStyle();
        if (span.isBold()) {
            style.setBold(Boolean.TRUE);
        }
        if (span.isItalic()) {
            style.setItalic(Boolean.TRUE);
        }
        if (span.isStrikethrough()) {
            style.setStrikethrough(Boolean.TRUE);
        }
        if (span.isUnderlined()) {
            style.setUnderlined(Boolean.TRUE);
        }
        if (span.isSpoiler()) {
            style.setObfuscated(Boolean.TRUE);
        }
        return part;
    }

    /**
     * Splits a run of message text so every web address in it becomes a
     * clickable link — underlined, in the palette's blue, opening
     * through the same confirm dialog every other chat link opens
     * through — and hands everything around the addresses to the
     * mention pass. Only whole {@code http(s)://} words that open at a
     * word boundary count, trailing punctuation stays ordinary text,
     * and with Minecraft's own chat-links option off nothing is touched
     * at all, exactly as vanilla leaves links inert then.
     */
    private static void appendLinksAndMentions(ChatComponentText root,
                                               String text,
                                               ChatChannel channel) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.gameSettings == null
                || !minecraft.gameSettings.chatLinks
                || text.indexOf(':') < 0) {
            appendChannelsAndMentions(root, text, channel);
            return;
        }
        String lowered = text.toLowerCase(Locale.ROOT);
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int at = indexOfLink(lowered, cursor);
            if (at < 0) {
                break;
            }
            int end = at;
            while (end < text.length()
                    && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            while (end > at && isTrailingPunctuation(text.charAt(end - 1))) {
                end--;
            }
            String url = text.substring(at, end);
            int scheme = lowered.startsWith("https", at)
                    ? "https://".length() : "http://".length();
            if (url.length() <= scheme) {
                // A bare scheme is only text.
                cursor = at + 1;
                continue;
            }
            if (literalStart < at) {
                appendChannelsAndMentions(root,
                        text.substring(literalStart, at), channel);
            }
            // In the palette's blue and plain: a link is underlined
            // while the pointer rests on it, like every other run that
            // answers to a click, and not before.
            ChatComponentText link = new ChatComponentText(url);
            ChatStyle style = link.getChatStyle()
                    .setColor(EnumChatFormatting.BLUE);
            style.setChatClickEvent(new ClickEvent(
                    ClickEvent.Action.OPEN_URL, url));
            link.setChatStyle(style);
            root.appendSibling(link);
            literalStart = end;
            cursor = end;
        }
        if (literalStart < text.length()) {
            appendChannelsAndMentions(root, text.substring(literalStart),
                    channel);
        }
    }

    /**
     * The next {@code http://}/{@code https://} that opens a word, in
     * the already-lowercased text.
     */
    private static int indexOfLink(String lowered, int from) {
        int cursor = from;
        while (cursor < lowered.length()) {
            int at = lowered.indexOf("http", cursor);
            if (at < 0) {
                return -1;
            }
            boolean opensWord = at == 0
                    || Character.isWhitespace(lowered.charAt(at - 1));
            int schemeEnd = at + 4;
            if (schemeEnd < lowered.length()
                    && lowered.charAt(schemeEnd) == 's') {
                schemeEnd++;
            }
            if (opensWord && lowered.regionMatches(schemeEnd, "://", 0, 3)) {
                return at;
            }
            cursor = at + 1;
        }
        return -1;
    }

    private static boolean isTrailingPunctuation(char character) {
        return character == '.' || character == ',' || character == ';'
                || character == ':' || character == '!' || character == '?'
                || character == ')' || character == '"' || character == '\'';
    }

    /**
     * The channel pass over one run of body text: a word behind a
     * {@code #} that names a channel — {@code #ooc}, {@code #Global} —
     * is drawn as {@code #Name} in the channel's colour and links to
     * its tab; every other word goes on to the mention pass.
     */
    private static void appendChannelsAndMentions(ChatComponentText root,
                                                  String text,
                                                  ChatChannel channel) {
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int hash = text.indexOf('#', cursor);
            if (hash < 0) {
                break;
            }
            boolean opensWord = hash == 0
                    || Character.isWhitespace(text.charAt(hash - 1));
            int end = ChatChannelSuggester.wordEnd(text, hash + 1);
            ChatChannel named = opensWord && end > hash + 1
                    ? ChatChannelSuggester.resolve(text.substring(hash + 1, end))
                    : null;
            if (named == null) {
                cursor = hash + 1;
                continue;
            }
            if (literalStart < hash) {
                appendMentions(root, text.substring(literalStart, hash),
                        channel);
            }
            // A slash and digits after the name link to one message of
            // the channel, by the id the server gave it.
            int linkEnd = ChatChannelSuggester.messageIdEnd(text, end);
            long messageId = linkEnd > end
                    ? Long.parseLong(text.substring(end + 1, linkEnd))
                    : ChatMessageIds.NONE;
            appendChannelLink(root, named, 0, messageId);
            literalStart = linkEnd;
            cursor = linkEnd;
        }
        if (literalStart < text.length()) {
            appendMentions(root, text.substring(literalStart), channel);
        }
    }

    /**
     * A channel as a link, in its colour: {@code #Name} alone for the
     * tab, and for a link to one of its messages — by this client's
     * own line id, or the server's message id — {@code #Name >} and a
     * speech bubble after it, the way a messenger draws a link to a
     * message. The pieces carry one marker, so they light together and
     * any of them answers the click.
     */
    static void appendChannelLink(ChatComponentText root, ChatChannel named,
                                  int chatLineId, long messageId) {
        int color = ClientChatChannelState.displayColor(named);
        appendChannelLink(root, "#" + ClientChatChannelState.displayName(
                ChatTab.of(named)), color, ChatTab.of(named).id(),
                chatLineId, messageId);
    }

    static void appendChannelLink(ChatComponentText root, String label,
                                  int color, String tabId, int chatLineId,
                                  long messageId) {
        boolean toMessage = chatLineId != 0
                || ChatMessageIds.isServerId(messageId);
        root.appendSibling(link(text(label, nearestFormatting(color), false),
                color, tabId, chatLineId, messageId));
        if (!toMessage) {
            return;
        }
        root.appendSibling(link(text(ChatChannelLinkMarker.MESSAGE_SEPARATOR,
                nearestFormatting(color), false), color, tabId, chatLineId,
                messageId));
        ChatComponentText slot = text(ChatChannelLinkMarker.ICON_SLOT,
                nearestFormatting(color), false);
        slot.getChatStyle().setBold(Boolean.TRUE);
        root.appendSibling(link(slot, color, tabId, chatLineId, messageId));
    }

    private static ChatComponentText link(ChatComponentText run, int color,
                                          String tabId, int chatLineId,
                                          long messageId) {
        return ChatMessageIds.isServerId(messageId)
                ? ChatChannelLinkMarker.applyMessage(run, color, tabId,
                        messageId)
                : ChatChannelLinkMarker.apply(run, color, tabId, chatLineId);
    }

    /**
     * Splits a run of message text so that every {@code @name} reaching
     * somebody is a piece of its own in that somebody's colour, and the
     * words around it stay as they were typed. A name that reaches
     * nobody is left alone: it is only text with an at-sign in front.
     */
    private static void appendMentions(ChatComponentText root, String text,
                                       ChatChannel channel) {
        int literalStart = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            int at = text.indexOf('@', cursor);
            if (at < 0) {
                break;
            }
            int end = at + 1;
            while (end < text.length() && ChatMentionColors
                    .isMentionCharacter(text.charAt(end))) {
                end++;
            }
            // The at-sign must open a word, so an address never becomes
            // a mention of whoever is named after it.
            boolean opensWord = at == 0 || !ChatMentionColors
                    .isMentionCharacter(text.charAt(at - 1));
            int color = opensWord && end > at + 1
                    ? ChatMentionColors.colorOf(
                            text.substring(at + 1, end), channel)
                    : -1;
            if (color >= 0) {
                if (at > literalStart) {
                    root.appendSibling(text(
                            text.substring(literalStart, at),
                            EnumChatFormatting.WHITE, false));
                }
                // A mention carries whom it reaches, so it answers to
                // the pointer: a player mention with their card on
                // hover and the conversation on a click, a role mention
                // with the role's card naming everyone holding it.
                String name = text.substring(at + 1, end);
                String account = ChatMentionColors.accountFor(name);
                ChatAccountRole role = account == null
                        ? ChatMentionColors.roleFor(name) : null;
                ChatComponentText piece = text(text.substring(at, end),
                        nearestFormatting(color), false);
                if (account != null) {
                    root.appendSibling(ChatMentionMarker.apply(
                            piece, color, account));
                } else if (role != null) {
                    root.appendSibling(ChatMentionMarker.applyRole(
                            piece, color, role));
                } else {
                    root.appendSibling(ChatColorMarker.apply(piece, color));
                }
                literalStart = end;
            }
            cursor = Math.max(end, at + 1);
        }
        if (literalStart < text.length()) {
            root.appendSibling(text(text.substring(literalStart),
                    EnumChatFormatting.WHITE, false));
        }
    }

    /** Palette stand-ins for vanilla rarity colours. */
    static int rarityRgb(EnumChatFormatting formatting) {
        if (formatting == EnumChatFormatting.YELLOW) {
            return LostTalesColors.rgb(LostTalesColors.HONEY);
        }
        if (formatting == EnumChatFormatting.AQUA) {
            return LostTalesColors.rgb(LostTalesColors.SEAFOAM);
        }
        if (formatting == EnumChatFormatting.LIGHT_PURPLE) {
            return LostTalesColors.rgb(LostTalesColors.ORCHID);
        }
        if (formatting == EnumChatFormatting.GREEN) {
            return LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN);
        }
        if (formatting == EnumChatFormatting.RED) {
            return LostTalesColors.rgb(LostTalesColors.SALMON);
        }
        if (formatting == EnumChatFormatting.GOLD) {
            return LostTalesColors.rgb(LostTalesColors.APRICOT);
        }
        if (formatting == EnumChatFormatting.BLUE) {
            return LostTalesColors.rgb(LostTalesColors.STEEL_BLUE);
        }
        return LostTalesColors.rgb(LostTalesColors.HUD_LABEL);
    }

    private static ChatComponentText text(
            String value, EnumChatFormatting color, boolean bold) {
        ChatComponentText component = new ChatComponentText(
                value == null ? "" : value);
        ChatStyle style = component.getChatStyle().setColor(color);
        if (bold) {
            style.setBold(Boolean.TRUE);
        }
        component.setChatStyle(style);
        return component;
    }

    static EnumChatFormatting nearestFormatting(int rgb) {
        EnumChatFormatting[] colors = new EnumChatFormatting[] {
                EnumChatFormatting.DARK_BLUE,
                EnumChatFormatting.DARK_GREEN,
                EnumChatFormatting.DARK_AQUA,
                EnumChatFormatting.DARK_RED,
                EnumChatFormatting.DARK_PURPLE,
                EnumChatFormatting.GOLD,
                EnumChatFormatting.GRAY,
                EnumChatFormatting.DARK_GRAY,
                EnumChatFormatting.BLUE,
                EnumChatFormatting.GREEN,
                EnumChatFormatting.AQUA,
                EnumChatFormatting.RED,
                EnumChatFormatting.LIGHT_PURPLE,
                EnumChatFormatting.YELLOW,
                EnumChatFormatting.WHITE
        };
        int[] values = new int[] {
                0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
                0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55,
                0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
        };
        int red = rgb >> 16 & 255;
        int green = rgb >> 8 & 255;
        int blue = rgb & 255;
        long bestDistance = Long.MAX_VALUE;
        EnumChatFormatting best = EnumChatFormatting.WHITE;
        for (int index = 0; index < values.length; index++) {
            int candidate = values[index];
            long dr = red - (candidate >> 16 & 255);
            long dg = green - (candidate >> 8 & 255);
            long db = blue - (candidate & 255);
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = colors[index];
            }
        }
        return best;
    }
}
