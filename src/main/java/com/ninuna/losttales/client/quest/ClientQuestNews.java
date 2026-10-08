package com.ninuna.losttales.client.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * This session's quest news, for the inbox: a quest started, advanced,
 * finished, failed or given up, and a world quest started or ended, each
 * with the quest it is about and the words its banner showed. It lasts
 * the session: the first sync after joining is silent, so nothing old is
 * news. It also knows which news the inbox has shown, so its button
 * counts only the rest.
 */
public final class ClientQuestNews {
    /** The most news kept; the oldest goes first. */
    static final int MAX_NEWS = 50;

    /** One piece of news. */
    public static final class News {
        public final String questId;
        /** The words the banner showed, in the reader's language. */
        public final String line;
        /** When it arrived, on this computer's clock. */
        public final long timeMillis;
        final long serial;

        News(String questId, String line, long timeMillis, long serial) {
            this.questId = questId;
            this.line = line;
            this.timeMillis = timeMillis;
            this.serial = serial;
        }
    }

    private static final LinkedList<News> NEWS = new LinkedList<News>();
    private static long nextSerial = 1L;
    /** The newest news the inbox has shown; 0 before it showed any. */
    private static long seenUpTo;

    private ClientQuestNews() {}

    /** Keeps a piece of news about {@code questId}, shown as {@code line}. */
    public static synchronized void record(String questId, String line) {
        if (questId == null || questId.length() == 0 || line == null
                || line.length() == 0) {
            return;
        }
        NEWS.addLast(new News(questId, line, System.currentTimeMillis(),
                nextSerial++));
        while (NEWS.size() > MAX_NEWS) {
            NEWS.removeFirst();
        }
    }

    /** Every piece of news kept, the newest first. */
    public static synchronized List<News> all() {
        List<News> newest = new ArrayList<News>(NEWS);
        Collections.reverse(newest);
        return newest;
    }

    /** How many pieces of news the inbox has not shown yet. */
    public static synchronized int unseen() {
        int count = 0;
        for (News news : NEWS) {
            if (news.serial > seenUpTo) {
                count++;
            }
        }
        return count;
    }

    /** The inbox shows every piece of news kept now. */
    public static synchronized void markSeen() {
        seenUpTo = nextSerial - 1L;
    }

    /** Forgets the session's news: the world is left. */
    public static synchronized void clear() {
        NEWS.clear();
        seenUpTo = nextSerial - 1L;
    }
}
