package com.ninuna.losttales.quest;

import com.ninuna.losttales.chat.share.ChatQuestCard;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.compat.lotr.LotrQuestShareAdapter;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.quest.missive.MissiveWords;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Builds a bounded, server-authoritative card for one quest the sender
 * runs. The quest is found by the reference the sender's client sent; the
 * name typed in the token is only the label the sender's own game showed,
 * and never decides what is shared. The card carries data each reader's
 * game words in its own language ({@link ChatQuestCard}).
 */
public final class LostTalesQuestShareResolver {
    private LostTalesQuestShareResolver() {}

    public static ChatShowcase resolve(EntityPlayerMP sender,
            String reference, int tokenIndex) {
        if (sender == null || reference == null || reference.length() == 0) {
            return null;
        }
        if (LotrQuestReference.isLotrQuest(reference)) {
            return LotrQuestShareAdapter.resolve(sender, reference, tokenIndex);
        }
        return resolve(LostTalesQuestPlayerData.get(sender), reference,
                tokenIndex);
    }

    /**
     * The card for the Lost Tales quest {@code reference} names, while
     * {@code data}'s owner runs it; null for any other.
     */
    static ChatShowcase resolve(LostTalesQuestPlayerData data,
                                String reference, int tokenIndex) {
        LostTalesQuestProgress progress = data == null || reference == null
                ? null : data.getActiveQuest(reference);
        LostTalesQuestDefinition quest = progress == null
                ? null : LostTalesQuestRegistry.getQuest(reference);
        if (quest == null) {
            return null;
        }
        return ChatShowcase.quest(tokenIndex, reference, card(quest, progress),
                quest.canStartFromShare(
                        LostTalesConfig.allowQuestItemStarts,
                        LostTalesConfig.allowQuestInteractionStarts));
    }

    /**
     * The card for a quest at {@code progress}: where it comes from, its
     * category's word, the current stage's objectives with their counts,
     * and its reward as written. A bundled quest and a missive carry no
     * words; a server quest carries the words its operator wrote.
     */
    public static ChatQuestCard card(LostTalesQuestDefinition quest,
                                     LostTalesQuestProgress progress) {
        ChatQuestCard.Source source = quest.isBundled()
                ? ChatQuestCard.Source.BUNDLED
                : MissiveWords.isMissive(quest.getWords())
                ? ChatQuestCard.Source.MISSIVE : ChatQuestCard.Source.SERVER;
        String title = source == ChatQuestCard.Source.SERVER
                ? fitBytes(LostTalesQuestWords.title(quest),
                        ChatQuestCard.MAX_TITLE_BYTES)
                : source == ChatQuestCard.Source.MISSIVE
                ? quest.getWords().get(MissiveWords.TITLE) : "";
        return new ChatQuestCard(source, title,
                fitBytes(LostTalesQuestCategory.of(quest),
                        ChatQuestCard.MAX_CATEGORY_BYTES),
                objectives(quest, progress, source), rewards(quest.getRewards()));
    }

    private static List<ChatQuestCard.Objective> objectives(
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            ChatQuestCard.Source source) {
        List<ChatQuestCard.Objective> objectives =
                new ArrayList<ChatQuestCard.Objective>();
        int stage = progress == null ? -1
                : LostTalesQuestObjectiveSelection.getCurrentStageIndex(
                        quest, progress);
        if (stage < 0 || stage >= quest.getStages().size()) {
            // Nothing to count: a server quest's card reads its description.
            if (source == ChatQuestCard.Source.SERVER
                    && quest.getDescription().length() > 0) {
                objectives.add(new ChatQuestCard.Objective("", "",
                        Collections.<String, String>emptyMap(), 0, 0, false,
                        fitBytes(quest.getDescription(),
                                ChatQuestCard.MAX_OBJECTIVE_TEXT_BYTES)));
            }
            return objectives;
        }
        for (LostTalesQuestObjectiveDefinition objective
                : quest.getStages().get(stage).getObjectives()) {
            if (objectives.size() >= ChatQuestCard.MAX_OBJECTIVES) {
                break;
            }
            int count = Math.min(ChatQuestCard.MAX_COUNT,
                    LostTalesQuestObjectiveTextHelper.getObjectiveTargetCount(
                            objective));
            int done = Math.min(count, LostTalesQuestObjectiveTextHelper
                    .getObjectiveProgress(progress, objective, true, false));
            objectives.add(new ChatQuestCard.Objective(
                    fitBytes(objective.getId(),
                            ChatQuestCard.MAX_OBJECTIVE_ID_BYTES),
                    fitBytes(LostTalesQuestObjectiveType.of(objective)
                            .canonicalName(),
                            ChatQuestCard.MAX_OBJECTIVE_TYPE_BYTES),
                    targets(objective), count, Math.max(0, done),
                    objective.isOptional(),
                    source == ChatQuestCard.Source.SERVER
                            ? fitBytes(objective.getDescription(),
                                    ChatQuestCard.MAX_OBJECTIVE_TEXT_BYTES)
                            : ""));
        }
        return objectives;
    }

    /** The parameters an objective's words name its targets by, each within its bound. */
    private static Map<String, String> targets(
            LostTalesQuestObjectiveDefinition objective) {
        Map<String, String> targets = new LinkedHashMap<String, String>();
        for (String param : ChatQuestCard.TARGET_PARAMS) {
            String value = objective.getParam(param, "").trim();
            if (value.length() > 0) {
                targets.put(param, fitBytes(value, ChatQuestCard.MAX_TARGET_BYTES));
            }
        }
        return targets;
    }

    /** The reward entries as written, as many as a card holds, each within its bounds. */
    private static Map<String, String> rewards(Map<String, String> written) {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        if (written == null) {
            return rewards;
        }
        for (Map.Entry<String, String> entry : written.entrySet()) {
            if (rewards.size() >= ChatQuestCard.MAX_REWARDS) {
                break;
            }
            String key = entry.getKey() == null ? "" : entry.getKey();
            if (key.length() == 0 || !fits(key, ChatQuestCard.MAX_REWARD_KEY_BYTES)) {
                continue;
            }
            rewards.put(key, fitBytes(entry.getValue(),
                    ChatQuestCard.MAX_REWARD_VALUE_BYTES));
        }
        return Collections.unmodifiableMap(rewards);
    }

    private static boolean fits(String value, int maximumBytes) {
        return fitBytes(value, maximumBytes).length() == value.length();
    }

    /**
     * The text cut to at most {@code maximumBytes} bytes of UTF-8, at the
     * end of a character, so a card always fits what the chat sends.
     */
    public static String fitBytes(String value, int maximumBytes) {
        String text = value == null ? "" : value;
        int bytes = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int width = utf8Width(codePoint);
            if (bytes + width > maximumBytes) {
                return text.substring(0, index);
            }
            bytes += width;
            index += Character.charCount(codePoint);
        }
        return text;
    }

    /** The bytes a character takes in UTF-8; a lone surrogate counts three, more than it is written as. */
    private static int utf8Width(int codePoint) {
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        return codePoint < 0x10000 ? 3 : 4;
    }
}
