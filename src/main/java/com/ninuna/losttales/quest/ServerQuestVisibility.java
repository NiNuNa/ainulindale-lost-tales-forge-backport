package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which of the server's own quests a client is sent ({@link
 * ServerQuestSync}): only those it needs, each once. A client needs a
 * quest that stands in its player's log, a world quest run it is shown,
 * and a quest a giver offers it.
 */
final class ServerQuestVisibility {
    private ServerQuestVisibility() {}

    /**
     * Of the quests {@code wanted}, those to send now: the server's own,
     * not sent before, each once, in the order asked.
     */
    static List<String> newlyVisible(Set<String> sent,
                                     Collection<String> wanted,
                                     Set<String> serverQuests) {
        Set<String> fresh = new LinkedHashSet<String>();
        if (wanted != null) {
            for (String questId : wanted) {
                if (questId != null && serverQuests.contains(questId)
                        && !sent.contains(questId)) {
                    fresh.add(questId);
                }
            }
        }
        return new ArrayList<String>(fresh);
    }

    /**
     * What a client holds once the server's files are read again: what it
     * was sent that the server still has, and what its log names now.
     */
    static Set<String> afterReload(Set<String> sent, Collection<String> inLog,
                                   Set<String> serverQuests) {
        Set<String> kept = new LinkedHashSet<String>();
        for (String questId : sent) {
            if (serverQuests.contains(questId)) {
                kept.add(questId);
            }
        }
        if (inLog != null) {
            for (String questId : inLog) {
                if (questId != null && serverQuests.contains(questId)) {
                    kept.add(questId);
                }
            }
        }
        return kept;
    }
}
