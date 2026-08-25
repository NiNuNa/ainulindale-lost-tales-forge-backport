package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

/**
 * The lines one window shows, laid out at that window's own width.
 *
 * <p>Minecraft wraps a message once, at one width, and keeps the result
 * in the list every chat draw reads — so windows of different widths
 * cannot share it. The unwrapped messages behind that list can, though:
 * this reads them, keeps the ones the window's view accepts, and lays
 * each out again at the window's width, in the same order vanilla files
 * its own — newest message first, and within a message its last line
 * first, because that is the order the renderer walks upward from the
 * baseline.</p>
 *
 * <p>Every open window is laid out here, whatever width it has. The
 * shared list is wrapped for the closed-chat feed, which shows the
 * channel prefix on every line; the open screen hides that prefix — the
 * tabs already name the channel — so a window laid out here gives its
 * width back to the message body instead of leaving it blank at the end
 * of each line. A window whose history cannot be read falls back to the
 * shared list.</p>
 *
 * <p>Results are cached per window and rebuilt only when the width, the
 * chat scale, the colour setting or the history itself changes, and a
 * rebuild costs only the messages it has not seen: each message's own
 * lines are kept against the message, so an arriving message lays out
 * one message rather than the whole history. Only the open chat screen
 * asks here at all, so nothing of this runs during normal play.</p>
 */
final class ChatWindowLines {
    /** Vanilla's unwrapped history, newest first. */
    private static final Field CHAT_LINES = resolveChatLines();
    private static final Map<String, Cached> CACHE =
            new HashMap<String, Cached>();
    private static boolean unavailableLogged;

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
        if (CHAT_LINES == null || minecraft == null
                || minecraft.fontRenderer == null || chat == null
                || window == null || filter == null || chatWidth <= 0) {
            return null;
        }
        List<ChatLine> messages = messages(chat);
        if (messages == null) {
            return null;
        }
        float scale = chat.func_146244_h();
        boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
        // The timestamp column at the window's left edge comes out of
        // the room the messages may wrap to, so a line never runs out
        // under the window's right edge to pay for it.
        ChatTimestampColumn columns =
                ChatTimestampColumn.current(minecraft.fontRenderer);
        int wrapWidth = Math.max(1,
                ChatWindowPlacement.wrapWidth(chatWidth, scale)
                        - (columns.messageX() - 2));
        Cached cached = CACHE.get(window.getId());
        if (cached == null || !cached.describes(wrapWidth, colours, filter)) {
            cached = new Cached(wrapWidth, colours, filter);
            CACHE.put(window.getId(), cached);
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
        long signature = messages.size();
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

    /** One window's laid-out lines and what they were laid out for. */
    private static final class Cached {
        private final int wrapWidth;
        private final boolean colours;
        private final ChatLineFilter filter;
        /** The history this layout describes; unchanged means reusable. */
        private long signature = Long.MIN_VALUE;
        /** Each message's own lines, held against the message itself. */
        private Map<ChatLine, List<ChatLine>> wrapped =
                new IdentityHashMap<ChatLine, List<ChatLine>>();
        List<ChatLine> lines = Collections.emptyList();

        Cached(int wrapWidth, boolean colours, ChatLineFilter filter) {
            this.wrapWidth = wrapWidth;
            this.colours = colours;
            this.filter = filter;
        }

        boolean describes(int wrapWidth, boolean colours,
                          ChatLineFilter filter) {
            return this.wrapWidth == wrapWidth && this.colours == colours
                    && this.filter.equals(filter);
        }

        /**
         * Brings the layout up to date with the history. Messages
         * already laid out keep the lines they had; only ones this
         * layout has not seen are wrapped, and messages the history has
         * dropped go with them.
         */
        void refresh(FontRenderer font, List<ChatLine> messages,
                     ChatLineFilter filter, long signature) {
            if (signature == this.signature) {
                return;
            }
            this.signature = signature;
            Map<ChatLine, List<ChatLine>> kept =
                    new IdentityHashMap<ChatLine, List<ChatLine>>(
                            this.wrapped.size() + 1);
            List<ChatLine> result =
                    new ArrayList<ChatLine>(messages.size());
            for (int index = 0; index < messages.size(); index++) {
                ChatLine message = messages.get(index);
                if (message == null || !filter.accepts(
                        ClientChatChannelViews.tabOf(
                                message.getChatLineID()))) {
                    continue;
                }
                List<ChatLine> pieces = this.wrapped.get(message);
                if (pieces == null) {
                    pieces = layOut(font, message);
                }
                kept.put(message, pieces);
                result.addAll(pieces);
            }
            this.wrapped = kept;
            this.lines = Collections.unmodifiableList(result);
        }

        /**
         * One message laid out at the width, its last line first: the
         * renderer draws index zero on the baseline and works upward.
         */
        private List<ChatLine> layOut(FontRenderer font, ChatLine message) {
            List<IChatComponent> wrappedLines = ChatMessageWrapper.wrap(font,
                    message.func_151461_a(), this.wrapWidth, this.colours,
                    true);
            List<ChatLine> pieces =
                    new ArrayList<ChatLine>(wrappedLines.size());
            for (int piece = wrappedLines.size() - 1; piece >= 0; piece--) {
                pieces.add(new ChatLine(message.getUpdatedCounter(),
                        wrappedLines.get(piece), message.getChatLineID()));
            }
            return pieces;
        }
    }
}
