package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.client.window.WindowPage;
import com.ninuna.losttales.client.window.WindowSearch;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;

/**
 * What the words each conversation's copy keeps in its well
 * ({@link WindowSearch}) find in it: the messages that answer them — lit in
 * place — and the one the view stands on, for each copy apart, so two
 * windows search their own conversations at once (WIN-25). The lines are
 * the view's own, scanned each frame they are drawn, so an edit, a removal
 * or an older page arriving is searched the moment it shows. Walking past
 * the oldest match asks the server for the page before the view's oldest
 * line, the way scrolling to the top does, and goes on asking until a
 * match comes in or the kept history is exhausted. What a copy found goes
 * as its words are cleared, and everything as the world is left.
 */
public final class ChatSearch {
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

    /** What one copy's words found. */
    private static final class Found {
        /** The turn the copy's words were cleared on, as these were found after it. */
        int seenGeneration;
        String seenQuery = "";
        ChatSearchQuery parsed = ChatSearchQuery.EMPTY;
        /** The matches as chat line ids, newest first, as the view lists them. */
        List<Integer> matches = Collections.emptyList();
        final Set<Integer> matchSet = new HashSet<Integer>();
        /** The match the view stands on, as an index into {@link #matches}; -1 for none. */
        int current = -1;
        boolean pagingOlder;
        long pagingSince;
        long pagedFrom;
    }

    /** What each copy's words found, by the copy's page id, while it keeps words. */
    private static final Map<String, Found> FOUND = new HashMap<String, Found>();
    /** The time everything was forgotten that what is kept was found after. */
    private static int seenEpoch = -1;
    /** Each line as read, the one read first going first past the bound. */
    private static final Map<Integer, Cached> TEXTS = new LinkedHashMap<Integer, Cached>();
    /** What the words of the copy whose lines are being drawn found; null between windows. */
    private static Found drawing;

    private ChatSearch() {}

    /**
     * What the words of the copy whose page has {@code copyId} found, caught
     * up with them: words cleared since forget what they found, new words
     * forget the place and are read again. Null while the copy keeps none.
     */
    private static Found found(String copyId) {
        int epoch = WindowSearch.epoch();
        if (epoch != seenEpoch) {
            seenEpoch = epoch;
            FOUND.clear();
            TEXTS.clear();
        }
        String query = WindowSearch.wordsOf(copyId);
        if (query.length() == 0) {
            if (FOUND.remove(copyId) != null && FOUND.isEmpty()) {
                TEXTS.clear();
            }
            return null;
        }
        Found found = FOUND.get(copyId);
        int generation = WindowSearch.generation(copyId);
        if (found == null || found.seenGeneration != generation) {
            found = new Found();
            found.seenGeneration = generation;
            FOUND.put(copyId, found);
        }
        if (!query.equals(found.seenQuery)) {
            found.seenQuery = query;
            found.parsed = ChatSearchQuery.parse(query);
            found.current = -1;
            found.pagingOlder = false;
        }
        return found;
    }

    /** What the words of the copy in front of the window found; null for none. */
    private static Found foundIn(String windowId) {
        WindowPage front = WindowSearch.frontOf(windowId);
        return front == null ? null : found(front.id());
    }

    /**
     * The window whose lines are drawn next, the words of the copy in
     * front there lighting them ({@link #litShare}); null once they are
     * drawn.
     */
    static synchronized void drawingIn(String windowId) {
        drawing = windowId == null ? null : foundIn(windowId);
    }

    /**
     * How lit a line of the window being drawn is by its copy's words:
     * whole for the match stood on, a share for any other.
     */
    static synchronized float litShare(int chatLineId) {
        Found found = drawing;
        if (found == null || found.matchSet.isEmpty()) {
            return 0.0F;
        }
        Integer id = Integer.valueOf(chatLineId);
        if (found.current >= 0 && found.current < found.matches.size()
                && found.matches.get(found.current).equals(id)) {
            return 1.0F;
        }
        return found.matchSet.contains(id) ? MATCH_SHARE : 0.0F;
    }

    /** How many messages the copy's words found. */
    public static synchronized int matchCount(WindowPage copy) {
        Found found = copy == null ? null : found(copy.id());
        return found == null ? 0 : found.matches.size();
    }

    /** The place of the match the copy stands on, one-based; zero while on none. */
    public static synchronized int position(WindowPage copy) {
        Found found = copy == null ? null : found(copy.id());
        return found == null || found.current < 0 ? 0 : found.current + 1;
    }

    /**
     * Reads the view's lines against the words of the copy in front of
     * the frame's window, keeping the match stood on where it is, and
     * answers the line to land on: the newest match when the words changed
     * or a page brought an older one that was waited for; zero to stay.
     * Called every frame the window is drawn.
     */
    static synchronized int scan(Minecraft minecraft, ChatFrame frame) {
        Found found = frame == null ? null : foundIn(frame.windowId);
        if (found == null) {
            return 0;
        }
        List<ChatLine> lines = frame.lines;
        Integer standing = found.current >= 0
                && found.current < found.matches.size()
                ? found.matches.get(found.current) : null;
        int oldestBefore = found.matches.isEmpty() ? 0
                : found.matches.get(found.matches.size() - 1).intValue();
        List<Integer> matched = new ArrayList<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        if (!found.parsed.isEmpty() && lines != null) {
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
                if (found.parsed.matches(cached.text, cached.identity,
                        cached.account)) {
                    matched.add(id);
                }
            }
        }
        found.matches = matched;
        found.matchSet.clear();
        found.matchSet.addAll(matched);
        found.current = standing == null ? -1 : matched.indexOf(standing);
        int land = 0;
        if (found.current < 0 && !matched.isEmpty() && standing == null
                && !found.pagingOlder) {
            // Fresh words land on the newest match.
            found.current = 0;
            land = matched.get(0).intValue();
        }
        if (found.pagingOlder) {
            int oldestNow = matched.isEmpty() ? 0
                    : matched.get(matched.size() - 1).intValue();
            if (!matched.isEmpty() && oldestNow != oldestBefore
                    && (standing == null || found.current == matched.size() - 2
                            || found.current < 0)) {
                // The page brought the older match that was waited for.
                found.current = matched.size() - 1;
                land = oldestNow;
                found.pagingOlder = false;
            } else {
                keepPaging(found, minecraft, frame);
            }
        }
        return land;
    }

    /**
     * Walks to the next match (newer, below) or the previous one (older,
     * above) of the copy in front of the frame's window, answering the
     * line to land on, or zero when there is none that way. Past the
     * oldest match, the previous one is asked of the server's kept
     * history, and the landing follows when it comes.
     */
    static synchronized int walk(boolean forward, Minecraft minecraft,
                                 ChatFrame frame) {
        Found found = frame == null ? null : foundIn(frame.windowId);
        if (found == null) {
            return 0;
        }
        if (forward) {
            if (found.current > 0) {
                found.current--;
                return found.matches.get(found.current).intValue();
            }
            return 0;
        }
        if (found.current >= 0 && found.current < found.matches.size() - 1) {
            found.current++;
            return found.matches.get(found.current).intValue();
        }
        if (found.current < 0 && !found.matches.isEmpty()) {
            found.current = 0;
            return found.matches.get(0).intValue();
        }
        askOlder(found, minecraft, frame);
        return 0;
    }

    /** Asks for the page before the view's oldest line, once per oldest line. */
    private static void askOlder(Found found, Minecraft minecraft,
                                 ChatFrame frame) {
        if (found.parsed.isEmpty() || frame.view == null
                || ClientChatOlderHistory.isExhausted(frame.view)) {
            found.pagingOlder = false;
            return;
        }
        long oldest = ClientChatOlderHistory.oldestNamedIn(frame.lines);
        if (ClientChatOlderHistory.requestIfAtTop(minecraft, frame.view,
                frame.lines, true)) {
            found.pagingOlder = true;
            found.pagingSince = System.nanoTime();
            found.pagedFrom = oldest;
        }
    }

    /** While a page is waited for: a page that came without a match asks for the next. */
    private static void keepPaging(Found found, Minecraft minecraft,
                                   ChatFrame frame) {
        long now = System.nanoTime();
        if (frame.view == null || ClientChatOlderHistory.isExhausted(frame.view)
                || now - found.pagingSince > PAGE_WAIT_NANOS) {
            found.pagingOlder = false;
            return;
        }
        long oldest = ClientChatOlderHistory.oldestNamedIn(frame.lines);
        if (oldest != found.pagedFrom && ClientChatOlderHistory.requestIfAtTop(
                minecraft, frame.view, frame.lines, true)) {
            found.pagingSince = now;
            found.pagedFrom = oldest;
        }
    }

    /**
     * What a line says and who said it, read once per version of the
     * line: the message as it was said, the name it was signed with,
     * and the account behind that name. The Server, the Client and the
     * Narrator are found by the names this game shows them by, and by the
     * name their lines are signed with.
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
        UUID senderId = run != null ? run.senderId
                : remembered != null ? remembered.packet.getSenderId() : null;
        identity = ChatNames.sender(LostTalesWords.LANG, senderId,
                remembered != null ? remembered.packet.getSkinId() : "", identity);
        cached = new Cached(line.getUpdatedCounter(), text, identity, account);
        TEXTS.put(id, cached);
        while (TEXTS.size() > ClientChatChannelViews.maxTrackedLines()) {
            TEXTS.remove(TEXTS.keySet().iterator().next());
        }
        return cached;
    }
}
