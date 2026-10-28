package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.client.chat.LostTalesNpcChatHook;
import com.ninuna.losttales.compat.lotr.LotrMiniquestOfferAccess;
import com.ninuna.losttales.gui.screen.LostTalesQuestConversationGui;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.screen.quest.QuestConversationModel;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestWords;
import com.ninuna.losttales.quest.LostTalesQuestConversation;
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
import com.ninuna.losttales.quest.LostTalesQuestRewardText;
import com.ninuna.losttales.quest.LostTalesQuestItemSpec;
import com.ninuna.losttales.quest.LostTalesQuestRules;
import com.ninuna.losttales.quest.QuestChatAnswer;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestActionPacket;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import com.ninuna.losttales.quest.LostTalesQuestRewards;

/**
 * Where a quest conversation is opened from.
 *
 * <p>Three ways in: LOTR opening its own offer screen, which is read and
 * shown in this mod's dress instead; a Lost Tales giver's offer, which
 * the server sends as the player touches the giver ({@link #offerFrom});
 * and touching somebody a running quest waits on for its hand-in, which
 * the client answers from the definitions and the progress it already
 * holds. None decides anything the server owns — a reply is a request,
 * and the server checks the whole of it again.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesQuestConversationHooks {

    private LostTalesQuestConversationHooks() {}

    /**
     * The conversation to show in place of LOTR's own offer screen, or
     * null to leave LOTR's alone — which is what happens when the
     * conversation is switched off or the offer cannot be read.
     */
    public static GuiScreen replaceLotrOffer(LOTRGuiMiniquestOffer offer) {
        if (offer == null || !LostTalesQuestConversationGui.isEnabled()
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
        QuestConversationModel model = QuestConversationModel.of(
                npc.getNPCName(), safe(quest.getFactionSubtitle()),
                safe(quest.getQuestObjective()), "",
                QuestConversationModel.Mood.OFFER, said,
                // What the quest pays is the one thing LOTR holds and
                // does not show at the offer, so it is what asking for
                // more is worth. Nothing is invented.
                rewardSummary(quest),
                new String[] {
                        translate("gui.losttales.quest.conversation.accept"),
                        translate("gui.losttales.quest.conversation.more"),
                        "",
                        translate("gui.losttales.quest.conversation.decline"),
                        translate("gui.losttales.quest.conversation.leave")});
        return new LostTalesQuestConversationGui(null, model,
                new LostTalesQuestConversationGui.Action() {
                    @Override
                    public void accept() {
                        close(npc, true);
                    }

                    @Override
                    public void leave() {
                        close(npc, false);
                    }

                    @Override
                    public void handOver(int choice) {
                        close(npc, false);
                    }

                    @Override
                    public void choose(int choice) {
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
     * The hand-in conversation touching {@code target} opens, or null
     * where there is none, or while the conversation screen is switched
     * off.
     */
    public static GuiScreen forEntity(Entity target) {
        if (target == null || !LostTalesQuestConversationGui.isEnabled()) {
            return null;
        }
        Exchange exchange = handInWith(target);
        return exchange == null ? null : conversation(exchange);
    }

    /**
     * Touching a person with the conversation screen switched off: their
     * words go to the chat, and a ready hand-in is given over at once, as
     * the screen's first answer would, or, where the quest offers a pick,
     * the line ends on its items to click (QUEST-37). A progress line is
     * only said. Answers whether the person had anything to say.
     */
    public static boolean answerWithoutScreen(Entity target) {
        if (target == null || LostTalesQuestConversationGui.isEnabled()) {
            return false;
        }
        Exchange exchange = handInWith(target);
        if (exchange == null) {
            return false;
        }
        answer(exchange);
        return true;
    }

    /**
     * A giver's offer, sent by the server as the player touched them: the
     * conversation opens on it, or the giver's line goes to the chat with
     * *Accept* and *Decline* to click, where the conversation screen is
     * switched off or another screen is open, which stays open
     * ({@link QuestOfferWay}, QUEST-37, QUEST-41). An offer whose speaker or quest
     * this client cannot find is let go.
     */
    public static void offerFrom(int entityId, String questId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return;
        }
        boolean onScreen = QuestOfferWay.of(LostTalesQuestConversationGui.isEnabled(),
                minecraft.currentScreen != null) == QuestOfferWay.CONVERSATION;
        Entity speaker = minecraft.theWorld.getEntityByID(entityId);
        LostTalesQuestDefinition quest =
                LostTalesClientQuestDefinitionStore.getQuest(questId);
        if (speaker == null || quest == null) {
            return;
        }
        LostTalesQuestConversation conversation = LostTalesQuestConversation.of(quest);
        if (!conversation.isOffered()) {
            return;
        }
        Exchange exchange = exchange(quest, conversation, speaker,
                QuestConversationModel.Mood.OFFER,
                conversation.line(LostTalesQuestConversation.OFFER),
                firstObjective(quest));
        if (exchange == null) {
            return;
        }
        if (onScreen) {
            minecraft.displayGuiScreen(conversation(exchange));
        } else {
            answer(exchange);
        }
    }

    /**
     * Somebody told the player a rumour, as the server sent it (QUEST-20):
     * their words go to the chat in their own voice, a banner says a lead
     * was heard, and the journal keeps it under Rumours.
     */
    public static void rumourFrom(int entityId, String questId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.theWorld == null) {
            return;
        }
        Entity speaker = minecraft.theWorld.getEntityByID(entityId);
        LostTalesQuestDefinition quest =
                LostTalesClientQuestDefinitionStore.getQuest(questId);
        String line = LostTalesQuestWords.rumourLine(quest);
        if (quest == null || line.length() == 0) {
            return;
        }
        if (speaker instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC) speaker;
            LostTalesNpcChatHook.sayToPlayer(npc, line,
                    LostTalesNpcChatHook.speakerColor(npc),
                    LostTalesNpcChatHook.speakerFaction(npc));
        } else if (speaker instanceof EntityLivingBase) {
            LostTalesNpcChatHook.sayToPlayer((EntityLivingBase) speaker, line,
                    LostTalesColors.rgb(LostTalesColors.HONEY), "");
        }
        String banner = translate("gui.losttales.quest.banner.rumour",
                LostTalesQuestWords.title(quest));
        LostTalesClientQuestBannerStore.addInfo(banner);
        ClientQuestNews.record(questId, banner);
    }

    /**
     * An exchange without the screen (QUEST-37): the words go to the chat.
     * An offer's line ends on *Accept* and *Decline*, which the player
     * clicks; a hand-in with a pick ends on its items; a ready hand-in
     * without one is given over at once.
     */
    private static void answer(Exchange exchange) {
        String questId = exchange.quest.getId();
        IChatComponent answers = null;
        if (exchange.mood == QuestConversationModel.Mood.OFFER) {
            answers = answers(new String[] {
                    translate("gui.losttales.quest.chat.accept"),
                    translate("gui.losttales.quest.chat.decline") },
                    new String[] {
                    QuestChatAnswer.encode(QuestChatAnswer.ACCEPT, -1, questId),
                    QuestChatAnswer.encode(QuestChatAnswer.DECLINE, -1, questId) });
        } else if (exchange.mood == QuestConversationModel.Mood.CHOICE
                || exchange.mood == QuestConversationModel.Mood.HAND_IN
                && !exchange.picks.isEmpty()) {
            String[] words = new String[exchange.picks.size()];
            String[] values = new String[words.length];
            for (int index = 0; index < words.length; index++) {
                words[index] = exchange.picks.get(index);
                values[index] = QuestChatAnswer.encode(
                        exchange.mood == QuestConversationModel.Mood.CHOICE
                                ? QuestChatAnswer.CHOOSE : QuestChatAnswer.HAND_IN,
                        index, questId);
            }
            answers = answers(words, values);
        }
        LostTalesNpcChatHook.sayToPlayer(exchange.speaker, exchange.said,
                LostTalesColors.rgb(LostTalesColors.HONEY), "", answers);
        if (exchange.mood == QuestConversationModel.Mood.HAND_IN
                && exchange.picks.isEmpty()) {
            LostTalesQuestConversationGui.questAction(questId).handOver(-1);
        }
    }

    /**
     * The words a chat line ends on to be clicked, parted by a dot: each
     * in green, but the second of two in red (*Accept*, *Decline*).
     */
    private static IChatComponent answers(String[] words, String[] values) {
        ChatComponentText line = new ChatComponentText(" ");
        for (int index = 0; index < words.length; index++) {
            if (index > 0) {
                line.appendSibling(new ChatComponentText(" \u00b7 "));
            }
            ChatComponentText word = new ChatComponentText(words[index]);
            word.getChatStyle().setColor(index == 0 ? EnumChatFormatting.GREEN
                    : words.length == 2 ? EnumChatFormatting.RED
                    : EnumChatFormatting.GREEN)
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND, values[index]));
            line.appendSibling(word);
        }
        return line;
    }

    /**
     * An answer clicked in the chat (QUEST-37): *Accept* asks for the quest
     * as the conversation's reply does, a pick's item hands the quest in
     * taking it, and *Decline* says nothing to anyone. The server checks
     * each again, the giver beside the player and in sight above all.
     */
    public static void answerFromChat(QuestChatAnswer answer) {
        if (answer == null || QuestChatAnswer.DECLINE.equals(answer.action)) {
            return;
        }
        LostTalesNetworkHandler.CHANNEL.sendToServer(new LostTalesQuestActionPacket(
                QuestChatAnswer.ACCEPT.equals(answer.action)
                        ? LostTalesQuestActionPacket.ACTION_ACCEPT
                        : QuestChatAnswer.CHOOSE.equals(answer.action)
                        ? LostTalesQuestActionPacket.ACTION_CHOOSE
                        : LostTalesQuestActionPacket.ACTION_HAND_IN,
                answer.questId, answer.choice));
    }

    /**
     * The hand-in touching {@code target} comes to, or null: a quest
     * under way whose hand-in this person waits for. Only a quest that
     * wrote conversation is talked about.
     */
    private static Exchange handInWith(Entity target) {
        for (LostTalesQuestProgress progress
                : LostTalesClientQuestProgressStore.getActiveQuests()) {
            LostTalesQuestDefinition quest =
                    LostTalesClientQuestDefinitionStore.getQuest(
                            progress.getQuestId());
            LostTalesQuestConversation conversation = LostTalesQuestConversation.of(quest);
            if (quest == null || !conversation.exists() && !progress.isChoosing()) {
                continue;
            }
            if (progress.isChoosing()) {
                Exchange choice = choiceWith(quest, conversation, progress, target);
                if (choice != null) {
                    return choice;
                }
                continue;
            }
            if (progress.isReturning()) {
                Exchange back = backAtGiver(quest, conversation, progress, target);
                if (back != null) {
                    return back;
                }
                continue;
            }
            Exchange exchange = handIn(quest, conversation, progress, target);
            if (exchange != null) {
                return exchange;
            }
        }
        return null;
    }

    /**
     * The hand-in of a quest whose stages are all done, when this person
     * is the one who gave it (QUEST-31): the giver's hand-in line, and the
     * quest's pick to choose from.
     */
    private static Exchange backAtGiver(LostTalesQuestDefinition quest,
                                        LostTalesQuestConversation conversation,
                                        LostTalesQuestProgress progress,
                                        Entity target) {
        if (!LostTalesQuestRules.returnsToGiver(quest) || !conversation.isHandedIn()
                || !isReturnGiver(quest, progress, target)) {
            return null;
        }
        Exchange exchange = exchange(quest, conversation, target,
                QuestConversationModel.Mood.HAND_IN,
                conversation.line(LostTalesQuestConversation.HAND_IN),
                LostTalesQuestWords.returnLine(quest, progress));
        if (exchange != null) {
            exchange.picks.addAll(pickLabels(quest));
        }
        return exchange;
    }

    /**
     * The choice a quest's stage waits for, when this person gave the
     * quest (QUEST-32): the stage's ask line and its replies.
     */
    private static Exchange choiceWith(LostTalesQuestDefinition quest,
                                       LostTalesQuestConversation conversation,
                                       LostTalesQuestProgress progress,
                                       Entity target) {
        LostTalesQuestStageDefinition stage = LostTalesQuestObjectiveSelection
                .getCurrentStage(quest, progress);
        if (stage == null || !isReturnGiver(quest, progress, target)) {
            return null;
        }
        Exchange exchange = exchange(quest, conversation, target,
                QuestConversationModel.Mood.CHOICE,
                LostTalesQuestWords.askLine(quest, stage),
                LostTalesQuestWords.chooseLine(quest, progress));
        if (exchange != null) {
            for (int index = 0; index < LostTalesQuestRules.choices(stage).size(); index++) {
                exchange.picks.add(LostTalesQuestWords.choiceWords(quest, stage, index));
            }
        }
        return exchange;
    }

    /** Whether the person is the one a waiting quest goes back to, as the server reads it. */
    private static boolean isReturnGiver(LostTalesQuestDefinition quest,
                                         LostTalesQuestProgress progress,
                                         Entity target) {
        if (target == null || target.isDead) {
            return false;
        }
        String giverId = progress.getGiverId();
        return giverId.length() > 0
                ? giverId.equals(target.getUniqueID().toString())
                : matches(quest.getInteraction(), target);
    }

    /** What the player says to take each item of the quest's pick (QUEST-33); empty for none. */
    private static List<String> pickLabels(LostTalesQuestDefinition quest) {
        List<String> labels = new ArrayList<String>();
        for (LostTalesQuestItemSpec item : LostTalesQuestRewards.picks(quest.getRewards())) {
            labels.add(translate("gui.losttales.quest.conversation.pick",
                    LostTalesQuestRewardText.itemPhrase(item.getItemId()
                            + "@" + item.getMeta() + "*" + item.getCount())));
        }
        return labels;
    }

    /**
     * The hand-in, when this person is waiting for what the quest asked
     * for. A quest part way through says its progress line instead, so
     * coming back early is answered rather than ignored.
     */
    private static Exchange handIn(LostTalesQuestDefinition quest,
                                   LostTalesQuestConversation conversation,
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
        if (waiting == null || !conversation.isHandedIn()) {
            return null;
        }
        boolean ready = LostTalesQuestObjectiveType.DELIVER.is(waiting)
                ? carries(waiting) : true;
        String said = ready
                ? conversation.line(LostTalesQuestConversation.HAND_IN)
                : conversation.lineOr(LostTalesQuestConversation.PROGRESS,
                        conversation.line(LostTalesQuestConversation.HAND_IN));
        return exchange(quest, conversation, target,
                ready ? QuestConversationModel.Mood.HAND_IN
                        : QuestConversationModel.Mood.PROGRESS,
                said, LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        progress, waiting, true, false));
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

    /** What a person says to the player, and to what end; null when they would say nothing. */
    private static Exchange exchange(LostTalesQuestDefinition quest,
                                     LostTalesQuestConversation conversation,
                                     Entity target,
                                     QuestConversationModel.Mood mood,
                                     String said, String objective) {
        if (said.length() == 0 || !(target instanceof EntityLivingBase)) {
            return null;
        }
        return new Exchange(quest, conversation, (EntityLivingBase)target, mood,
                said, objective);
    }

    /** One turn of a quest conversation: who speaks, what they say, and what it leads to. */
    private static final class Exchange {
        final LostTalesQuestDefinition quest;
        final LostTalesQuestConversation conversation;
        final EntityLivingBase speaker;
        final QuestConversationModel.Mood mood;
        final String said;
        final String objective;
        /** What the player says to take each item of a pick at this hand-in; empty for none. */
        final List<String> picks = new ArrayList<String>();

        Exchange(LostTalesQuestDefinition quest, LostTalesQuestConversation conversation,
                 EntityLivingBase speaker, QuestConversationModel.Mood mood,
                 String said, String objective) {
            this.quest = quest;
            this.conversation = conversation;
            this.speaker = speaker;
            this.mood = mood;
            this.said = said;
            this.objective = objective;
        }
    }

    /** The conversation screen for one exchange. */
    private static GuiScreen conversation(Exchange exchange) {
        LostTalesQuestDefinition quest = exchange.quest;
        LostTalesQuestConversation conversation = exchange.conversation;
        QuestConversationModel model = QuestConversationModel.of(
                speakerName(exchange.speaker), "", LostTalesQuestWords.title(quest),
                exchange.objective, exchange.mood, exchange.said,
                // Nothing written to say further leaves what the quest
                // pays as the answer, which is what a player asks for.
                conversation.lineOr(LostTalesQuestConversation.MORE,
                        rewardLine(quest)),
                new String[] {
                        conversation.lineOr(LostTalesQuestConversation.ACCEPT,
                                translate("gui.losttales.quest.conversation.accept")),
                        translate("gui.losttales.quest.conversation.more"),
                        conversation.lineOr(LostTalesQuestConversation.HAND_OVER,
                                translate("gui.losttales.quest.conversation.handover")),
                        conversation.lineOr(LostTalesQuestConversation.DECLINE,
                                translate("gui.losttales.quest.conversation.decline")),
                        conversation.lineOr(LostTalesQuestConversation.LEAVE,
                                translate("gui.losttales.quest.conversation.leave"))});
        if (!exchange.picks.isEmpty()) {
            model = model.withChoices(exchange.picks);
        }
        final EntityLivingBase speaker = exchange.speaker;
        final LostTalesQuestConversationGui.Action requests =
                LostTalesQuestConversationGui.questAction(quest.getId());
        return new LostTalesQuestConversationGui(null, model,
                new LostTalesQuestConversationGui.Action() {
                    @Override
                    public void accept() {
                        requests.accept();
                    }

                    @Override
                    public void handOver(int choice) {
                        requests.handOver(choice);
                    }

                    @Override
                    public void choose(int choice) {
                        requests.choose(choice);
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

    /** What a quest gives, the optional objectives' rewards too, in a line; empty where it gives nothing. */
    private static String rewardLine(LostTalesQuestDefinition quest) {
        String rewards = LostTalesQuestRewardText.summary(
                LostTalesQuestRewards.withOptionals(quest));
        return rewards.length() == 0 ? ""
                : translate("gui.losttales.quest.conversation.reward", rewards);
    }

    /** The first thing a quest asks for, for the card. */
    private static String firstObjective(LostTalesQuestDefinition quest) {
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                return LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        null, objective, false, false);
            }
        }
        return "";
    }

    /** Whether a block of parameters names this entity, as the server reads them. */
    private static boolean matches(Map<String, String> params, Entity target) {
        return LostTalesQuestObjectiveMatcher.matchesEntity(target, params);
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
