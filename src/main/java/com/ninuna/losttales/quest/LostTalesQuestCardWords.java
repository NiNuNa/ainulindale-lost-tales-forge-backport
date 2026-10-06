package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.compat.lotr.LotrQuestShareAdapter;
import com.ninuna.losttales.quest.missive.MissiveWords;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.StatCollector;

/**
 * A shared quest's card in the words of the game reading it
 * ({@link ChatQuestCard}): a bundled quest's title and objectives from the
 * lang lines its id names, a missive's from its template ids, every
 * objective without words of its own from its kind and targets, and the
 * reward from what the quest pays. Words a server's operator or LOTR
 * gave the quest are shown as they came.
 */
public final class LostTalesQuestCardWords {
    private LostTalesQuestCardWords() {}

    /** The card's title; the quest's reference where nothing else names it. */
    public static String title(String reference, ChatQuestCard card) {
        String id = reference == null ? "" : reference;
        switch (card.getSource()) {
            case BUNDLED:
                return LostTalesQuestWords.line(
                        LostTalesQuestWords.titleKey(id), id);
            case MISSIVE:
                return MissiveWords.title(card.getTitle());
            case LOTR:
                return card.getTitle().length() > 0 ? card.getTitle()
                        : StatCollector.translateToLocal(
                                "gui.losttales.quest.lotr.untitled");
            case LOTR_WELCOME:
                return StatCollector.translateToLocal(
                        "gui.losttales.quest.lotr.grey_wanderer");
            default:
                return card.getTitle().length() > 0 ? card.getTitle() : id;
        }
    }

    /**
     * The card's objectives on one line, parted by semicolons, each with
     * how far along it is; a bundled quest with none to count reads its
     * description.
     */
    public static String objectives(String reference, ChatQuestCard card) {
        StringBuilder text = new StringBuilder();
        for (ChatQuestCard.Objective objective : card.getObjectives()) {
            String line = objectiveLine(reference, card.getSource(), objective);
            if (line.length() == 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(line);
        }
        if (text.length() == 0 && card.getSource() == ChatQuestCard.Source.BUNDLED) {
            return LostTalesQuestWords.line(
                    LostTalesQuestWords.descriptionKey(reference), "");
        }
        return text.toString();
    }

    /** What the quest pays, in a line; the pending line for a quest that names nothing. */
    public static String reward(ChatQuestCard card) {
        if (card.getSource() == ChatQuestCard.Source.LOTR_WELCOME) {
            return StatCollector.translateToLocal(
                    "gui.losttales.quest.lotr.reward.tutorial");
        }
        if (card.getSource() == ChatQuestCard.Source.LOTR) {
            String faction = LotrQuestShareAdapter.factionName(
                    card.getRewards().get(ChatQuestCard.LOTR_PAYMENT));
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.quest.reward.payment",
                    faction.length() > 0 ? faction : StatCollector.translateToLocal(
                            "gui.losttales.quest.reward.faction"));
        }
        String rewards = LostTalesQuestRewardText.summary(card.getRewards());
        return rewards.length() > 0 ? rewards : StatCollector.translateToLocal(
                "gui.losttales.quest.reward.pending");
    }

    /**
     * One objective as the journal says it: its own words where it carries
     * them and has nothing to count, else its words or its line, with
     * {@code (2/5)} and whether it is optional.
     */
    static String objectiveLine(String reference, ChatQuestCard.Source source,
                                ChatQuestCard.Objective objective) {
        if (objective.getType().length() == 0) {
            return objective.getText();
        }
        Map<String, String> params = new LinkedHashMap<String, String>(
                objective.getTargets());
        params.put("count", String.valueOf(objective.getCount()));
        LostTalesQuestObjectiveDefinition definition =
                new LostTalesQuestObjectiveDefinition(objective.getId(),
                        objective.getType(), objective.getText(),
                        objective.isOptional(), params,
                        source == ChatQuestCard.Source.BUNDLED
                                ? LostTalesQuestWords.objectiveKey(reference,
                                        objective.getId())
                                : source == ChatQuestCard.Source.MISSIVE
                                ? MissiveWords.objectiveKey(objective.getType())
                                : "");
        String line = LostTalesQuestObjectiveTextHelper.describe(definition)
                + " (" + Math.min(objective.getProgress(),
                        LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(
                                definition))
                + "/" + LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(
                        definition) + ")";
        return objective.isOptional() ? line + " " + StatCollector.translateToLocal(
                "gui.losttales.quest.objective.optional") : line;
    }
}
