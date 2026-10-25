package com.ninuna.losttales.command;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.ArmourWords;
import com.ninuna.losttales.armour.NamedSet;
import com.ninuna.losttales.armour.Relics;
import com.ninuna.losttales.permission.LostTalesCapability;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * Hands out relic pieces (CONTENT-11): {@code <player> <relic>} gives all
 * four pieces of a relic set, {@code <player> <relic> <slot>} one, each
 * the first item its slot takes that the game has, marked. A piece that
 * finds no room drops at the player's feet. A relic exists as often as
 * it is handed out.
 */
public final class LostTalesCommandRelic extends LostTalesCommandBase {

    static final String SAY = "chat.losttales.command.relic.";

    public LostTalesCommandRelic() {
        super("relic");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/losttales relic <player> <relic> [helmet|chestplate|leggings|boots]";
    }

    @Override
    public LostTalesCapability getCapability() {
        return LostTalesCapability.CHARACTER_ADMIN;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args == null || args.length < 2 || args.length > 3 || sender.getEntityWorld() == null
                || sender.getEntityWorld().isRemote) {
            usage(sender, getCommandUsage(sender));
            return;
        }
        EntityPlayerMP player = LostTalesCommandSubject.inPerson(sender, args[0]);
        if (player == null) {
            return;
        }
        NamedSet set = Armour.serverCatalogue().set(args[1]);
        if (set == null || !set.isRelic()) {
            say(sender, EnumChatFormatting.RED, SAY + "unknown", args[1]);
            return;
        }
        String written = args.length == 3 ? set.getId() + "/" + args[2] : set.getId();
        if (args.length == 3 && ArmourSlot.fromWord(args[2]) == null) {
            say(sender, EnumChatFormatting.RED, SAY + "slot", args[2]);
            return;
        }
        int given = 0;
        for (Relics.Piece piece : Relics.pieces(Armour.serverCatalogue(), written, Relics.GAME)) {
            ItemStack stack = Relics.make(piece.getItemId(), set.getId());
            if (stack == null) {
                continue;
            }
            if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
            given++;
        }
        if (given == 0) {
            say(sender, EnumChatFormatting.RED, SAY + "no_item", ArmourWords.setName(set));
            return;
        }
        player.inventory.markDirty();
        say(sender, EnumChatFormatting.GREEN, SAY + "done", player.getCommandSenderName(),
                Integer.valueOf(given), ArmourWords.setName(set));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null) {
            return null;
        }
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, LostTalesCommandSubject.names(sender));
        }
        if (args.length == 2) {
            List<String> ids = new ArrayList<String>();
            for (NamedSet set : Armour.serverCatalogue().getSets()) {
                if (set.isRelic()) {
                    ids.add(set.getId());
                }
            }
            return getListOfStringsMatchingLastWord(args, ids.toArray(new String[ids.size()]));
        }
        if (args.length == 3) {
            return getListOfStringsMatchingLastWord(args, "helmet", "chestplate", "leggings",
                    "boots");
        }
        return null;
    }
}
