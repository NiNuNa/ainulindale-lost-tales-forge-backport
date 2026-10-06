package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.client.window.PageSearch;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import com.ninuna.losttales.quest.missive.MissiveWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The missive board page's list of notices, worked out from the board's
 * notices alone: which a search keeps, which is picked as the notices
 * change under it, the walk from one to the next, and how long each has
 * left on the board. Free of the game itself, so a test can ask it
 * everything the page does.
 */
final class MissiveNoticeList {
    private MissiveNoticeList() {}

    /**
     * The notices a search keeps, in slot order: all of them while nothing
     * is typed, else each whose title, issuer, words or objectives, in the
     * game's language, hold every word typed, in any order and whatever the
     * case. A notice that cannot be read says nothing, so only an empty
     * search keeps it.
     */
    static List<MissiveNotice> of(List<MissiveNotice> notices, String query) {
        if (notices == null || notices.isEmpty()) {
            return Collections.emptyList();
        }
        PageSearch search = PageSearch.of(query);
        List<MissiveNotice> kept = new ArrayList<MissiveNotice>();
        for (MissiveNotice notice : notices) {
            if (notice != null && (search.isEmpty() || matches(search, notice))) {
                kept.add(notice);
            }
        }
        return kept;
    }

    private static boolean matches(PageSearch search, MissiveNotice notice) {
        LostTalesMissiveData missive = notice.getMissive();
        if (missive == null) {
            return false;
        }
        List<String> parts = new ArrayList<String>();
        parts.add(MissiveWords.title(missive));
        parts.add(MissiveWords.issuer(missive));
        parts.add(MissiveWords.description(missive));
        parts.add(MissiveWords.flavor(missive));
        for (LostTalesMissiveObjectiveData objective : missive.getObjectives()) {
            parts.add(MissiveWords.objective(objective));
        }
        return search.matches(parts.toArray(new String[parts.size()]));
    }

    /** The notice in {@code slot}; null for none. */
    static MissiveNotice inSlot(List<MissiveNotice> notices, int slot) {
        if (notices == null) {
            return null;
        }
        for (MissiveNotice notice : notices) {
            if (notice.getSlot() == slot) {
                return notice;
            }
        }
        return null;
    }

    /**
     * The slot to pick once the notices changed: the letter picked, where
     * it still stands; else the notice now in its slot; else the nearest
     * one before it, else the first; -1 once there are none. Nothing
     * picked yet picks the first.
     */
    static int keepPick(List<MissiveNotice> notices, int pickedSlot,
                        String pickedQuestId) {
        if (notices == null || notices.isEmpty()) {
            return -1;
        }
        if (pickedQuestId != null && pickedQuestId.length() > 0) {
            for (MissiveNotice notice : notices) {
                if (pickedQuestId.equals(notice.getQuestId())) {
                    return notice.getSlot();
                }
            }
        }
        if (pickedSlot < 0) {
            return notices.get(0).getSlot();
        }
        int before = -1;
        for (MissiveNotice notice : notices) {
            if (notice.getSlot() == pickedSlot) {
                return pickedSlot;
            }
            if (notice.getSlot() < pickedSlot) {
                before = notice.getSlot();
            }
        }
        return before >= 0 ? before : notices.get(0).getSlot();
    }

    /**
     * The notice {@code step} places after the picked one among those
     * shown (before it for a negative step), stopping at either end; the
     * first shown where none of them is picked, -1 for none shown.
     */
    static int step(List<MissiveNotice> shown, int pickedSlot, int step) {
        if (shown == null || shown.isEmpty()) {
            return -1;
        }
        int index = -1;
        for (int at = 0; at < shown.size(); at++) {
            if (shown.get(at).getSlot() == pickedSlot) {
                index = at;
                break;
            }
        }
        if (index < 0) {
            return shown.get(0).getSlot();
        }
        int next = Math.max(0, Math.min(shown.size() - 1, index + step));
        return shown.get(next).getSlot();
    }

    /**
     * The world ticks a notice has left on the board at {@code now}, the
     * board having said how many it had at {@code receivedAt}; never
     * below none, and {@link MissiveNotice#STAYS_UP} for one the board
     * never takes down. A world clock that went back counts from the time
     * it was received.
     */
    static long ticksLeft(MissiveNotice notice, long receivedAt, long now) {
        if (notice == null || notice.staysUp()) {
            return MissiveNotice.STAYS_UP;
        }
        long passed = Math.max(0L, now - receivedAt);
        return Math.max(0L, notice.getTicksLeft() - passed);
    }
}
