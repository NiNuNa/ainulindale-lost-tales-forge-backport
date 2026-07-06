package com.ninuna.losttales.quest;

/**
 * Coarse-grained start source used by the 1.7.10 quest manager.
 *
 * This replaces modern interaction/context objects with a tiny enum so the server can
 * reject client journal starts for quests intended to begin from an item, NPC, or script.
 */
public enum LostTalesQuestStartSource {
    COMMAND,
    JOURNAL,
    ITEM,
    INTERACTION
}
