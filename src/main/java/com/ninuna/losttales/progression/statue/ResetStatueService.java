package com.ninuna.losttales.progression.statue;

import com.ninuna.losttales.block.custom.LostTalesBlockResetStatue;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.ProgressionStatuePacket;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionPlayerData;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillWords;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.quest.LostTalesQuestItemSpec;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * The statue where a character resets one skill's perks and gets its
 * points back (PROG-12), for a price the server sets: coins that double
 * with each reset the character makes, and an item if the server names
 * one. Nothing else offers a reset. The server checks the statue, the
 * player's reach, the played character, the perks and the price itself.
 */
public final class ResetStatueService {
    /** How near a player stands to use a statue, in blocks. */
    public static final double REACH = 8.0D;

    private ResetStatueService() {}

    /** The player used a statue: its page opens, with the price of their next reset. */
    public static void open(EntityPlayerMP player, int x, int y, int z) {
        if (!isStatue(player.worldObj, x, y, z) || !near(player, x, y, z)) {
            return;
        }
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        int resets = data == null ? 0 : data.state().getResets();
        LostTalesNetworkHandler.CHANNEL.sendTo(new ProgressionStatuePacket(x, y, z,
                price(resets), item().isEmpty() ? "" : LostTalesConfig.statueItem.trim()), player);
    }

    /** Resets one skill's perks at the statue the player stands by, if they pay its price. */
    public static void reset(EntityPlayerMP player, int x, int y, int z, String skillId) {
        if (!isStatue(player.worldObj, x, y, z) || !near(player, x, y, z)) {
            refuse(player, "far");
            return;
        }
        ProgressionPlayerData data = ProgressionPlayerData.get(player);
        if (data == null || data.isReadOnly() || ProgressionService.playedCharacter(player) == null) {
            refuse(player, "unavailable");
            return;
        }
        ProgressionState state = data.state();
        int ranks = state.ranksIn(skillId);
        SkillDefinition skill = ProgressionCatalog.skill(skillId);
        if (ranks == 0) {
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.statue.refused.no_perks",
                    skill == null ? skillId : SkillWords.name(skill)));
            return;
        }
        int coins = price(state.getResets());
        int carried = LotrPerkHooks.coinsCarried(player);
        if (carried < coins) {
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.statue.refused.coins", Integer.valueOf(coins),
                    Integer.valueOf(carried)));
            return;
        }
        LostTalesQuestItemSpec item = item();
        int slot = item.isEmpty() ? -1 : slotOf(player, item);
        if (!item.isEmpty() && slot < 0) {
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.statue.refused.item", itemName(item)));
            return;
        }
        if (slot >= 0) {
            player.inventory.decrStackSize(slot, item.getCount());
        }
        LotrPerkHooks.takeCoins(player, coins);
        state.clearPerks(skillId);
        state.setResets(Math.min(ProgressionState.MAX_RESETS, state.getResets() + 1));
        data.replace(state);
        player.inventoryContainer.detectAndSendChanges();
        player.addChatMessage(new ChatComponentTranslation("chat.losttales.statue.reset.done",
                skill == null ? skillId : SkillWords.name(skill), Integer.valueOf(ranks),
                Integer.valueOf(coins)));
        open(player, x, y, z);
    }

    /** The coins the next reset costs after {@code resets}, by the server's settings. */
    public static int price(int resets) {
        return ResetPrice.of(resets, LostTalesConfig.statueFirstPrice,
                LostTalesConfig.statuePriceFactor);
    }

    /** The item each reset also takes, as the server names it; empty for none. */
    static LostTalesQuestItemSpec item() {
        String written = LostTalesConfig.statueItem == null ? "" : LostTalesConfig.statueItem.trim();
        LostTalesQuestItemSpec item = LostTalesQuestItemSpec.parse(written, 1, 0);
        return item.item() == null ? LostTalesQuestItemSpec.parse("", 1, 0) : item;
    }

    private static int slotOf(EntityPlayerMP player, LostTalesQuestItemSpec item) {
        Item wanted = item.item();
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            if (stack != null && stack.getItem() == wanted && stack.stackSize >= item.getCount()
                    && (item.getMeta() < 0 || stack.getItemDamage() == item.getMeta())) {
                return slot;
            }
        }
        return -1;
    }

    private static Object itemName(LostTalesQuestItemSpec item) {
        Item wanted = item.item();
        return wanted == null ? item.getItemId()
                : new ChatComponentTranslation(new ItemStack(wanted, 1,
                        Math.max(0, item.getMeta())).getUnlocalizedName() + ".name");
    }

    /** Whether the block at the place is a reset statue, either half. */
    static boolean isStatue(World world, int x, int y, int z) {
        return world != null && world.getBlock(x, y, z) instanceof LostTalesBlockResetStatue;
    }

    private static boolean near(EntityPlayerMP player, int x, int y, int z) {
        return player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= REACH * REACH;
    }

    private static void refuse(EntityPlayerMP player, String why) {
        player.addChatMessage(new ChatComponentTranslation("chat.losttales.statue.refused." + why));
    }
}
