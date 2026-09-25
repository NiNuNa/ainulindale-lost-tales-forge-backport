package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.server.ChatHistory;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesChatOlderHistoryPacket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Loads a channel's older lines as the reader reaches the top of its
 * tab, the way a messenger pages its history in. A view scrolled to its
 * oldest line asks the server for the page before that line, once; the
 * answer is laid above it, and the view may ask again from its new
 * oldest line. A view whose ask changes nothing within
 * {@link #ANSWER_NANOS} — the server had nothing older, or would not
 * say — is done for the session, so an empty channel never asks twice a
 * second. Nothing is asked once the game's own history is nearly full:
 * it trims its oldest lines on every new one, which is exactly where
 * the older lines go, so past that point a page would only be paid for
 * to be thrown away.
 */
final class ClientChatOlderHistory {
    /** How long an ask is given before it is read as "nothing older". */
    static final long ANSWER_NANOS = 2500L * 1000000L;
    /** Room the game's list must keep for a page and the lines still to come. */
    static final int CAPACITY_MARGIN = ChatHistory.MAX_OLDER_PER_REQUEST + 16;

    /** By view: the oldest line the last ask reached back from. */
    private static final Map<ChatTab, Long> ASKED_BEFORE = new HashMap<ChatTab, Long>();
    /** By view: when that ask went out. */
    private static final Map<ChatTab, Long> ASKED_AT = new HashMap<ChatTab, Long>();
    /** Views the server has nothing older for. */
    private static final Set<ChatTab> EXHAUSTED = new HashSet<ChatTab>();

    private ClientChatOlderHistory() {}

    /**
     * Asks for the page before the view's oldest line when the view is
     * scrolled to it, at most once per oldest line. Called every frame a
     * window is drawn; answers whether a request went out.
     */
    static synchronized boolean requestIfAtTop(Minecraft minecraft, ChatTab view,
                                               List<ChatLine> lines, boolean atTop) {
        if (!atTop || minecraft == null || minecraft.ingameGUI == null
                || view == null || view.isNpc() || view.isPage() || lines == null
                || lines.isEmpty()) {
            return false;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        List<ChatLine> held = ChatWindowLines.messageHistory(chat);
        if (held == null) {
            return false;
        }
        long oldest = oldestNamedIn(lines);
        Decision decision = decide(view, oldest, System.nanoTime(), held.size(),
                LostTalesChatHistoryHooks.capacity());
        if (decision != Decision.ASK) {
            return false;
        }
        // A whisper names its conversation by the tab's id — the one held
        // as the identity being read; a scoped channel by the conversation.
        String scope = view.isWhisper() ? ChatTab.viewed(view).id()
                : ClientChatContextHistory.scopeOf(ChatTab.viewed(view));
        if (!view.isWhisper()
                && view.getChannel().isScoped() == (scope.length() == 0)) {
            return false;
        }
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new LostTalesChatOlderHistoryPacket(view.getChannel(), scope,
                            oldest));
        } catch (IllegalArgumentException refused) {
            EXHAUSTED.add(key(view));
            return false;
        }
        return true;
    }

    /** What a frame at the top of a view does about its oldest line. */
    enum Decision { ASK, WAIT, DONE }

    /**
     * The rule, apart from the screen: ask once per oldest line, wait
     * on an ask still young, and stop for good once an ask has changed
     * nothing in its time or the game's list has no room left.
     */
    static synchronized Decision decide(ChatTab view, long oldestMessageId, long now,
                                        int heldLines, int capacity) {
        ChatTab key = key(view);
        if (key == null || !ChatMessageIds.isServerId(oldestMessageId)
                || EXHAUSTED.contains(key)) {
            return Decision.DONE;
        }
        if (heldLines + CAPACITY_MARGIN > capacity) {
            return Decision.DONE;
        }
        Long asked = ASKED_BEFORE.get(key);
        Long at = ASKED_AT.get(key);
        if (asked != null && at != null) {
            if (asked.longValue() == oldestMessageId) {
                if (now - at.longValue() < ANSWER_NANOS) {
                    return Decision.WAIT;
                }
                EXHAUSTED.add(key);
                return Decision.DONE;
            }
            if (asked.longValue() < oldestMessageId) {
                // The view's oldest line is now newer than what was asked
                // from: the history was cleared under it. Start over.
                ASKED_BEFORE.remove(key);
                ASKED_AT.remove(key);
            }
        }
        ASKED_BEFORE.put(key, Long.valueOf(oldestMessageId));
        ASKED_AT.put(key, Long.valueOf(now));
        return Decision.ASK;
    }

    /**
     * The line an older message goes above, or null when the message is
     * not older than everything the view holds: the chat line id of the
     * view's oldest server-named line, when that line is newer than
     * {@code messageId}. Read off the game's own unwrapped list, newest
     * first, so the answer is the list's and nothing cached.
     */
    static Integer anchorFor(Minecraft minecraft, ChatTab tab, long messageId) {
        if (minecraft == null || minecraft.ingameGUI == null || tab == null
                || !ChatMessageIds.isServerId(messageId)) {
            return null;
        }
        List<ChatLine> held = ChatWindowLines.messageHistory(
                minecraft.ingameGUI.getChatGUI());
        if (held == null) {
            return null;
        }
        ChatTab view = key(tab);
        for (int index = held.size() - 1; index >= 0; index--) {
            ChatLine line = held.get(index);
            if (line == null) {
                continue;
            }
            ChatTab filed = ClientChatChannelViews.tabOf(line.getChatLineID());
            if (filed == null || !view.equals(key(filed))) {
                continue;
            }
            long named = ClientChatMessageIds.messageIdOf(line.getChatLineID());
            if (!ChatMessageIds.isServerId(named)) {
                continue;
            }
            return named > messageId ? Integer.valueOf(line.getChatLineID()) : null;
        }
        return null;
    }

    /** The oldest server-named message among the view's lines, newest first; NONE for none. */
    static long oldestNamedIn(List<ChatLine> lines) {
        for (int index = lines.size() - 1; index >= 0; index--) {
            ChatLine line = lines.get(index);
            if (line == null || ChatWindowLines.isFiller(line)) {
                continue;
            }
            long named = ClientChatMessageIds.messageIdOf(line.getChatLineID());
            if (ChatMessageIds.isServerId(named)) {
                return named;
            }
        }
        return ChatMessageIds.NONE;
    }

    /** Whether the view's kept history has been paged to its end this session. */
    static synchronized boolean isExhausted(ChatTab view) {
        ChatTab key = key(view);
        return key == null || EXHAUSTED.contains(key);
    }

    /** Forgets every ask; what a cleared history calls. */
    static synchronized void clear() {
        ASKED_BEFORE.clear();
        ASKED_AT.clear();
        EXHAUSTED.clear();
    }

    private static ChatTab key(ChatTab tab) {
        return tab == null ? null : ChatTab.viewed(tab);
    }
}
