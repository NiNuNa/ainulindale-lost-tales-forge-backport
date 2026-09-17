package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.client.chat.LostTalesNpcChatHook;
import com.ninuna.losttales.compat.lotr.LotrMiniquestOfferAccess;
import com.ninuna.losttales.gui.screen.LostTalesQuestDialogueGui;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.screen.quest.QuestDialogueModel;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDialogue;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveMatcher;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveSelection;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Map;
import lotr.client.gui.LOTRGuiMiniquestOffer;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRSpeech;
import lotr.common.network.LOTRPacketMiniquestOffer;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.StatCollector;

/**
 * Where a quest conversation is opened from.
 *
 * <p>Two ways in: LOTR opening its own offer screen, which is read and
 * shown in this mod's dress instead; and touching somebody a Lost Tales
 * quest names, which the client answers from the definitions and the
 * progress it already holds. Neither decides anything the server owns —
 * a reply is a request, and the server checks the whole of it again.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesQuestDialogueHooks {

    private LostTalesQuestDialogueHooks() {}

    /**
     * The conversation to show in place of LOTR's own offer screen, or
     * null to leave LOTR's alone — which is what happens when the
     * conversation is switched off or the offer cannot be read.
     */
    public static GuiScreen replaceLotrOffer(LOTRGuiMiniquestOffer offer) {
        if (offer == null || !LostTalesQuestDialogueGui.isEnabled()
                || !LotrMiniquestOfferAccess.isAvailable()) {
            return null;
        }
        final LOTRMiniQuest quest = LotrMiniquestOfferAccess.quest(offer);
        final LOTREntityNPC npc = LotrMiniquestOfferAccess.npc(offer);
        if (quest == null || npc == null) {
            return null;
        }
        String said = spoken(quest, npc);
        if (said.length() == 0) {
            // Without the quest's own words there is nothing to say that
            // LOTR would not say better; its screen keeps the offer.
            return null;
        }
        QuestDialogueModel model = QuestDialogueModel.of(
                npc.getNPCName(), safe(quest.getFactionSubtitle()),
                safe(quest.getQuestObjective()), "",
                QuestDialogueModel.Mood.OFFER, said,
                // What the quest pays is the one thing LOTR holds and
                // does not show at the offer, so it is what asking for
                // more is worth. Nothing is invented.
                rewardSummary(quest),
                new String[] {
                        translate("gui.losttales.quest.dialogue.accept"),
                        translate("gui.losttales.quest.dialogue.more"),
                        "",
                        translate("gui.losttales.quest.dialogue.decline"),
                        translate("gui.losttales.quest.dialogue.leave")});
        return new LostTalesQuestDialogueGui(null, model,
                new LostTalesQuestDialogueGui.Action() {
                    @Override
                    public void accept() {
                        close(npc, true);
                    }

                    @Override
                    public void leave() {
                        close(npc, false);
                    }

                    @Override
                    public void handOver() {
                        close(npc, false);
                    }

                    @Override
                    public void says(String line) {
                        LostTalesNpcChatHook.sayToPlayer(npc, line,
                                LostTalesNpcChatHook.speakerColor(npc),
                                LostTalesNpcChatHook.speakerFaction(npc));
                    }
                });
    }

    /** Answers LOTR the way its own offer screen's buttons would. */
    private static void close(LOTREntityNPC npc, boolean accepted) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            LOTRPacketMiniquestOffer.sendClosePacket(minecraft.thePlayer, npc,
                    accepted);
        }
    }

    /**
     * What a Middle-earth giver says as they offer: the quest's own
     * written quote, put through LOTR's own formatting with LOTR's own
     * arguments — {@code #} becomes the player's name and {@code $} the
     * objective in speech — so the words are the ones LOTR's screen
     * would have shown, and the lore is not lost by being re-derived.
     *
     * <p>Empty where the quote cannot be built, which hands the offer
     * back to LOTR's screen rather than showing a thinner one. A line
     * that failed to format is never shown raw: its {@code #} and
     * {@code $} would reach the player as themselves.</p>
     */
    private static String spoken(LOTRMiniQuest quest, LOTREntityNPC npc) {
        Minecraft minecraft = Minecraft.getMinecraft();
        String quote = safe(quest.quoteStart);
        if (quote.length() == 0 || minecraft == null
                || minecraft.thePlayer == null) {
            return "";
        }
        try {
            return safe(LOTRSpeech.formatSpeech(quote, minecraft.thePlayer,
                    null, quest.getObjectiveInSpeech()));
        } catch (RuntimeException unformatted) {
            return "";
        }
    }

    /**
     * What a Middle-earth quest pays, in a line: the alignment and
     * payment its giver's faction gives, and whether they may be willing
     * to join afterwards. Built from what LOTR holds before the quest is
     * taken, which is why there are no amounts in it — LOTR itself only
     * knows those once the quest is done.
     */
    private static String rewardSummary(LOTRMiniQuest quest) {
        StringBuilder line = new StringBuilder();
        String faction = safe(quest.getFactionSubtitle());
        line.append(translate("gui.losttales.quest.reward.payment",
                faction.length() > 0 ? faction
                        : translate("gui.losttales.quest.reward.faction")));
        if (quest.willHire) {
            line.append(". ").append(
                    translate("gui.losttales.quest.reward.hire"));
        }
        return line.append('.').toString();
    }

    /**
     * The conversation touching {@code target} opens, or null where
     * there is none: a quest that is offered by this person and can be
     * taken, or one already under way whose hand-in they are waiting
     * for. Only a quest that wrote dialogue is talked about.
     */
    public static GuiScreen forEntity(Entity target) {
        if (target == null || !LostTalesQuestDialogueGui.isEnabled()) {
            return null;
        }
        for (LostTalesQuestDefinition quest
                : LostTalesClientQuestDefinitionStore.getQuests()) {
            LostTalesQuestDialogue dialogue = LostTalesQuestDialogue.of(quest);
            if (!dialogue.exists()) {
                continue;
            }
            LostTalesQuestProgress progress =
                    LostTalesClientQuestProgressStore.getActiveQuest(quest.getId());
            if (progress == null) {
                GuiScreen offered = offer(quest, dialogue, target);
                if (offered != null) {
                    return offered;
                }
                continue;
            }
            GuiScreen handIn = handIn(quest, dialogue, progress, target);
            if (handIn != null) {
                return handIn;
            }
        }
        return null;
    }

    /** The offer, when this person gives the quest and it is not taken. */
    private static GuiScreen offer(LostTalesQuestDefinition quest,
                                   LostTalesQuestDialogue dialogue,
                                   Entity target) {
        if (!dialogue.isOffered()
                || LostTalesClientQuestProgressStore.isQuestCompleted(quest.getId())
                || !matches(quest.getInteraction(), target)) {
            return null;
        }
        return conversation(quest, dialogue, target,
                QuestDialogueModel.Mood.OFFER, dialogue.line(
                        LostTalesQuestDialogue.OFFER), firstObjective(quest));
    }

    /**
     * The hand-in, when this person is waiting for what the quest asked
     * for. A quest part way through says its progress line instead, so
     * coming back early is answered rather than ignored.
     */
    private static GuiScreen handIn(LostTalesQuestDefinition quest,
                                    LostTalesQuestDialogue dialogue,
                                    LostTalesQuestProgress progress,
                                    Entity target) {
        LostTalesQuestObjectiveDefinition waiting = null;
        for (LostTalesQuestObjectiveDefinition objective
                : LostTalesQuestObjectiveSelection.getProgressibleObjectives(
                        quest, progress)) {
            if (LostTalesQuestObjectiveType.of(objective).isNpcVisit()
                    && !LostTalesQuestObjectiveSelection.isComplete(progress,
                            objective)
                    && matches(objective.getParams(), target)) {
                waiting = objective;
                break;
            }
        }
        if (waiting == null || !dialogue.isHandedIn()) {
            return null;
        }
        boolean ready = LostTalesQuestObjectiveType.DELIVER.is(waiting)
                ? carries(waiting) : true;
        String said = ready
                ? dialogue.line(LostTalesQuestDialogue.HAND_IN)
                : dialogue.lineOr(LostTalesQuestDialogue.PROGRESS,
                        dialogue.line(LostTalesQuestDialogue.HAND_IN));
        return conversation(quest, dialogue, target,
                ready ? QuestDialogueModel.Mood.HAND_IN
                        : QuestDialogueModel.Mood.PROGRESS,
                said, LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        progress, waiting, true, false, false, false));
    }

    /**
     * Whether the player is carrying everything a delivery asks for.
     * Only what is shown depends on it: the server counts again before
     * anything leaves the inventory.
     */
    private static boolean carries(LostTalesQuestObjectiveDefinition objective) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.thePlayer.inventory == null) {
            return false;
        }
        int wanted = LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective);
        int held = 0;
        for (net.minecraft.item.ItemStack stack
                : minecraft.thePlayer.inventory.mainInventory) {
            if (stack != null && stack.stackSize > 0
                    && LostTalesQuestObjectiveMatcher.matchesItem(stack,
                            objective)) {
                held += stack.stackSize;
            }
        }
        return held >= wanted;
    }

    private static GuiScreen conversation(LostTalesQuestDefinition quest,
                                          LostTalesQuestDialogue dialogue,
                                          Entity target,
                                          QuestDialogueModel.Mood mood,
                                          String said, String objective) {
        if (said.length() == 0 || !(target instanceof EntityLivingBase)) {
            return null;
        }
        QuestDialogueModel model = QuestDialogueModel.of(
                speakerName(target), "", quest.getTitle(), objective,
                mood, said,
                // Nothing written to say further leaves what the quest
                // pays as the answer, which is what a player asks for.
                dialogue.lineOr(LostTalesQuestDialogue.MORE,
                        rewardLine(quest)),
                new String[] {
                        dialogue.lineOr(LostTalesQuestDialogue.ACCEPT,
                                translate("gui.losttales.quest.dialogue.accept")),
                        translate("gui.losttales.quest.dialogue.more"),
                        dialogue.lineOr(LostTalesQuestDialogue.HAND_OVER,
                                translate("gui.losttales.quest.dialogue.handover")),
                        dialogue.lineOr(LostTalesQuestDialogue.DECLINE,
                                translate("gui.losttales.quest.dialogue.decline")),
                        dialogue.lineOr(LostTalesQuestDialogue.LEAVE,
                                translate("gui.losttales.quest.dialogue.leave"))});
        final EntityLivingBase speaker = (EntityLivingBase)target;
        final LostTalesQuestDialogueGui.Action requests =
                LostTalesQuestDialogueGui.questAction(quest.getId());
        return new LostTalesQuestDialogueGui(null, model,
                new LostTalesQuestDialogueGui.Action() {
                    @Override
                    public void accept() {
                        requests.accept();
                    }

                    @Override
                    public void handOver() {
                        requests.handOver();
                    }

                    @Override
                    public void leave() {
                        requests.leave();
                    }

                    @Override
                    public void says(String line) {
                        LostTalesNpcChatHook.sayToPlayer(speaker, line,
                                LostTalesColors.rgb(LostTalesColors.HONEY), "");
                    }
                });
    }

    /** What a quest gives, in a line; empty where it gives nothing. */
    private static String rewardLine(LostTalesQuestDefinition quest) {
        StringBuilder line = new StringBuilder();
        for (Map.Entry<String, String> reward : quest.getRewards().entrySet()) {
            if (reward.getValue() == null || reward.getValue().length() == 0) {
                continue;
            }
            if (line.length() > 0) {
                line.append(", ");
            }
            line.append(reward.getValue());
        }
        return line.length() == 0 ? ""
                : translate("gui.losttales.quest.dialogue.reward",
                        line.toString());
    }

    /** The first thing a quest asks for, for the card. */
    private static String firstObjective(LostTalesQuestDefinition quest) {
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        null, objective, false, false, false, false);
            }
        }
        return "";
    }

    /** Whether a block of parameters names this entity. */
    private static boolean matches(Map<String, String> params, Entity target) {
        if (params == null || params.isEmpty()) {
            return false;
        }
        String selector = first(params, "entity", "entityId", "npc", "target");
        return selector.length() > 0
                && LostTalesQuestObjectiveMatcher.matchesEntity(target,
                        selector, "");
    }

    private static String first(Map<String, String> params, String... keys) {
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    private static String speakerName(Entity target) {
        String name = target instanceof EntityLivingBase
                ? ((EntityLivingBase)target).getCommandSenderName() : "";
        return name == null || name.length() == 0
                ? translate("gui.losttales.quest.giver.unknown") : name;
    }

    private static String translate(String key) {
        return StatCollector.translateToLocal(key);
    }

    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
