package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.world.WorldQuestView;
import java.util.Collection;
import java.util.LinkedHashMap;
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
            LostTalesClientQuestBannerStore.notifyWorldQuests(previous,
                    VIEWS);
        }
        received = true;
        ClientQuestCatalog.forget();
    }

    public static synchronized WorldQuestView view(String questId) {
        return VIEWS.get(questId);
    }

    /** The world quests running now, as the server last said, in its order. */
    public static synchronized java.util.List<WorldQuestView> running() {
        java.util.List<WorldQuestView> running = new java.util.ArrayList<WorldQuestView>();
        for (WorldQuestView view : VIEWS.values()) {
            if (view.isRunning()) {
                running.add(view);
            }
        }
        return running;
    }

    /**
     * Whether the player carries something a running world quest asks to
     * be handed in at a missive board (QUEST-36): what greys or lights the
     * board's Hand In. The server counts again.
     */
    public static boolean carriesForBoard(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null || player.inventory == null) {
            return false;
        }
        for (WorldQuestView view : running()) {
            com.ninuna.losttales.quest.LostTalesQuestDefinition quest =
                    LostTalesClientQuestDefinitionStore.getQuest(view.getQuestId());
            for (com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition objective
                    : com.ninuna.losttales.quest.world.WorldQuestRules.objectives(quest)) {
                if (!com.ninuna.losttales.quest.LostTalesQuestObjectiveType.DELIVER.is(objective)
                        || !com.ninuna.losttales.quest.LostTalesQuestDefinitionValidator
                                .isBoardDelivery(objective.getParams())
                        || view.getCount(objective.getId())
                                >= com.ninuna.losttales.quest.world.WorldQuestRules.goal(objective)) {
                    continue;
                }
                for (net.minecraft.item.ItemStack stack : player.inventory.mainInventory) {
                    if (stack != null && com.ninuna.losttales.quest.LostTalesQuestObjectiveMatcher
                            .matchesItem(stack, objective)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Forgets the server's word as the player leaves it. */
    public static synchronized void clear() {
        VIEWS.clear();
        received = false;
    }
}
