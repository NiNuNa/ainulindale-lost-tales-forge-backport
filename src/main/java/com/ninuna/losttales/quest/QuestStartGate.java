package com.ninuna.losttales.quest;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesQuestOfferPacket;
import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveNbt;
import com.ninuna.losttales.quest.missive.LostTalesMissiveQuestFactory;
import com.ninuna.losttales.quest.missive.MissiveSeals;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * The one way a quest starts, on the server. Every start comes here with
 * what proves it ({@link QuestStartProof}): an operator's command, the
 * item in the player's hand, the giver touched or spoken to, a quest
 * card, a sealed letter. The gate looks at the world again for each, then
 * judges the start by {@link QuestStartRules} and the quest's
 * prerequisites, and only then begins the quest.
 *
 * <p>A quest's giver is whoever its {@code interaction} names, in the
 * dimension it names, or a creature whose own data names the quest
 * ({@link #TAG_QUEST_ID}).</p>
 */
public final class QuestStartGate {
    /** Where an item or a creature names the quest it starts. */
    public static final String TAG_QUEST_ID = "LostTalesQuestId";
    /** Whether a starter item is used up as its quest starts. */
    public static final String TAG_CONSUME = "LostTalesQuestConsume";

    /**
     * How far a giver may stand: a little past an ordinary interaction,
     * so turning on the spot while a conversation is open does not end it.
     */
    static final double GIVER_REACH = 6.0D;
    /** How far a touched block may stand, as far as the game lets a block be used. */
    private static final double BLOCK_REACH = 8.0D;

    private QuestStartGate() {}

    /** What a start came to. */
    public enum StartResult {
        STARTED,
        /** A touch on the giver of a quest offered in conversation, answered with the offer. */
        OFFERED,
        UNKNOWN_QUEST,
        NO_PLAYER_DATA,
        ALREADY_ACTIVE,
        ALREADY_COMPLETED,
        RESTART_NOT_ALLOWED,
        START_NOT_ALLOWED,
        /** What the start came with proves nothing: no giver in sight, no seal, no card. */
        UNPROVEN,
        REQUIREMENTS_NOT_MET
    }

    /**
     * Starts {@code questId} for the player, if what {@code proof} brings
     * holds and every rule lets it. Each refusal but a missing proof is
     * said to the player; a card that proves nothing says the card is no
     * longer there to join.
     */
    public static StartResult start(EntityPlayerMP player, String questId,
                                    QuestStartProof proof) {
        if (player == null || proof == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return StartResult.START_NOT_ALLOWED;
        }
        String id = questId == null ? "" : questId.trim();
        if (!LostTalesQuestIds.fits(id)) {
            // Nothing past the id bound is named back, an item's data
            // least of all.
            return StartResult.UNKNOWN_QUEST;
        }
        QuestStartProof.Way way = proof.getWay();
        LostTalesMissiveData missive = way == QuestStartProof.Way.LETTER
                ? missiveOn(proof.getStack(), id) : null;
        LostTalesQuestDefinition quest;
        if (way == QuestStartProof.Way.LETTER) {
            quest = missive == null ? null
                    : LostTalesMissiveQuestFactory.createQuestDefinition(missive);
        } else {
            quest = LostTalesQuestRegistry.getQuest(id);
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        Entity giver = quest == null ? null : giverFor(player, quest, proof);
        boolean proven = quest != null && proves(player, quest, proof, giver);
        QuestStartRules.Verdict verdict = QuestStartRules.judge(quest, data,
                way, proven, LostTalesConfig.allowQuestItemStarts,
                LostTalesConfig.allowQuestInteractionStarts);
        switch (verdict) {
            case UNKNOWN_QUEST:
                say(player, "chat.losttales.quest.unknown", id);
                return StartResult.UNKNOWN_QUEST;
            case WORLD_QUEST:
                say(player, "chat.losttales.quest.world.not_personal",
                        LostTalesQuestWords.titleComponent(quest));
                return StartResult.START_NOT_ALLOWED;
            case NO_PLAYER_DATA:
                say(player, "chat.losttales.quest.no_data");
                return StartResult.NO_PLAYER_DATA;
            case UNPROVEN:
                if (way == QuestStartProof.Way.CARD) {
                    say(player, "chat.losttales.quest.join_unavailable");
                }
                return StartResult.UNPROVEN;
            case WRONG_START:
                say(player, "chat.losttales.quest.wrong_start");
                return StartResult.START_NOT_ALLOWED;
            case OFFER:
                return offer(player, quest, giver)
                        ? StartResult.OFFERED : StartResult.START_NOT_ALLOWED;
            default:
                break;
        }
        // The giver is marked on the map where the player met them,
        // whether or not the quest starts.
        boolean marked = markGiver(player, quest, proof, giver);
        StartResult result = verdict == QuestStartRules.Verdict.ADMIT
                ? admit(player, quest, data, proof, missive)
                : refuse(player, quest, verdict);
        if (marked && result != StartResult.STARTED) {
            LostTalesQuestManager.syncToClient(player);
        }
        return result;
    }

    /**
     * The player touched a creature. Somebody a quest sent them to is
     * answered first, a word or a delivery owed to them, whatever the
     * server allows for starts. Then each quest this creature gives and
     * the player may start by touching it starts, or is offered where the
     * quest is offered in conversation, the first that starts, is offered
     * or is already under way ending the search. Nothing is offered while
     * this creature waits for a hand-in the player's client talks about.
     * Answers whether any quest answered the touch.
     */
    public static boolean touchedCreature(EntityPlayerMP player, Entity target) {
        if (player == null || target == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return false;
        }
        boolean answered = LostTalesQuestManager.handleTalkedTo(player, target);
        if (!LostTalesConfig.allowQuestInteractionStarts) {
            return answered;
        }
        boolean awaitsHandIn = awaitsHandIn(player, target);
        for (LostTalesQuestDefinition quest : questsOf(target)) {
            if (!quest.canStartFromInteraction() || quest.isWorldQuest()
                    || !isGiver(quest, target, player)
                    || awaitsHandIn
                            && LostTalesQuestDialogue.of(quest).isOffered()) {
                continue;
            }
            StartResult result = start(player, quest.getId(),
                    QuestStartProof.touch(target));
            if (result == StartResult.OFFERED || ends(result)) {
                return true;
            }
        }
        return answered;
    }

    /**
     * The player touched the block at {@code x}, {@code y}, {@code z}:
     * each quest whose interaction names it there starts, the first that
     * starts or is already under way ending the search. A block offers
     * nothing; a quest offered in conversation never starts from one.
     */
    public static boolean touchedBlock(EntityPlayerMP player, int x, int y,
                                       int z) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote
                || !LostTalesConfig.allowQuestInteractionStarts) {
            return false;
        }
        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
            if (!quest.canStartFromInteraction() || quest.isWorldQuest()
                    || LostTalesQuestParams.value(quest.getInteraction(),
                            "block").length() == 0
                    || LostTalesQuestDialogue.of(quest).isOffered()
                    || !isBlockGiver(quest, player, x, y, z)) {
                continue;
            }
            if (ends(start(player, quest.getId(),
                    QuestStartProof.touch(x, y, z)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a giver may offer {@code quest} to the player now: a quest
     * offered in conversation that the player could take there this
     * moment, its prerequisites met. Nothing is said.
     */
    public static boolean mayOffer(EntityPlayerMP player,
                                   LostTalesQuestDefinition quest) {
        if (player == null || quest == null
                || !LostTalesQuestDialogue.of(quest).isOffered()) {
            return false;
        }
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        return QuestStartRules.judge(quest, data,
                QuestStartProof.Way.CONVERSATION, true,
                LostTalesConfig.allowQuestItemStarts,
                LostTalesConfig.allowQuestInteractionStarts)
                == QuestStartRules.Verdict.ADMIT
                && (!LostTalesConfig.enableQuestPrerequisites
                        || LostTalesQuestPrerequisiteHelper.refusalOf(
                                quest, player, data) == null);
    }

    /**
     * Whether {@code entity} gives {@code quest}: its own data names the
     * quest, or the quest's interaction names its kind, in the dimension
     * the interaction names when it names one.
     */
    public static boolean isGiver(LostTalesQuestDefinition quest, Entity entity,
                                  EntityPlayerMP player) {
        if (quest == null || entity == null || player == null) {
            return false;
        }
        if (quest.getId().equals(questIdOf(entity))) {
            return true;
        }
        Map<String, String> interaction = quest.getInteraction();
        return LostTalesQuestParams.value(interaction, "entity").length() > 0
                && inDimension(player, interaction)
                && LostTalesQuestObjectiveMatcher.matchesEntity(entity,
                        interaction);
    }

    /**
     * The quest's giver within {@link #GIVER_REACH} of the player and in
     * their sight, or null: never a person the quest sends the player to
     * later.
     */
    public static Entity giverInSight(EntityPlayerMP player,
                                      LostTalesQuestDefinition quest) {
        if (player == null || quest == null || player.worldObj == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Entity> near = player.worldObj.getEntitiesWithinAABBExcludingEntity(
                player, player.boundingBox.expand(GIVER_REACH, GIVER_REACH,
                        GIVER_REACH));
        for (Entity candidate : near) {
            if (isGiver(quest, candidate, player)
                    && withinReach(player, candidate)
                    && player.canEntityBeSeen(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * The giver offers the quest, where it may: the player's client is
     * sent the quest where it may not know it yet ({@link ServerQuestSync}),
     * then the offer, which opens the conversation. Answers whether it was
     * offered.
     */
    private static boolean offer(EntityPlayerMP player,
                                 LostTalesQuestDefinition quest, Entity giver) {
        if (giver == null || !mayOffer(player, quest)) {
            return false;
        }
        ServerQuestSync.reveal(player, Collections.singleton(quest.getId()));
        LostTalesNetworkHandler.CHANNEL.sendTo(new LostTalesQuestOfferPacket(
                giver.getEntityId(), quest.getId()), player);
        return true;
    }

    /**
     * Whether touching {@code target} opens a hand-in on the player's
     * side: a quest of theirs with a written hand-in waits on this
     * person. The conversation then is about that quest, not an offer.
     */
    private static boolean awaitsHandIn(EntityPlayerMP player, Entity target) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null) {
            return false;
        }
        for (LostTalesQuestProgress progress : data.getActiveQuests()) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(progress.getQuestId());
            if (quest == null || !LostTalesQuestDialogue.of(quest).isHandedIn()) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective
                    : LostTalesQuestObjectiveSelection.getProgressibleObjectives(
                            quest, progress)) {
                if (LostTalesQuestObjectiveType.of(objective).isNpcVisit()
                        && !LostTalesQuestObjectiveSelection.isComplete(
                                progress, objective)
                        && LostTalesQuestObjectiveMatcher.matchesEntity(target,
                                objective)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a start's outcome ends a touch's search: it started, or the player is or was on it. */
    private static boolean ends(StartResult result) {
        return result == StartResult.STARTED
                || result == StartResult.ALREADY_ACTIVE
                || result == StartResult.ALREADY_COMPLETED;
    }

    /** The quest a creature's data names first, then every quest the server knows. */
    private static List<LostTalesQuestDefinition> questsOf(Entity target) {
        List<LostTalesQuestDefinition> quests =
                new ArrayList<LostTalesQuestDefinition>();
        String named = questIdOf(target);
        LostTalesQuestDefinition own = named.length() == 0 ? null
                : LostTalesQuestRegistry.getQuest(named);
        if (own != null) {
            quests.add(own);
        }
        for (LostTalesQuestDefinition quest : LostTalesQuestRegistry.getQuests()) {
            if (quest != own) {
                quests.add(quest);
            }
        }
        return quests;
    }

    /** The giver a touch or a conversation found, or null for the other ways. */
    private static Entity giverFor(EntityPlayerMP player,
                                   LostTalesQuestDefinition quest,
                                   QuestStartProof proof) {
        if (proof.getWay() == QuestStartProof.Way.CONVERSATION) {
            return giverInSight(player, quest);
        }
        Entity touched = proof.getWay() == QuestStartProof.Way.TOUCH
                ? proof.getCreature() : null;
        return touched != null && touched.worldObj == player.worldObj
                && !touched.isDead && withinReach(player, touched)
                && player.canEntityBeSeen(touched)
                && isGiver(quest, touched, player) ? touched : null;
    }

    /** Whether what the start brings holds, looked at again in the world. */
    private static boolean proves(EntityPlayerMP player,
                                  LostTalesQuestDefinition quest,
                                  QuestStartProof proof, Entity giver) {
        switch (proof.getWay()) {
            case COMMAND:
                return true;
            case ITEM:
                ItemStack held = player.inventory == null ? null
                        : player.inventory.getCurrentItem();
                return held != null && held == proof.getStack()
                        && quest.getId().equals(questIdOf(held));
            case TOUCH:
                return proof.isBlock()
                        ? player.getDistanceSq(proof.getX() + 0.5D,
                                proof.getY() + 0.5D, proof.getZ() + 0.5D)
                                <= BLOCK_REACH * BLOCK_REACH
                                && isBlockGiver(quest, player, proof.getX(),
                                        proof.getY(), proof.getZ())
                        : giver != null;
            case CONVERSATION:
                return giver != null;
            case CARD:
                EntityPlayerMP author =
                        LostTalesServerPlayers.findOnline(proof.getAuthor());
                LostTalesQuestPlayerData authorData = author == null ? null
                        : LostTalesQuestPlayerData.get(author);
                return QuestStartRules.cardHolds(player.getUniqueID(),
                        proof.getAuthor(), authorData != null
                                && authorData.isQuestActive(quest.getId()));
            case LETTER:
                return MissiveSeals.isGenuine(player.worldObj, proof.getStack());
            default:
                return false;
        }
    }

    /** The player may take the quest: its prerequisites, then the quest begins. */
    private static StartResult admit(EntityPlayerMP player,
                                     LostTalesQuestDefinition quest,
                                     LostTalesQuestPlayerData data,
                                     QuestStartProof proof,
                                     LostTalesMissiveData missive) {
        if (LostTalesConfig.enableQuestPrerequisites) {
            IChatComponent refusal = LostTalesQuestPrerequisiteHelper.refusalOf(
                    quest, player, data);
            if (refusal != null) {
                player.addChatMessage(refusal);
                return StartResult.REQUIREMENTS_NOT_MET;
            }
        }
        if (missive != null) {
            return beginMissive(player, quest, data, missive.getTimeLimitTicks());
        }
        LostTalesQuestManager.begin(player, quest, 0L);
        if (proof.getWay() == QuestStartProof.Way.ITEM) {
            useUp(player, proof.getStack());
        }
        return StartResult.STARTED;
    }

    /**
     * Registers a letter's quest, keeps it with the log and begins it.
     * Refused while the log keeps as many missives as it holds.
     */
    private static StartResult beginMissive(EntityPlayerMP player,
                                            LostTalesQuestDefinition quest,
                                            LostTalesQuestPlayerData data,
                                            long timeLimitTicks) {
        StartResult result;
        if (!LostTalesQuestRegistry.registerRuntimeQuest(quest)) {
            say(player, "chat.losttales.quest.unknown", quest.getId());
            result = StartResult.UNKNOWN_QUEST;
        } else if (!data.rememberDynamicQuestDefinition(quest)) {
            say(player, "chat.losttales.quest.missives_full");
            result = StartResult.START_NOT_ALLOWED;
        } else {
            LostTalesQuestManager.begin(player, quest, Math.max(0L, timeLimitTicks));
            result = StartResult.STARTED;
        }
        // The log may have forgotten older missives to make room, and a
        // quest that did not start is held by nobody.
        LostTalesQuestManager.forgetUnheldMissives(null);
        return result;
    }

    private static StartResult refuse(EntityPlayerMP player,
                                      LostTalesQuestDefinition quest,
                                      QuestStartRules.Verdict verdict) {
        if (verdict == QuestStartRules.Verdict.ALREADY_ACTIVE) {
            say(player, "chat.losttales.quest.already_active",
                    LostTalesQuestWords.titleComponent(quest));
            return StartResult.ALREADY_ACTIVE;
        }
        if (verdict == QuestStartRules.Verdict.ALREADY_COMPLETED) {
            say(player, "chat.losttales.quest.already_completed",
                    LostTalesQuestWords.titleComponent(quest));
            return StartResult.ALREADY_COMPLETED;
        }
        say(player, "chat.losttales.quest.not_restartable",
                LostTalesQuestWords.titleComponent(quest));
        return StartResult.RESTART_NOT_ALLOWED;
    }

    /** Marks the giver a touch or a conversation found; answers whether the log changed. */
    private static boolean markGiver(EntityPlayerMP player,
                                     LostTalesQuestDefinition quest,
                                     QuestStartProof proof, Entity giver) {
        if (giver != null) {
            return LostTalesQuestManager.revealQuestGiverMarker(player, quest,
                    giver, false);
        }
        if (proof.getWay() == QuestStartProof.Way.TOUCH && proof.isBlock()) {
            return LostTalesQuestManager.revealQuestGiverMarker(player, quest,
                    player.worldObj.getBlock(proof.getX(), proof.getY(),
                            proof.getZ()),
                    proof.getX(), proof.getY(), proof.getZ(), false);
        }
        return false;
    }

    /** A starter item that says so is used up, outside creative mode. */
    private static void useUp(EntityPlayerMP player, ItemStack held) {
        if (held == null || player.capabilities.isCreativeMode
                || !held.hasTagCompound()
                || !held.getTagCompound().getBoolean(TAG_CONSUME)) {
            return;
        }
        held.stackSize--;
        if (held.stackSize <= 0) {
            player.inventory.setInventorySlotContents(
                    player.inventory.currentItem, null);
        }
        player.inventory.markDirty();
    }

    /** The letter's missive where it is the one {@code questId} names; null for any other. */
    private static LostTalesMissiveData missiveOn(ItemStack letter,
                                                  String questId) {
        LostTalesMissiveData missive = letter == null ? null
                : LostTalesMissiveNbt.readFromItemStack(letter);
        return missive != null && missive.isValid()
                && questId.equals(missive.getQuestId()) ? missive : null;
    }

    /** The quest an item's or a creature's data names; empty for none or one past the id bound. */
    private static String questIdOf(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return named(tag);
    }

    private static String questIdOf(Entity entity) {
        return named(entity == null ? null : entity.getEntityData());
    }

    private static String named(NBTTagCompound tag) {
        String questId = tag == null ? "" : tag.getString(TAG_QUEST_ID).trim();
        return LostTalesQuestIds.fits(questId) ? questId : "";
    }

    private static boolean withinReach(EntityPlayerMP player, Entity entity) {
        return player.getDistanceSqToEntity(entity) <= GIVER_REACH * GIVER_REACH;
    }

    /**
     * Whether the block standing at the place is one the quest's
     * interaction names: its kind, its {@code meta}, its dimension, and
     * the player within {@code radius} of the {@code x}, {@code y},
     * {@code z} it names.
     */
    private static boolean isBlockGiver(LostTalesQuestDefinition quest,
                                        EntityPlayerMP player, int x, int y,
                                        int z) {
        Map<String, String> interaction = quest.getInteraction();
        String blockSpec = LostTalesQuestParams.value(interaction, "block");
        if (blockSpec.length() == 0 || !inDimension(player, interaction)) {
            return false;
        }
        Block block = player.worldObj.getBlock(x, y, z);
        return block != null && matchesBlock(block, blockSpec)
                && matchesMetadata(player.worldObj.getBlockMetadata(x, y, z),
                        interaction)
                && matchesLocation(player, x, y, z, interaction);
    }

    private static boolean inDimension(EntityPlayerMP player,
                                       Map<String, String> interaction) {
        String dimension = LostTalesQuestParams.value(interaction, "dimension");
        if (dimension.length() == 0) {
            return true;
        }
        int here = player.worldObj.provider.dimensionId;
        return LostTalesDimensionHelper.parseDimensionId(dimension, here) == here;
    }

    private static boolean matchesBlock(Block block, String blockSpec) {
        Object blockNameObject = Block.blockRegistry.getNameForObject(block);
        String blockName = blockNameObject == null ? ""
                : LostTalesQuestObjectiveMatcher.normalizeResourceId(
                        blockNameObject.toString());
        for (String entry : blockSpec.split(",")) {
            String normalized = LostTalesQuestObjectiveMatcher.normalizeResourceId(entry);
            if (normalized.length() == 0) {
                continue;
            }
            Object registered = Block.blockRegistry.getObject(normalized);
            if (registered == block || normalized.equals(blockName)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the block's metadata is one {@code meta} lists; any, where it lists none. */
    private static boolean matchesMetadata(int metadata,
                                           Map<String, String> interaction) {
        String metaText = LostTalesQuestParams.value(interaction, "meta");
        if (metaText.length() == 0) {
            return true;
        }
        for (String entry : metaText.split(",")) {
            if (metadata == LostTalesQuestParams.parseInt(entry, -1)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player stands within the interaction's {@code radius} (1.5 blocks) of the place it names; anywhere, where it names none. */
    private static boolean matchesLocation(EntityPlayerMP player, int blockX,
                                           int blockY, int blockZ,
                                           Map<String, String> interaction) {
        String xText = LostTalesQuestParams.value(interaction, "x");
        String yText = LostTalesQuestParams.value(interaction, "y");
        String zText = LostTalesQuestParams.value(interaction, "z");
        if (xText.length() == 0 && yText.length() == 0 && zText.length() == 0) {
            return true;
        }
        double targetX = LostTalesQuestParams.parseDouble(xText, blockX) + 0.5D;
        double targetY = LostTalesQuestParams.parseDouble(yText, blockY) + 0.5D;
        double targetZ = LostTalesQuestParams.parseDouble(zText, blockZ) + 0.5D;
        double radius = Math.max(0.5D, LostTalesQuestParams.parseDouble(
                LostTalesQuestParams.value(interaction, "radius"), 1.5D));
        double dx = player.posX - targetX;
        double dy = player.posY - targetY;
        double dz = player.posZ - targetZ;
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    private static void say(EntityPlayerMP player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }
}
