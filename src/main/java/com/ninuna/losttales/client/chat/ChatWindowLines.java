package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

/**
 * The lines one view shows, laid out for that view alone: every open
 * window at its own width, and the closed-chat feed.
 *
 * <p>Minecraft wraps a message once, at one width, and keeps the result
 * in the list every chat draw reads — so views of different widths
 * cannot share it. The unwrapped messages behind that list can, though:
 * this reads them, keeps the ones the view accepts, and lays each out
 * again at the view's width, in the same order vanilla files its own —
 * newest message first, and within a message its last line first,
 * because that is the order the renderer walks upward from the
 * baseline.</p>
 *
 * <p>The view also decides its own <em>grouping</em>, which is why the
 * feed is laid out here rather than reading the shared list: a run is
 * broken by the message before it <em>in this view</em>, and a channel's
 * own window and the interleaved feed do not see the same one. Vanilla's
 * history keeps every message in full; {@link ChatGroupRuns} holds the
 * grouped form beside it, and each view picks between them message by
 * message.</p>
 *
 * <p>The feed shows the channel prefix on every line; the open screen
 * hides it — the tabs already name the channel — so a window gives that
 * width back to the message body instead of leaving it blank at the end
 * of each line. A view whose history cannot be read falls back to the
 * shared list, where every message keeps its header.</p>
 *
 * <p>Results are cached per view and rebuilt only when the width, the
 * colour setting, the drawn state or the history itself changes, and a
 * rebuild costs only the messages whose layout it has not already seen:
 * each message's lines are kept against the message and the form it was
 * laid out in, so an arriving message lays out one message rather than
 * the whole history.</p>
 */
final class ChatWindowLines {
    /** Vanilla's unwrapped history, newest first. */
    private static final Field CHAT_LINES = resolveChatLines();
    private static final Map<String, Cached> CACHE =
            new HashMap<String, Cached>();
    private static boolean unavailableLogged;
    /**
     * Bumped whenever the history is changed in place rather than added
     * to. The signature below notices messages arriving and leaving by
     * counting them and reading the ends; a message rewritten where it
     * stands changes neither, so it says so itself.
     */
    private static long revision;

    private ChatWindowLines() {}

    /** Whether a window can be laid out for itself at all. */
    static boolean isAvailable() {
        return CHAT_LINES != null;
    }

    /**
     * The window's lines, laid out at {@code chatWidth} for the open
     * chat screen, or null when the unwrapped history cannot be read and
     * the window should fall back to the shared list.
     */
    static synchronized List<ChatLine> forWindow(Minecraft minecraft,
                                                 GuiNewChat chat,
                                                 ChatWindow window,
                                                 ChatLineFilter filter,
                                                 int chatWidth) {
        if (minecraft == null || minecraft.fontRenderer == null
                || chat == null || window == null || chatWidth <= 0) {
            return null;
        }
        // The timestamp column at the window's left edge comes out of
        // the room the messages may wrap to, so a line never runs out
        // under the window's right edge to pay for it.
        ChatTimestampColumn columns =
                ChatTimestampColumn.current(minecraft.fontRenderer);
        return forView(minecraft, chat, window.getId(), filter,
                ChatWindowPlacement.wrapWidth(chatWidth,
                        chat.func_146244_h()) - (columns.messageX() - 2),
                true, false);
    }

    /**
     * The closed-chat feed's lines, laid out at the game's own chat
     * width — exactly the width vanilla wraps the shared list to, since
     * the feed shows the same channel prefixes on the same messages and
     * only their grouping is its own.
     */
    static synchronized List<ChatLine> forFeed(Minecraft minecraft,
                                               GuiNewChat chat,
                                               ChatLineFilter filter) {
        if (minecraft == null || chat == null) {
            return null;
        }
        return forView(minecraft, chat, ChatWindowFrame.feed().windowId,
                filter, ChatWindowPlacement.wrapWidth(
                        ChatWindowPlacement.chatWidth(minecraft),
                        chat.func_146244_h()), false, true);
    }

    /**
     * One view's lines, or null when the unwrapped history cannot be
     * read and the view should fall back to the shared list. A
     * {@code fading} view drops its lines a few seconds after they
     * arrive, and drops a whole run at once: its runs are held to a
     * span of their own, and every line of a run is given the arrival
     * tick of the run's newest message, which is the clock the renderer
     * fades each line on.
     */
    private static List<ChatLine> forView(Minecraft minecraft,
                                          GuiNewChat chat, String viewId,
                                          ChatLineFilter filter,
                                          int wrapWidth, boolean chatOpen,
                                          boolean fading) {
        if (CHAT_LINES == null || minecraft == null
                || minecraft.fontRenderer == null || filter == null
                || viewId == null) {
            return null;
        }
        List<ChatLine> messages = messages(chat);
        if (messages == null) {
            return null;
        }
        int width = Math.max(1, wrapWidth);
        boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
        Cached cached = CACHE.get(viewId);
        if (cached == null || !cached.describes(width, colours, chatOpen,
                fading, filter)) {
            cached = new Cached(width, colours, chatOpen, fading, filter);
            CACHE.put(viewId, cached);
        }
        cached.refresh(minecraft.fontRenderer, messages, filter,
                signatureOf(messages));
        return cached.lines;
    }

    /** Forgotten with the rest of the client's chat state. */
    static synchronized void clear() {
        CACHE.clear();
    }

    /**
     * Drops the layout of every window that has gone. A view's lines are
     * held against its window's id, and once that window is closed
     * nothing asks for them again — so the entry is never refreshed and
     * never replaced either, since ids are handed out and never reused.
     * What it leaves behind is not small: a whole re-wrapped copy of the
     * history as it stood when the window closed, and, through the keys
     * of that copy, vanilla's own message objects held alive after
     * vanilla has trimmed them from its history. Every detached window
     * closed in a session left one until the world was left.
     *
     * <p>Called from the draw beside {@link ChatWindowFrame#prune}: the
     * two per-window caches answer to one question — which windows are
     * still real — and are let go together. The feed is not a window and
     * is never pruned.</p>
     */
    static synchronized void prune(List<ChatWindow> windows) {
        // Nothing to sweep while the cache holds no more than the feed
        // and one entry per window. A window drawn before its first
        // layout makes this miss by one, which only puts the sweep off
        // to the next frame; it can never drop a view still in use.
        if (windows != null && CACHE.size() > windows.size() + 1) {
            pruneViews(CACHE, windows);
        }
    }

    /**
     * The sweep itself, over any map of views by id: everything whose id
     * is neither the feed's nor a live window's goes. Separate from the
     * cache it is run over so it can be exercised without a screen.
     */
    static void pruneViews(Map<String, ?> views, List<ChatWindow> windows) {
        String feedId = ChatWindowFrame.feed().windowId;
        Iterator<String> iterator = views.keySet().iterator();
        while (iterator.hasNext()) {
            String viewId = iterator.next();
            if (viewId == null || viewId.equals(feedId)) {
                continue;
            }
            boolean alive = false;
            for (int index = 0; windows != null && index < windows.size();
                 index++) {
                if (windows.get(index).getId().equals(viewId)) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                iterator.remove();
            }
        }
    }

    /** How many views the cache is holding; for tests and diagnostics. */
    static synchronized int cachedViewCount() {
        return CACHE.size();
    }

    /**
     * Rewrites the message drawn under {@code chatLineId} where it
     * stands, so an edited line stays in the order it was said in
     * rather than jumping to the end of the conversation — which is
     * what printing it again would do, since vanilla files every
     * printed line as the newest.
     *
     * <p>Answers whether the history could be reached at all. It cannot
     * only when the same reflection every window's own width depends on
     * has already failed, and the chat has said so once.</p>
     */
    static boolean replaceMessage(GuiNewChat chat, int chatLineId,
                                  IChatComponent replacement) {
        List<ChatLine> messages = chat == null || replacement == null ? null
                : messages(chat);
        if (messages == null) {
            return false;
        }
        for (int index = 0; index < messages.size(); index++) {
            ChatLine line = messages.get(index);
            if (line != null && line.getChatLineID() == chatLineId) {
                messages.set(index, new ChatLine(line.getUpdatedCounter(),
                        replacement, chatLineId));
                revision++;
                return true;
            }
        }
        return false;
    }

    /** Drops the message drawn under {@code chatLineId} from the history. */
    static boolean removeMessage(GuiNewChat chat, int chatLineId) {
        List<ChatLine> messages = chat == null ? null : messages(chat);
        if (messages == null) {
            return false;
        }
        boolean removed = false;
        for (int index = messages.size() - 1; index >= 0; index--) {
            ChatLine line = messages.get(index);
            if (line != null && line.getChatLineID() == chatLineId) {
                messages.remove(index);
                removed = true;
            }
        }
        if (removed) {
            revision++;
        }
        return removed;
    }

    /**
     * Vanilla's unwrapped message history, newest first, or null when it
     * cannot be read. Shared with the watcher that notices lines printed
     * straight into the chat.
     */
    static List<ChatLine> messageHistory(GuiNewChat chat) {
        return chat == null ? null : messages(chat);
    }


    /**
     * What the history looks like right now: enough of it to notice a
     * message arriving, leaving, or being replaced.
     */
    private static long signatureOf(List<ChatLine> messages) {
        long signature = messages.size() * 31L + revision;
        if (!messages.isEmpty()) {
            ChatLine head = messages.get(0);
            if (head != null) {
                signature = signature * 31L + head.getChatLineID();
                signature = signature * 31L + head.getUpdatedCounter();
            }
            ChatLine tail = messages.get(messages.size() - 1);
            if (tail != null) {
                signature = signature * 31L + tail.getChatLineID();
            }
        }
        return signature;
    }

    @SuppressWarnings("unchecked")
    private static List<ChatLine> messages(GuiNewChat chat) {
        try {
            return (List<ChatLine>)CHAT_LINES.get(chat);
        } catch (IllegalAccessException unreadable) {
            return null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Vanilla's unwrapped message list, by either of its names and
     * verified to be the list it is. Without it a window cannot have a
     * width of its own, and the chat says so once.
     */
    private static Field resolveChatLines() {
        String[] names = { "chatLines", "field_146252_h" };
        for (String name : names) {
            try {
                Field field = GuiNewChat.class.getDeclaredField(name);
                if (Modifier.isStatic(field.getModifiers())
                        || !List.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                continue;
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }

    static void logUnavailableOnce() {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        FMLLog.warning("[%s] The chat's unwrapped history could not be "
                + "read; every window keeps the game's own chat width",
                LostTalesMetaData.MOD_ID);
    }

    /** One view's laid-out lines and what they were laid out for. */
    private static final class Cached {
        private final int wrapWidth;
        private final boolean colours;
        private final boolean chatOpen;
        /** Whether this view drops its lines a few seconds after they arrive. */
        private final boolean fading;
        private final ChatLineFilter filter;
        /** The history this layout describes; unchanged means reusable. */
        private long signature = Long.MIN_VALUE;
        /** Each message's own lines, held against the message itself. */
        private Map<ChatLine, Piece> wrapped =
                new IdentityHashMap<ChatLine, Piece>();
        List<ChatLine> lines = Collections.emptyList();

        Cached(int wrapWidth, boolean colours, boolean chatOpen,
               boolean fading, ChatLineFilter filter) {
            this.wrapWidth = wrapWidth;
            this.colours = colours;
            this.chatOpen = chatOpen;
            this.fading = fading;
            this.filter = filter;
        }

        boolean describes(int wrapWidth, boolean colours, boolean chatOpen,
                          boolean fading, ChatLineFilter filter) {
            return this.wrapWidth == wrapWidth && this.colours == colours
                    && this.chatOpen == chatOpen && this.fading == fading
                    && this.filter.equals(filter);
        }

        /**
         * Brings the layout up to date with the history. Messages
         * already laid out in the form this view still wants keep the
         * lines they had; only the rest are wrapped, and messages the
         * history has dropped go with them.
         */
        void refresh(FontRenderer font, List<ChatLine> messages,
                     ChatLineFilter filter, long signature) {
            if (signature == this.signature) {
                return;
            }
            this.signature = signature;
            List<ChatLine> visible =
                    new ArrayList<ChatLine>(messages.size());
            for (int index = 0; index < messages.size(); index++) {
                ChatLine message = messages.get(index);
                if (message != null && filter.accepts(
                        ClientChatChannelViews.tabOf(
                                message.getChatLineID()))) {
                    visible.add(message);
                }
            }
            // Grouping is this view's own: the same rule, run over the
            // messages the view shows and no others, and held to the
            // span this view lets a run go on for.
            int[] lineIds = new int[visible.size()];
            for (int index = 0; index < visible.size(); index++) {
                lineIds[index] = visible.get(index).getChatLineID();
            }
            boolean[] grouped = this.fading
                    ? ChatGroupRuns.continuationsInFeed(lineIds)
                    : ChatGroupRuns.continuationsOf(lineIds);
            Map<ChatLine, Piece> kept = new IdentityHashMap<ChatLine, Piece>(
                    this.wrapped.size() + 1);
            List<ChatLine> result =
                    new ArrayList<ChatLine>(messages.size());
            // A fading view fades a run as one, so every line of a run
            // carries the arrival tick of the run's newest message —
            // the clock the renderer ages each line on. Walking newest
            // first, that is the newest message met since the last line
            // that opened a run.
            int runNewest = 0;
            for (int index = 0; index < visible.size(); index++) {
                ChatLine message = visible.get(index);
                if (this.fading && index > 0 && !grouped[index - 1]) {
                    runNewest = index;
                }
                int counter = this.fading
                        ? visible.get(runNewest).getUpdatedCounter()
                        : message.getUpdatedCounter();
                Piece piece = this.wrapped.get(message);
                if (piece == null || piece.grouped != grouped[index]) {
                    piece = layOut(font, message, grouped[index], counter);
                } else if (piece.updatedCounter != counter) {
                    piece = piece.on(counter);
                }
                kept.put(message, piece);
                result.addAll(piece.lines);
            }
            this.wrapped = kept;
            this.lines = Collections.unmodifiableList(result);
        }

        /**
         * One message laid out at the width, its last line first: the
         * renderer draws index zero on the baseline and works upward. A
         * grouped message is laid out from the headerless form kept for
         * it, and from the full one when the history outlived that.
         */
        private Piece layOut(FontRenderer font, ChatLine message,
                             boolean grouped, int updatedCounter) {
            ChatGroupRuns.Entry entry = grouped
                    ? ChatGroupRuns.of(message.getChatLineID()) : null;
            IChatComponent root = entry == null ? message.func_151461_a()
                    : entry.groupedLine;
            List<IChatComponent> wrappedLines = ChatMessageWrapper.wrap(font,
                    root, this.wrapWidth, this.colours, this.chatOpen);
            return new Piece(grouped, updatedCounter, wrappedLines,
                    message.getChatLineID());
        }
    }

    /**
     * One message's lines, the form they were laid out in, and the
     * arrival tick they are drawn with — a fading view's whole run
     * shares the newest one, so the run fades out together.
     */
    private static final class Piece {
        final boolean grouped;
        final int updatedCounter;
        private final List<IChatComponent> wrappedLines;
        private final int chatLineId;
        final List<ChatLine> lines;

        Piece(boolean grouped, int updatedCounter,
              List<IChatComponent> wrappedLines, int chatLineId) {
            this.grouped = grouped;
            this.updatedCounter = updatedCounter;
            this.wrappedLines = wrappedLines;
            this.chatLineId = chatLineId;
            // Last line first: the renderer draws index zero on the
            // baseline and works upward.
            List<ChatLine> built =
                    new ArrayList<ChatLine>(wrappedLines.size());
            for (int index = wrappedLines.size() - 1; index >= 0; index--) {
                built.add(new ChatLine(updatedCounter,
                        wrappedLines.get(index), chatLineId));
            }
            this.lines = built;
        }

        /** The same layout on another clock; nothing is wrapped again. */
        Piece on(int updatedCounter) {
            return new Piece(this.grouped, updatedCounter, this.wrappedLines,
                    this.chatLineId);
        }
    }
}
