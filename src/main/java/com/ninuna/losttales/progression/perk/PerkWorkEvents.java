package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.compat.lotr.LotrCountHooks;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.progression.count.CountEvents;
import com.ninuna.losttales.progression.count.CountWorth;
import com.ninuna.losttales.progression.count.Thing;
import com.ninuna.losttales.progression.count.Things;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.WeaponKind;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemSmeltedEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.registry.GameData;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * The perks of work (PROG-06): drawing and loosing, the wear of tools,
 * weapons and armour, mining and felling speed, what a harvest and a
 * mine drop, smelting, forging and repairing, filling a keg and
 * shortening LOTR's fast-travel wait. A bow's draw runs alike on both
 * sides so the client draws what the server looses; the rest is the
 * server's.
 */
public final class PerkWorkEvents {
    /** What a spear thrown under Recovery carries until it comes back. */
    static final String KEEP_WEAR = "LostTalesKeepWear";
    private static final int SPEAR_SCAN_TICKS = 20;
    /** The most mugs a keg's slot holds. */
    private static final int KEG_MOST_MUGS = 64;
    private static final Random RANDOM = new Random();

    // A bow's draw and a crossbow's load: both sides.

    @SubscribeEvent
    public void onUseStart(PlayerUseItemEvent.Start event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || event.item == null) {
            return;
        }
        PerkRuntime.Moments moments = PerkRuntime.of(player);
        moments.drawnTicks = 0;
        moments.drawAdded = 0;
        PerkIndex perks = Perks.of(player);
        for (TakenPerk nock : perks.taken(PerkEffect.QUICK_NOCK)) {
            if (Perks.weapon(event.item).matches(withOrWeapons(nock))
                    && player.worldObj.getTotalWorldTime() - moments.lastShot
                    <= (long) (nock.param("seconds", 3.0D) * 20.0D)) {
                int ahead = (int) Math.round(LotrPerkHooks.drawTicks(event.item) * nock.amount());
                event.duration = Math.max(1, event.duration - ahead);
                moments.drawAdded += ahead;
            }
        }
    }

    @SubscribeEvent
    public void onUseTick(PlayerUseItemEvent.Tick event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || player.worldObj == null || event.item == null) {
            return;
        }
        WeaponKind kind = LotrWeaponKinds.kindOf(event.item);
        if (kind != WeaponKind.BOW && kind != WeaponKind.CROSSBOW) {
            return;
        }
        PerkIndex perks = Perks.of(player);
        PerkIndex.WeaponMatch match = Perks.weapon(event.item);
        double share = perks.total(PerkEffect.DRAW_SPEED, match)
                + perks.total(PerkEffect.RELOAD_SPEED, match);
        if (!(share > 0.0D)) {
            return;
        }
        int full = LotrPerkHooks.drawTicks(event.item);
        int drawn = event.item.getMaxItemUseDuration() - event.duration;
        if (drawn >= full) {
            return;
        }
        PerkRuntime.Moments moments = PerkRuntime.of(player);
        moments.drawnTicks++;
        int owed = (int) Math.floor(moments.drawnTicks * share) - moments.drawAdded;
        if (owed > 0) {
            int extra = Math.min(owed, full - drawn);
            event.duration -= extra;
            moments.drawAdded += extra;
        }
    }

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        if (event.entityPlayer != null && event.entityPlayer.worldObj != null) {
            PerkRuntime.of(event.entityPlayer).lastShot =
                    event.entityPlayer.worldObj.getTotalWorldTime();
        }
    }

    /** Arrows and spears as they leave a player: farther, truer from the saddle, marked to take no wear. */
    @SubscribeEvent
    public void onEntityJoin(EntityJoinWorldEvent event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        if (event.entity instanceof EntityArrow
                && ((EntityArrow) event.entity).shootingEntity instanceof EntityPlayerMP) {
            EntityArrow arrow = (EntityArrow) event.entity;
            EntityPlayerMP archer = (EntityPlayerMP) arrow.shootingEntity;
            PerkIndex perks = Perks.of(archer);
            double aim = perks.total(PerkEffect.MOUNTED_AIM);
            if (aim > 0.0D && archer.ridingEntity != null) {
                aimTowardLook(arrow, archer, Math.min(1.0D, aim));
            }
            double flight = perks.total(PerkEffect.ARROW_FLIGHT);
            if (flight > 0.0D) {
                arrow.motionX *= 1.0D + flight;
                arrow.motionY *= 1.0D + flight;
                arrow.motionZ *= 1.0D + flight;
                // An arrow's damage grows with its speed; the speed alone is the perk's.
                arrow.setDamage(arrow.getDamage() / (1.0D + flight));
            }
            return;
        }
        ItemStack carried = LotrCountHooks.projectileItem(event.entity);
        Entity thrower = LotrPerkHooks.throwerOf(event.entity);
        if (carried != null && thrower instanceof EntityPlayerMP
                && LotrWeaponKinds.kindOfProjectile(event.entity) == WeaponKind.SPEAR) {
            PerkIndex perks = Perks.of((EntityPlayer) thrower);
            double flight = perks.total(PerkEffect.THROW_FLIGHT, Perks.weapon(carried));
            if (flight > 0.0D) {
                event.entity.motionX *= 1.0D + flight;
                event.entity.motionY *= 1.0D + flight;
                event.entity.motionZ *= 1.0D + flight;
            }
            if (RANDOM.nextDouble() < perks.total(PerkEffect.THROW_RECOVERY, Perks.weapon(carried))) {
                ItemStack marked = carried.copy();
                if (!marked.hasTagCompound()) {
                    marked.setTagCompound(new NBTTagCompound());
                }
                marked.getTagCompound().setBoolean(KEEP_WEAR, true);
                LotrPerkHooks.setProjectileItem(event.entity, marked);
                PerkRuntime.of((EntityPlayer) thrower).spearsMarked = true;
            }
        }
    }

    /** Turns an arrow's flight part of the way to where the archer looks. */
    private static void aimTowardLook(EntityArrow arrow, EntityPlayer archer, double share) {
        double speed = Math.sqrt(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY
                + arrow.motionZ * arrow.motionZ);
        if (speed < 1.0E-4D) {
            return;
        }
        Vec3 look = archer.getLookVec();
        double x = arrow.motionX / speed * (1.0D - share) + look.xCoord * share;
        double y = arrow.motionY / speed * (1.0D - share) + look.yCoord * share;
        double z = arrow.motionZ / speed * (1.0D - share) + look.zCoord * share;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-4D) {
            return;
        }
        arrow.motionX = x / length * speed;
        arrow.motionY = y / length * speed;
        arrow.motionZ = z / length * speed;
    }

    // Wear: weapons in a blow, tools in a break, armour in a blow taken.

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        if (event.entityPlayer instanceof EntityPlayerMP) {
            watchHeld(event.entityPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof EntityPlayerMP) {
            watchHeld(event.getPlayer());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) event.entityLiving;
        double share = Perks.of(player).total(PerkEffect.ARMOUR_WEAR);
        if (share > 0.0D) {
            for (ItemStack armour : player.inventory.armorInventory) {
                if (armour != null && armour.isItemStackDamageable()) {
                    PerkRuntime.of(player).wear.add(new PerkRuntime.Wear(armour, share));
                }
            }
        }
    }

    private static void watchHeld(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (held == null || !held.isItemStackDamageable()) {
            return;
        }
        double share = Perks.of(player).total(PerkEffect.WEAR, Perks.weapon(held));
        if (share > 0.0D) {
            PerkRuntime.of(player).wear.add(new PerkRuntime.Wear(held, share));
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PerkRuntime.Moments moments = PerkRuntime.of(player);
        for (PerkRuntime.Wear wear : moments.wear) {
            if (wear.stack.stackSize > 0 && wear.stack.getItemDamage() > wear.damageBefore
                    && RANDOM.nextDouble() < wear.chance) {
                wear.stack.setItemDamage(wear.damageBefore);
            }
        }
        moments.wear.clear();
        if (moments.spearsMarked && player.ticksExisted % SPEAR_SCAN_TICKS == 0) {
            mendMarkedSpears(player);
        }
        double wait = Perks.of(player).total(PerkEffect.TRAVEL_WAIT);
        if (wait > 0.0D) {
            // LOTR's clock moves a tick a tick; this many more make the wait that share shorter.
            moments.travelCarry += Math.min(wait, 0.9D) / (1.0D - Math.min(wait, 0.9D));
            int ticks = (int) Math.floor(moments.travelCarry);
            if (ticks > 0) {
                moments.travelCarry -= ticks;
                LotrPerkHooks.hastenFastTravel(player, ticks);
            }
        }
    }

    /** A spear thrown under Recovery came back worn: its wear is undone and its mark taken off. */
    private static void mendMarkedSpears(EntityPlayerMP player) {
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.hasTagCompound()
                    && stack.getTagCompound().getBoolean(KEEP_WEAR)) {
                stack.getTagCompound().removeTag(KEEP_WEAR);
                if (stack.getTagCompound().hasNoTags()) {
                    stack.setTagCompound(null);
                }
                if (stack.getItemDamage() > 0) {
                    stack.setItemDamage(stack.getItemDamage() - 1);
                }
            }
        }
    }

    // Speed: felling, stone, deep down and picks; both sides.

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        EntityPlayer player = event.entityPlayer;
        if (player == null || event.block == null) {
            return;
        }
        PerkIndex perks = Perks.of(player);
        ItemStack held = player.getHeldItem();
        PerkIndex.WeaponMatch match = Perks.weapon(held);
        Material material = event.block.getMaterial();
        double bonus = perks.total(PerkEffect.MINE_SPEED, match);
        if (material == Material.wood) {
            bonus += perks.total(PerkEffect.CHOP_SPEED, match);
        }
        if (material == Material.rock) {
            bonus += perks.total(PerkEffect.STONE_SPEED);
        }
        for (TakenPerk deep : perks.taken(PerkEffect.DEEP_SPEED)) {
            if (event.y < deep.param("below", 32.0D)) {
                bonus += deep.amount();
            }
        }
        if (bonus > 0.0D) {
            event.newSpeed *= (float) (1.0D + bonus);
        }
    }

    // Drops: crops, herbs, ores and gems.

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (!(event.harvester instanceof EntityPlayerMP) || event.drops == null
                || event.drops.isEmpty() || event.block == null) {
            return;
        }
        PerkIndex perks = Perks.of(event.harvester);
        Thing block = Things.of(event.block, event.blockMetadata);
        List<ItemStack> extra = new ArrayList<ItemStack>();
        ItemStack first = event.drops.get(0);
        if (CountEvents.isRipe(event.block, event.blockMetadata)
                && RANDOM.nextDouble() < perks.total(PerkEffect.HARVEST_BONUS)) {
            extra.add(one(event.drops.get(RANDOM.nextInt(event.drops.size()))));
        }
        for (TakenPerk herbalist : perks.taken(PerkEffect.HERBALIST)) {
            if (names(herbalist.getPerk().getWith(), block) && first != null) {
                ItemStack more = first.copy();
                more.stackSize = Math.max(1, (int) Math.round(herbalist.amount()));
                extra.add(more);
            }
        }
        if (!event.isSilkTouching) {
            for (TakenPerk ore : perks.taken(PerkEffect.ORE_BONUS)) {
                if (names(ore.getPerk().getWith(), block) && first != null
                        && RANDOM.nextDouble() < ore.amount()) {
                    extra.add(one(first));
                }
            }
            for (TakenPerk gem : perks.taken(PerkEffect.GEM_BONUS)) {
                if (names(gem.getPerk().getWith(), block) && RANDOM.nextDouble() < gem.amount()) {
                    for (ItemStack drop : event.drops) {
                        if (drop != null) {
                            extra.add(drop.copy());
                        }
                    }
                }
            }
        }
        event.drops.addAll(extra);
    }

    private static boolean names(List<String> with, Thing thing) {
        for (String key : thing.getKeys()) {
            if (with.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack one(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    // Smelting, forging and repairing.

    @SubscribeEvent
    public void onItemSmelted(ItemSmeltedEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.smelting == null
                || event.smelting.stackSize <= 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PerkIndex perks = Perks.of(player);
        String name = LotrWeaponKinds.itemName(event.smelting);
        for (TakenPerk quick : perks.taken(PerkEffect.SMELT_NUGGET)) {
            for (String pair : quick.getPerk().getWith()) {
                int arrow = pair.indexOf('>');
                if (arrow <= 0 || !pair.substring(0, arrow).equals(name)) {
                    continue;
                }
                Item nugget = (Item) GameData.getItemRegistry().getObject(pair.substring(arrow + 1));
                int count = chances(event.smelting.stackSize, quick.amount());
                if (nugget != null && count > 0) {
                    give(player, new ItemStack(nugget, count));
                }
            }
        }
        if (event.smelting.getItem() instanceof ItemFood) {
            int more = chances(event.smelting.stackSize, perks.total(PerkEffect.SECOND_HELPING));
            if (more > 0) {
                ItemStack helping = event.smelting.copy();
                helping.stackSize = more;
                give(player, helping);
            }
        }
    }

    @SubscribeEvent
    public void onItemCrafted(ItemCraftedEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.crafting == null
                || event.craftMatrix == null) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PerkIndex perks = Perks.of(player);
        if (!perks.has(PerkEffect.FORGE_THRIFT) && !perks.has(PerkEffect.TEMPERED)
                && !perks.has(PerkEffect.MASTERWORK)) {
            return;
        }
        Thing made = Things.of(event.crafting);
        List<Thing> used = new ArrayList<Thing>();
        List<ItemStack> usedStacks = new ArrayList<ItemStack>();
        IInventory matrix = event.craftMatrix;
        for (int slot = 0; slot < matrix.getSizeInventory(); slot++) {
            ItemStack stack = matrix.getStackInSlot(slot);
            if (stack != null) {
                used.add(Things.of(stack));
                usedStacks.add(stack);
            }
        }
        for (TakenPerk thrift : perks.taken(PerkEffect.FORGE_THRIFT)) {
            SkillCount craft = craftCount(thrift);
            if (craft != null && CountWorth.ofCraft(craft, made, 1, used) > 0.0D
                    && RANDOM.nextDouble() < thrift.amount()) {
                ItemStack best = null;
                double bestWorth = 0.0D;
                for (int index = 0; index < used.size(); index++) {
                    double worth = CountWorth.tableWorth(craft.getIngredients(), used.get(index));
                    if (worth > bestWorth) {
                        bestWorth = worth;
                        best = usedStacks.get(index);
                    }
                }
                if (best != null) {
                    give(player, one(best));
                }
            }
        }
        TakenPerk tempered = perks.first(PerkEffect.TEMPERED);
        TakenPerk masterwork = perks.first(PerkEffect.MASTERWORK);
        TakenPerk forge = tempered != null ? tempered : masterwork;
        SkillCount craft = forge == null ? null : craftCount(forge);
        if (craft == null || CountWorth.ofCraft(craft, made, 1, used) <= 0.0D
                || !LotrPerkHooks.modifiersOn(player.worldObj)) {
            return;
        }
        ItemStack piece = freshPiece(player, event.crafting);
        if (piece != null) {
            boolean fine = masterwork != null && RANDOM.nextDouble() < masterwork.amount();
            if (LotrPerkHooks.forge(piece, RANDOM, tempered != null, fine)) {
                player.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.skill.note.masterwork", piece.getDisplayName()));
            }
        }
    }

    /** The perk's skill's count of crafts, which says what a forged piece used. */
    private static SkillCount craftCount(TakenPerk perk) {
        for (SkillCount count : perk.getSkill().getCounts()) {
            if (count.getAct() == SkillAct.CRAFT && !count.getIngredients().isEmpty()) {
                return count;
            }
        }
        return null;
    }

    /**
     * The piece a craft just made: on the cursor, or, taken by a
     * shift-click, the copy in the inventory LOTR has not rolled yet.
     */
    private static ItemStack freshPiece(EntityPlayerMP player, ItemStack crafted) {
        if (crafted.stackSize > 0 && LotrPerkHooks.isUnrolled(crafted)) {
            return crafted;
        }
        ItemStack held = player.inventory.getItemStack();
        if (held != null && held.getItem() == crafted.getItem() && LotrPerkHooks.isUnrolled(held)) {
            return held;
        }
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack != null && stack.getItem() == crafted.getItem()
                    && LotrPerkHooks.isUnrolled(stack)) {
                return stack;
            }
        }
        return null;
    }

    /** A repair at vanilla's anvil: some of its levels come back, and it restores more. */
    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.left == null
                || event.output == null || !event.left.isItemDamaged()
                || event.output.getItemDamage() >= event.left.getItemDamage()) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        PerkIndex perks = Perks.of(player);
        double more = perks.total(PerkEffect.REPAIR_MORE);
        if (more > 0.0D) {
            int restored = event.left.getItemDamage() - event.output.getItemDamage();
            event.output.setItemDamage(Math.max(0, event.output.getItemDamage()
                    - (int) Math.round(restored * more)));
        }
        double cheaper = perks.total(PerkEffect.REPAIR_COST);
        if (cheaper > 0.0D && player.openContainer instanceof ContainerRepair
                && !player.capabilities.isCreativeMode) {
            int cost = ((ContainerRepair) player.openContainer).maximumCost;
            int back = cost - PerkShares.lower(cost, cheaper);
            if (back > 0) {
                player.addExperienceLevel(back);
            }
        }
    }

    /** A keg filled by a Brewer gives more mugs, as many as a keg's slot holds. */
    public static void brewed(EntityPlayerMP player, ItemStack drink) {
        int more = (int) Math.round(Perks.of(player).total(PerkEffect.BREWER));
        if (drink != null && more > 0) {
            drink.stackSize = Math.min(KEG_MOST_MUGS, drink.stackSize + more);
        }
    }

    private static List<String> withOrWeapons(TakenPerk perk) {
        return perk.getPerk().getWith().isEmpty() ? perk.getSkill().weapons()
                : perk.getPerk().getWith();
    }

    /** How many of {@code tries} came up under {@code chance}. */
    private static int chances(int tries, double chance) {
        if (!(chance > 0.0D)) {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < tries; index++) {
            if (RANDOM.nextDouble() < chance) {
                count++;
            }
        }
        return count;
    }

    private static void give(EntityPlayerMP player, ItemStack stack) {
        if (!player.inventory.addItemStackToInventory(stack) && stack.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(stack, false);
        }
        player.inventoryContainer.detectAndSendChanges();
    }
}
