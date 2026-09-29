package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.world.WorldQuestView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world quests as the server last said they stand, and this player's
 * part in each ({@link WorldQuestView}). A run that starts, succeeds or
 * fails shows as a banner; the first list after joining is shown quietly,
 * as the quest log's is. Forgotten as the player leaves the server.
 */
public final class ClientWorldQuests {
    private static final Map<String, WorldQuestView> VIEWS =
            new LinkedHashMap<String, WorldQuestView>();
    private static boolean received;

    private ClientWorldQuests() {}

    /** Takes the server's list in place of the one held, and shows what changed as banners. */
    public static synchronized void update(Collection<WorldQuestView> views) {
        Map<String, WorldQuestView> previous =
                new LinkedHashMap<String, WorldQuestView>(VIEWS);
        VIEWS.clear();
        if (views != null) {
            for (WorldQuestView view : views) {
                if (view != null) {
                    VIEWS.put(view.getQuestId(), view);
                }
            }
        }
        if (received) {
            LostTalesClientQuestNotificationStore.notifyWorldQuests(previous,
                    VIEWS);
        }
        received = true;
        ClientQuestCatalog.forget();
    }

    public static synchronized List<WorldQuestView> views() {
        return Collections.unmodifiableList(
                new ArrayList<WorldQuestView>(VIEWS.values()));
    }

    public static synchronized WorldQuestView view(String questId) {
        return VIEWS.get(questId);
    }

    /** Forgets the server's word as the player leaves it. */
    public static synchronized void clear() {
        VIEWS.clear();
        received = false;
    }
}
