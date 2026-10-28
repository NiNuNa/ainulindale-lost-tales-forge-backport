package com.ninuna.losttales.quest;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.Relics;
import com.ninuna.losttales.compat.lotr.LotrQuestRewards;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.player.WaitingReward;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Pays a finished quest: its own rewards, the item picked from its
 * {@code pick}, and each optional objective's that was achieved.
 * Experience is the game's and alignment LOTR's, through its own call
 * (QUEST-33). Items, LOTR's coins and another quest's sealed starter
 * (QUEST-43) go into the inventory, and what finds no room waits under
 * the quest in the journal until the player collects it (QUEST-44):
 * nothing is dropped while the quest log keeps it.
 */
public final class LostTalesQuestRewardHelper {
    /** The quests a starter reward named that the server lacks, each logged once; cleared with the server's stores. */
    private static final Set<String> UNKNOWN_STARTERS = new HashSet<String>();

    private LostTalesQuestRewardHelper() {}

    /**
     * Pays the quest; {@code picked} is the index of the item taken from
     * its pick, and anything out of range takes the first.
     */
    public static boolean grantRewards(EntityPlayerMP player,
            LostTalesQuestDefinition quest,
            LostTalesQuestProgress progress, int picked) {
        if (player == null || quest == null) {
            return false;
        }

        Payout payout = new Payout(player, quest);
        boolean granted = payout.payBlock(quest.getRewards());
        List<LostTalesQuestItemSpec> picks =
                LostTalesQuestRewards.picks(quest.getRewards());
        if (!picks.isEmpty()) {
            granted |= payout.oweItem(picks.get(
                    picked >= 0 && picked < picks.size() ? picked : 0));
        }
        granted = grantOptionalRewards(payout, quest, progress, granted);
        payout.finish(granted);
        return granted;
    }

    private static boolean grantOptionalRewards(Payout payout,
            LostTalesQuestDefinition quest, LostTalesQuestProgress progress,
            boolean alreadyGranted) {
        boolean granted = alreadyGranted;
        if (quest == null || progress == null) {
            return granted;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective
                    : stage.getObjectives()) {
                if (!objective.isOptional()
                        || progress.getObjectiveProgress(objective.getId())
                        < LostTalesQuestObjectiveTextHelper
                        .getObjectiveTargetCount(objective)) {
                    continue;
                }
                granted |= payout.payBlock(
                        LostTalesQuestRewards.optionalRewards(objective));
            }
        }
        return granted;
    }

    /**
     * Pays one reward block of the quest as it is, a world quest's podium
     * among them (QUEST-36); a pick is never in one. What finds no room
     * waits under the quest. Answers whether anything was paid.
     */
    public static boolean grantRewardBlock(EntityPlayerMP player,
            LostTalesQuestDefinition quest, Map<String, String> rewards) {
        if (player == null || quest == null) {
            return false;
        }
        Payout payout = new Payout(player, quest);
        boolean granted = payout.payBlock(rewards);
        payout.finish(granted);
        return granted;
    }

    /**
     * The player asked to collect what waits under the quest (QUEST-44),
     * checked here on the server: what fits goes into the inventory, what
     * still does not fit keeps waiting, and the player is told which.
     * Nothing is collected on the death screen, nor by a machine acting
     * as a player.
     */
    public static void collect(EntityPlayerMP player, String questId) {
        LostTalesQuestPlayerData data = LostTalesQuestPlayerData.get(player);
        if (data == null || questId == null) {
            return;
        }
        List<WaitingReward> waiting = data.getWaitingRewards(questId);
        IChatComponent title = titleOf(questId);
        if (waiting.isEmpty()) {
            say(player, "chat.losttales.quest.collect.nothing", title);
            LostTalesQuestManager.syncToClient(player);
            return;
        }
        if (!mayBePaid(player) || data.isReadOnlyForNewerVersion()) {
            say(player, "chat.losttales.quest.collect.not_now", title);
            return;
        }
        List<WaitingReward> left = QuestRewardPayout.leftOver(waiting,
                new PlayerHands(player));
        long before = total(waiting);
        long after = total(left);
        data.replaceWaitingRewards(questId, left);
        if (after < before) {
            player.inventory.markDirty();
        }
        say(player, left.isEmpty() ? "chat.losttales.quest.collect.done"
                : after < before ? "chat.losttales.quest.collect.some"
                : "chat.losttales.quest.collect.full", title);
        LostTalesQuestManager.syncToClient(player);
    }

    /** Forgets the starters logged as unknown; with the server's other stores. */
    public static synchronized void clear() {
        UNKNOWN_STARTERS.clear();
    }

    /**
     * The items a reward map grants: its {@code item}, then each of its
     * {@code items}, read as {@link LostTalesQuestRewardText} names them.
     */
    static List<LostTalesQuestItemSpec> itemsGranted(Map<String, String> rewards) {
        List<LostTalesQuestItemSpec> items = new ArrayList<LostTalesQuestItemSpec>();
        LostTalesQuestItemSpec single = LostTalesQuestItemSpec.rewardItem(rewards);
        if (single != null) {
            items.add(single);
        }
        items.addAll(LostTalesQuestItemSpec.rewardItems(rewards));
        return items;
    }

    /**
     * {@code count} items as stacks of at most {@code limit} each, full
     * stacks first; a limit under one is taken as one.
     */
    static int[] stackSizes(int count, int limit) {
        int each = Math.max(1, limit);
        int total = Math.max(0, count);
        int[] sizes = new int[(total + each - 1) / each];
        for (int index = 0; index < sizes.length; index++) {
            sizes[index] = Math.min(each, total - index * each);
        }
        return sizes;
    }

    /**
     * One payment of a quest's rewards. Experience, levels, alignment and
     * skills are paid as each block is read; items, coins and starters are
     * owed, then handed over together as the payment finishes, so what
     * finds no room waits under the quest as one.
     */
    private static final class Payout {
        private final EntityPlayerMP player;
        private final LostTalesQuestDefinition quest;
        private final List<WaitingReward> owed = new ArrayList<WaitingReward>();

        Payout(EntityPlayerMP player, LostTalesQuestDefinition quest) {
            this.player = player;
            this.quest = quest;
        }

        /** Pays or owes everything one reward block names; answers whether it names anything paid. */
        boolean payBlock(Map<String, String> rewards) {
            if (rewards == null || rewards.isEmpty()) {
                return false;
            }
            boolean granted = false;

            int xp = LostTalesQuestParams.parseInt(rewards.get("experience"), 0);
            if (xp > 0) {
                this.player.addExperience(xp);
                granted = true;
            }

            int levels = LostTalesQuestParams.parseInt(rewards.get("levels"), 0);
            if (levels > 0) {
                this.player.addExperienceLevel(levels);
                granted = true;
            }

            for (LostTalesQuestItemSpec item : itemsGranted(rewards)) {
                granted |= oweItem(item);
            }

            for (String relic : LostTalesQuestRewards.relics(rewards)) {
                for (Relics.Piece piece : Relics.pieces(Armour.serverCatalogue(), relic,
                        Relics.GAME)) {
                    this.owed.add(WaitingReward.relic(piece.getItemId(),
                            piece.getSet().getId(), 1));
                    granted = true;
                }
            }

            for (LostTalesQuestRewards.Alignment alignment
                    : LostTalesQuestRewards.alignments(rewards)) {
                granted |= LotrQuestRewards.addAlignment(this.player,
                        alignment.getFactionId(), alignment.getAmount());
            }

            granted |= oweCoins(LostTalesQuestRewards.coins(rewards));

            for (LostTalesQuestRewards.SkillReward skill
                    : LostTalesQuestRewards.skills(rewards)) {
                granted |= ProgressionService.questReward(this.player,
                        skill.getSkillId(), skill.getAmount()) > 0.0D;
            }

            granted |= oweStarter(LostTalesQuestRewards.starter(rewards));
            return granted;
        }

        /** Owes the item, by the id the game registers it under; an item the game lacks pays nothing. */
        boolean oweItem(LostTalesQuestItemSpec spec) {
            Item registered = spec == null ? null : spec.item();
            if (registered == null) {
                return false;
            }
            Object name = Item.itemRegistry.getNameForObject(registered);
            this.owed.add(WaitingReward.item(name == null ? spec.getItemId()
                    : String.valueOf(name), spec.getMeta(), spec.getCount()));
            return true;
        }

        /**
         * Owes LOTR's coins worth {@code value}, counted out as LOTR counts
         * them; where LOTR's coins cannot be read so, LOTR pays them its
         * own way, dropping what does not fit.
         */
        boolean oweCoins(int value) {
            if (value <= 0) {
                return false;
            }
            String coin = LotrQuestRewards.coinItemId();
            int[] counts = coin.length() == 0 ? null
                    : LotrQuestRewards.coinCounts(value);
            if (counts == null) {
                return LotrQuestRewards.giveCoins(this.player, value);
            }
            for (int meta = counts.length - 1; meta >= 0; meta--) {
                if (counts[meta] > 0) {
                    this.owed.add(WaitingReward.item(coin, meta, counts[meta]));
                }
            }
            return true;
        }

        /**
         * Owes the sealed starter of the quest {@code questId} (QUEST-43);
         * a quest the server lacks pays nothing, logged once.
         */
        boolean oweStarter(String questId) {
            if (questId == null || questId.length() == 0) {
                return false;
            }
            if (LostTalesQuestRegistry.getQuest(questId) == null) {
                warnUnknownStarter(questId, this.quest.getId());
                return false;
            }
            this.owed.add(WaitingReward.starter(questId, 1));
            return true;
        }

        /**
         * Hands over what is owed; what finds no room waits under the quest,
         * all of it on the death screen, and only what the quest log cannot
         * keep falls at the player's feet. Then says what was paid and what
         * waits.
         */
        void finish(boolean granted) {
            List<WaitingReward> left = QuestRewardPayout.leftOver(this.owed,
                    mayBePaid(this.player) ? new PlayerHands(this.player) : NO_HANDS);
            boolean waits = false;
            if (!left.isEmpty()) {
                LostTalesQuestPlayerData data =
                        LostTalesQuestPlayerData.get(this.player);
                List<WaitingReward> unkept = data == null ? left
                        : data.addWaitingRewards(this.quest.getId(), left);
                waits = total(unkept) < total(left);
                if (!unkept.isEmpty()) {
                    LostTalesLog.warning("The quest log of %s keeps no more waiting"
                            + " rewards (%s); what %s paid that found no room fell"
                            + " at their feet: %s", this.player.getCommandSenderName(),
                            data == null ? "none" : data.isReadOnlyForNewerVersion()
                                    ? "read-only" : "full",
                            this.quest.getId(), unkept);
                    for (WaitingReward reward : unkept) {
                        drop(this.player, reward);
                    }
                }
            }
            if (granted) {
                say(this.player, "chat.losttales.quest.note.rewards");
            }
            if (waits) {
                say(this.player, "chat.losttales.quest.note.rewards_waiting",
                        LostTalesQuestWords.titleComponent(this.quest));
                LostTalesQuestManager.syncToClient(this.player);
            }
        }
    }

    /** Hands that take nothing: a dead player's rewards all wait. */
    private static final QuestRewardPayout.Hands NO_HANDS = new QuestRewardPayout.Hands() {
        @Override
        public int hand(WaitingReward reward) {
            return reward.getCount();
        }
    };

    /** The player's inventory as the payout's hands. */
    private static final class PlayerHands implements QuestRewardPayout.Hands {
        private final EntityPlayerMP player;

        PlayerHands(EntityPlayerMP player) {
            this.player = player;
        }

        @Override
        public int hand(WaitingReward reward) {
            return reward.isStarter() ? handStarters(reward) : handItems(reward);
        }

        /**
         * The item in stacks no larger than its own limit, until one finds
         * no room; an item the game lacks waits whole.
         */
        private int handItems(WaitingReward reward) {
            Item item = itemOf(reward.getId());
            if (item == null) {
                return reward.getCount();
            }
            int each = Math.max(1, new ItemStack(item, 1, reward.getMeta())
                    .getMaxStackSize());
            int left = reward.getCount();
            while (left > 0) {
                int size = Math.min(each, left);
                ItemStack stack = new ItemStack(item, size, reward.getMeta());
                Relics.mark(stack, reward.getRelic());
                this.player.inventory.addItemStackToInventory(stack);
                int notTaken = Math.max(0, Math.min(size, stack.stackSize));
                left -= size - notTaken;
                if (notTaken > 0) {
                    break;
                }
            }
            return left;
        }

        /**
         * Sealed starters, made one by one as each finds room; none while
         * the server lacks their quest or the world cannot seal.
         */
        private int handStarters(WaitingReward reward) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(reward.getId());
            if (quest == null) {
                return reward.getCount();
            }
            for (int index = 0; index < reward.getCount(); index++) {
                ItemStack stack = QuestStarters.create(this.player.worldObj, quest);
                if (stack == null) {
                    return reward.getCount() - index;
                }
                this.player.inventory.addItemStackToInventory(stack);
                if (stack.stackSize > 0) {
                    return reward.getCount() - index;
                }
            }
            return 0;
        }
    }

    /** Drops what the quest log could not keep at the player's feet: the last resort. */
    private static void drop(EntityPlayerMP player, WaitingReward reward) {
        if (reward.isStarter()) {
            LostTalesQuestDefinition quest =
                    LostTalesQuestRegistry.getQuest(reward.getId());
            for (int index = 0; quest != null && index < reward.getCount(); index++) {
                ItemStack stack = QuestStarters.create(player.worldObj, quest);
                if (stack != null) {
                    dropStack(player, stack);
                }
            }
            return;
        }
        Item item = itemOf(reward.getId());
        if (item == null) {
            return;
        }
        int limit = new ItemStack(item, 1, reward.getMeta()).getMaxStackSize();
        for (int size : stackSizes(reward.getCount(), limit)) {
            ItemStack stack = new ItemStack(item, size, reward.getMeta());
            Relics.mark(stack, reward.getRelic());
            dropStack(player, stack);
        }
    }

    private static void dropStack(EntityPlayerMP player, ItemStack stack) {
        EntityItem entityItem = player.dropPlayerItemWithRandomChoice(stack, false);
        if (entityItem != null) {
            entityItem.delayBeforeCanPickup = 0;
        }
    }

    /** The item registered under the id; null for one the game does not have. */
    private static Item itemOf(String itemId) {
        Object found = itemId == null ? null : Item.itemRegistry.getObject(itemId);
        return found instanceof Item ? (Item) found : null;
    }

    /**
     * Whether items and experience given now reach the player: not on the
     * death screen, where they would stay with the body, nor a machine
     * acting as one.
     */
    public static boolean mayBePaid(EntityPlayerMP player) {
        return !(player instanceof FakePlayer) && !player.isDead
                && player.getHealth() > 0.0F;
    }

    /** How many rewards the list holds, all kinds together. */
    private static long total(List<WaitingReward> rewards) {
        long total = 0L;
        for (WaitingReward reward : rewards) {
            total += reward.getCount();
        }
        return total;
    }

    /** The quest's title as each reader's game words it; its id for a quest the server lacks. */
    private static IChatComponent titleOf(String questId) {
        LostTalesQuestDefinition quest = LostTalesQuestRegistry.getQuest(questId);
        return quest == null ? new ChatComponentText(questId)
                : LostTalesQuestWords.titleComponent(quest);
    }

    private static synchronized void warnUnknownStarter(String starter, String paidBy) {
        if (UNKNOWN_STARTERS.add(starter)) {
            LostTalesLog.warning("The quest %s pays the starter of %s, which this server"
                    + " does not have; it pays nothing for it", paidBy, starter);
        }
    }

    private static void say(EntityPlayerMP player, String key, Object... args) {
        player.addChatMessage(new ChatComponentTranslation(key, args));
    }
}
