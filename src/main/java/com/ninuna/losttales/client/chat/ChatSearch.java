package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;

/**
 * One search at a time, over the window being typed in: the query, the
 * messages of the front tab that answer it — lit in place — and the one
 * the view stands on. The lines are the view's own, scanned each frame
 * they are drawn, so an edit, a removal or an older page arriving is
 * searched the moment it shows. Walking past the oldest match asks the
 * server for the page before the view's oldest line, the way scrolling
 * to the top does, and goes on asking until a match comes in or the
 * kept history is exhausted. Closed with the screen, and whenever the
 * input moves to another window.
 */
final class ChatSearch {
    /** How brightly a match is lit; the match the view stands on is lit whole. */
    static final float MATCH_SHARE = 0.4F;
    /** How long to wait for a page before giving the paging up. */
    private static final long PAGE_WAIT_NANOS = 4000L * 1000000L;

    /** What one line was read as, kept until its line is redrawn. */
    private static final class Cached {
        final int counter;
        final String text;
        final String identity;
        final String account;

        Cached(int counter, String text, String identity, String account) {
            this.counter = counter;
            this.text = text;
            this.identity = identity;
            this.account = account;
        }
    }

    private static String windowId;
    private static String query = "";
    private static ChatSearchQuery parsed = ChatSearchQuery.EMPTY;
    /** The matches as chat line ids, newest first, as the view lists them. */
    private static List<Integer> matches = Collections.emptyList();
    private static final Set<Integer> MATCH_SET = new HashSet<Integer>();
    /** The match the view stands on, as an index into {@link #matches}; -1 for none. */
    private static int current = -1;
    private static final Map<Integer, Cached> TEXTS = new HashMap<Integer, Cached>();
    private static boolean pagingOlder;
    private static long pagingSince;
    private static long pagedFrom;

    private ChatSearch() {}

    static synchronized boolean isOpen() {
        return windowId != null;
    }

    static synchronized boolean isOpenOn(String id) {
        return windowId != null && windowId.equals(id);
    }

    static synchronized String query() {
        return query;
    }

    /** Opens the search over a window; the one already open there is kept. */
    static synchronized void open(String id) {
        if (id == null || id.equals(windowId)) {
            return;
        }
        close();
        windowId = id;
    }

    static synchronized void close() {
        windowId = null;
        query = "";
        parsed = ChatSearchQuery.EMPTY;
        matches = Collections.emptyList();
        MATCH_SET.clear();
        current = -1;
        TEXTS.clear();
        pagingOlder = false;
    }

    /** Closes the search unless it is over the window being typed in. */
    static synchronized void closeUnless(String activeWindowId) {
        if (windowId != null && !windowId.equals(activeWindowId)) {
            close();
        }
    }

    /** The query as typed; a change forgets the place and rescans. */
    static synchronized void setQuery(String text) {
        String typed = text == null ? "" : text;
        if (typed.equals(query)) {
            return;
        }
        query = typed;
        parsed = ChatSearchQuery.parse(typed);
        current = -1;
        pagingOlder = false;
    }

    /** How lit a line is by the search: whole for the match stood on, a share for any other. */
    static synchronized float litShare(int chatLineId) {
        if (windowId == null || MATCH_SET.isEmpty()) {
            return 0.0F;
        }
        Integer id = Integer.valueOf(chatLineId);
        if (current >= 0 && current < matches.size() && matches.get(current).equals(id)) {
            return 1.0F;
        }
        return MATCH_SET.contains(id) ? MATCH_SHARE : 0.0F;
    }

    static synchronized int matchCount() {
        return matches.size();
    }

    /** The place of the match stood on, one-based; zero while on none. */
    static synchronized int position() {
        return current < 0 ? 0 : current + 1;
    }

    /**
     * Reads the view's lines against the query, keeping the match stood
     * on where it is, and answers the line to land on: the newest match
     * when the query changed or a page brought an older one that was
     * waited for; zero to stay. Called every frame the window is drawn.
     */
    static synchronized int scan(Minecraft minecraft, ChatWindowFrame frame) {
        if (windowId == null || frame == null || !windowId.equals(frame.windowId)) {
            return 0;
        }
        List<ChatLine> lines = frame.lines;
        Integer standing = current >= 0 && current < matches.size()
                ? matches.get(current) : null;
        int oldestBefore = matches.isEmpty() ? 0 : matches.get(matches.size() - 1).intValue();
        List<Integer> found = new ArrayList<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        if (!parsed.isEmpty() && lines != null) {
            for (int index = 0; index < lines.size(); index++) {
                ChatLine line = lines.get(index);
                if (line == null || ChatWindowLines.isFiller(line)) {
                    continue;
                }
                Integer id = Integer.valueOf(line.getChatLineID());
                if (!seen.add(id)) {
                    continue;
                }
                Cached cached = read(lines, index, line);
                if (parsed.matches(cached.text, cached.identity, cached.account)) {
                    found.add(id);
                }
            }
        }
        matches = found;
        MATCH_SET.clear();
        MATCH_SET.addAll(found);
        current = standing == null ? -1 : found.indexOf(standing);
        int land = 0;
        if (current < 0 && !found.isEmpty() && standing == null && !pagingOlder) {
            // A fresh query lands on the newest match.
            current = 0;
            land = found.get(0).intValue();
        }
        if (pagingOlder) {
            int oldestNow = found.isEmpty() ? 0 : found.get(found.size() - 1).intValue();
            if (!found.isEmpty() && oldestNow != oldestBefore
                    && (standing == null || current == found.size() - 2
                            || current < 0)) {
                // The page brought the older match that was waited for.
                current = found.size() - 1;
                land = oldestNow;
                pagingOlder = false;
            } else {
                keepPaging(minecraft, frame);
            }
        }
        return land;
    }

    /**
     * Walks to the next match (newer, below) or the previous one (older,
     * above), answering the line to land on, or zero when there is none
     * that way. Past the oldest match, the previous one is asked of the
     * server's kept history, and the landing follows when it comes.
     */
    static synchronized int walk(boolean forward, Minecraft minecraft,
                                 ChatWindowFrame frame) {
        if (windowId == null || frame == null || !windowId.equals(frame.windowId)) {
            return 0;
        }
        if (forward) {
            if (current > 0) {
                current--;
                return matches.get(current).intValue();
            }
            return 0;
        }
        if (current >= 0 && current < matches.size() - 1) {
            current++;
            return matches.get(current).intValue();
        }
        if (current < 0 && !matches.isEmpty()) {
            current = 0;
            return matches.get(0).intValue();
        }
        askOlder(minecraft, frame);
        return 0;
    }

    /** Asks for the page before the view's oldest line, once per oldest line. */
    private static void askOlder(Minecraft minecraft, ChatWindowFrame frame) {
        if (parsed.isEmpty() || frame.view == null
                || ClientChatOlderHistory.isExhausted(frame.view)) {
            pagingOlder = false;
            return;
        }
        long oldest = ClientChatOlderHistory.oldestNamedIn(frame.lines);
        if (ClientChatOlderHistory.requestIfAtTop(minecraft, frame.view, frame.lines, true)) {
            pagingOlder = true;
            pagingSince = System.nanoTime();
            pagedFrom = oldest;
        } else if (!pagingOlder) {
            pagingOlder = false;
        }
    }

    /** While a page is waited for: a page that came without a match asks for the next. */
    private static void keepPaging(Minecraft minecraft, ChatWindowFrame frame) {
        long now = System.nanoTime();
        if (frame.view == null || ClientChatOlderHistory.isExhausted(frame.view)
                || now - pagingSince > PAGE_WAIT_NANOS) {
            pagingOlder = false;
            return;
        }
        long oldest = ClientChatOlderHistory.oldestNamedIn(frame.lines);
        if (oldest != pagedFrom && ClientChatOlderHistory.requestIfAtTop(
                minecraft, frame.view, frame.lines, true)) {
            pagingSince = now;
            pagedFrom = oldest;
        }
    }

    /**
     * What a line says and who said it, read once per version of the
     * line: the message as it was said, the name it was signed with,
     * and the account behind that name.
     */
    private static Cached read(List<ChatLine> lines, int index, ChatLine line) {
        Integer id = Integer.valueOf(line.getChatLineID());
        Cached cached = TEXTS.get(id);
        if (cached != null && cached.counter == line.getUpdatedCounter()) {
            return cached;
        }
        String text = LostTalesChatClipboard.messageTextOf(lines, index);
        ChatGroupRuns.Entry run = ChatGroupRuns.of(line.getChatLineID());
        String identity = run == null ? "" : run.identityName;
        String account = "";
        ClientChatMessages.Remembered remembered = ClientChatMessages.get(
                ClientChatMessageIds.messageIdOf(line.getChatLineID()));
        if (remembered != null) {
            account = remembered.packet.getAccountName();
            if (identity.length() == 0) {
                identity = remembered.packet.getIdentityName();
            }
        }
        cached = new Cached(line.getUpdatedCounter(), text, identity, account);
        TEXTS.put(id, cached);
        while (TEXTS.size() > ClientChatChannelViews.maxTrackedLines()) {
            TEXTS.remove(TEXTS.keySet().iterator().next());
        }
        return cached;
    }
}
