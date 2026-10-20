package com.ninuna.losttales.progression.count;

import com.ninuna.losttales.compat.lotr.LotrCountHooks;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.progression.ProgressionService;
import com.ninuna.losttales.progression.perk.PerkAnimalEvents;
import com.ninuna.losttales.progression.perk.PerkWorkEvents;
import com.ninuna.losttales.progression.skill.SkillAct;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.skill.WeaponKind;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.perk.PerkRuntime;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemCraftedEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.ItemSmeltedEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCocoa;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockMelon;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AnvilRepairEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.world.BlockEvent;

/**
 * Turns what players do into skill experience (PROG-01, PROG-02): hits
 * and kills on a foe that fights back, blows armour and blocking took,
 * distance ridden, mounts and animals tamed and fed, items smelted and
 * crafted, anvil repairs, kegs filled, crops and herbs harvested, blocks
 * mined, young bred and regions opened. Players count toward the combat
 * skills only where the server says so. Registered on both buses; reads
 * only what other mods' entities, items and blocks show any game.
 */
public final class CountEvents {
    private static final int SAMPLE_TICKS = 20;
    private static final int REGION_TICKS = 100;
    /** Farther than this in one sample is a teleport, not a ride. */
    private static final double MAX_RIDE_PER_SAMPLE = 40.0D;
    /** The growing age vanilla sets on parents the tick they breed. */
    private static final int JUST_BRED_AGE = 6000;
    private static final int NEWBORN_AGE = -24000;
    private static final double PARENT_REACH = 8.0D;

    // Combat: hits and kills (Archery, Blades, Heavy Arms, Polearms) and blows taken (Defence).

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.entityLiving;
        if (event.isCanceled() || target == null || target.worldObj == null
                || target.worldObj.isRemote || !(event.ammount > 0.0F)) {
            return;
        }
        DamageSource source = event.source;
        EntityPlayerMP attacker = attackerOf(source);
        if (attacker != null && attacker != target && isFoe(attacker, target)) {
            double dealt = Math.min(event.ammount, Math.max(0.0F, target.getHealth()));
            Weapon weapon = weaponOf(attacker, source);
            String kind = Things.nameOf(target);
            SkillCounter.weaponAct(attacker, SkillAct.HIT, Things.of(target), dealt,
                    SkillCounter.key(SkillAct.KILL, kind), false, weapon.kind, weapon.stack);
        }
        if (target instanceof EntityPlayerMP && !source.isUnblockable()
                && source.getEntity() instanceof EntityLivingBase
                && source.getEntity() != target
                && isFoe((EntityPlayerMP) target, (EntityLivingBase) source.getEntity())) {
            EntityPlayerMP player = (EntityPlayerMP) target;
            Entity foe = source.getEntity();
            SkillCounter.act(player, SkillAct.GUARD, Things.of(foe),
                    guarded(player, event.ammount),
                    SkillCounter.key(SkillAct.GUARD, foe.getUniqueID().toString()), true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase target = event.entityLiving;
        if (event.isCanceled() || target == null || target.worldObj == null
                || target.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP killer = attackerOf(event.source);
        if (killer != null && killer != target && isFoe(killer, target)) {
            Weapon weapon = weaponOf(killer, event.source);
            String kind = Things.nameOf(target);
            SkillCounter.weaponAct(killer, SkillAct.KILL, Things.of(target),
                    target.getMaxHealth(), SkillCounter.key(SkillAct.KILL, kind), true,
                    weapon.kind, weapon.stack);
        }
    }

    /**
     * Whether the target fights back: anything with an attack of its own
     * or hostile by nature, never the attacker's own tamed animal; another
     * player only where the server counts players.
     */
    static boolean isFoe(EntityPlayerMP attacker, EntityLivingBase target) {
        if (target instanceof EntityPlayer) {
            return LostTalesConfig.playersCountForCombat;
        }
        if (target instanceof EntityTameable && ((EntityTameable) target).isTamed()
                && attacker.getCommandSenderName().equals(
                        ((EntityTameable) target).func_152113_b())) {
            return false;
        }
        return target instanceof IMob || target.getAttributeMap()
                .getAttributeInstance(SharedMonsterAttributes.attackDamage) != null;
    }

    /** The player behind a blow, struck in hand or loosed; null for anything else. */
    static EntityPlayerMP attackerOf(DamageSource source) {
        Entity entity = source == null ? null : source.getEntity();
        return entity instanceof EntityPlayerMP ? (EntityPlayerMP) entity : null;
    }

    static Weapon weaponOf(EntityPlayerMP player, DamageSource source) {
        Entity direct = source.getSourceOfDamage();
        if (direct != null && direct != player) {
            ItemStack carried = LotrCountHooks.projectileItem(direct);
            return new Weapon(LotrWeaponKinds.kindOfProjectile(direct), carried);
        }
        ItemStack held = player.getHeldItem();
        return new Weapon(LotrWeaponKinds.kindOf(held), held);
    }

    /** What armour and blocking kept from a blow, as vanilla shares it out. */
    static double guarded(EntityPlayerMP player, float amount) {
        double blocked = 0.0D;
        if (player.isBlocking() && amount > 1.0F) {
            blocked = (amount - 1.0F) * 0.5D;
        }
        double armour = Math.min(20, player.getTotalArmorValue()) / 25.0D;
        return blocked + (amount - blocked) * armour;
    }

    /** A weapon by its kind and the item itself, either of which a file may name. */
    static final class Weapon {
        final WeaponKind kind;
        final ItemStack stack;

        Weapon(WeaponKind kind, ItemStack stack) {
            this.kind = kind;
            this.stack = stack;
        }
    }

    // Crafts: smelting and forging (Smithing, Cooking), anvil repairs (Smithing).

    @SubscribeEvent
    public void onItemSmelted(ItemSmeltedEvent event) {
        if (event.player instanceof EntityPlayerMP && event.smelting != null
                && event.smelting.stackSize > 0) {
            Thing thing = Things.of(event.smelting);
            SkillCounter.act((EntityPlayerMP) event.player, SkillAct.SMELT, thing,
                    event.smelting.stackSize,
                    SkillCounter.key(SkillAct.SMELT, thing.getRepeatKey()), true);
        }
    }

    @SubscribeEvent
    public void onItemCrafted(ItemCraftedEvent event) {
        if (!(event.player instanceof EntityPlayerMP) || event.crafting == null) {
            return;
        }
        List<Thing> used = new ArrayList<Thing>();
        IInventory matrix = event.craftMatrix;
        if (matrix != null) {
            for (int slot = 0; slot < matrix.getSizeInventory(); slot++) {
                ItemStack stack = matrix.getStackInSlot(slot);
                if (stack != null) {
                    used.add(Things.of(stack));
                }
            }
        }
        SkillCounter.craft((EntityPlayerMP) event.player, Things.of(event.crafting),
                event.crafting.stackSize, used);
    }

    @SubscribeEvent
    public void onAnvilRepair(AnvilRepairEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.left == null
                || event.output == null || !event.left.isItemDamaged()
                || event.output.getItemDamage() >= event.left.getItemDamage()) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        int cost = player.openContainer instanceof ContainerRepair
                ? ((ContainerRepair) player.openContainer).maximumCost : 0;
        repaired(player, event.output, cost);
    }

    /** A repair at any anvil: {@code cost} the levels or material it took. */
    public static void repaired(EntityPlayerMP player, ItemStack output, int cost) {
        Thing thing = Things.of(output);
        SkillCounter.act(player, SkillAct.REPAIR, thing, cost,
                SkillCounter.key(SkillAct.REPAIR, thing.getRepeatKey()), true);
    }

    // The land: mining (Mining), crops and herbs (Husbandry).

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        EntityPlayer breaker = event.getPlayer();
        if (event.isCanceled() || !(breaker instanceof EntityPlayerMP)
                || breaker.capabilities.isCreativeMode || event.block == null) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) breaker;
        Thing thing = Things.of(event.block, event.blockMetadata);
        if (ForgeHooks.canHarvestBlock(event.block, player, event.blockMetadata)) {
            SkillCounter.act(player, SkillAct.MINE, thing, 1.0D,
                    SkillCounter.key(SkillAct.MINE, thing.getRepeatKey()), true);
        }
        if (isRipe(event.block, event.blockMetadata) || named(SkillAct.HARVEST, thing)) {
            SkillCounter.act(player, SkillAct.HARVEST, thing, 1.0D,
                    SkillCounter.key(SkillAct.HARVEST, thing.getRepeatKey()), true);
        }
    }

    /** Whether a crop is ripe: grown wheat and LOTR's crops, nether wart, cocoa, a melon or pumpkin. */
    public static boolean isRipe(Block block, int meta) {
        if (block instanceof BlockCrops) {
            return meta >= 7;
        }
        if (block instanceof BlockNetherWart) {
            return meta >= 3;
        }
        if (block instanceof BlockCocoa) {
            return BlockCocoa.func_149987_c(meta) >= 2;
        }
        return block instanceof BlockMelon || block instanceof BlockPumpkin;
    }

    /** Whether any skill's table for the act names the thing itself, not only {@code *}. */
    static boolean named(SkillAct act, Thing thing) {
        for (SkillDefinition skill : ProgressionCatalog.skills()) {
            for (SkillCount count : skill.getCounts()) {
                if (count.getAct() != act) {
                    continue;
                }
                for (String key : thing.getKeys()) {
                    if (count.getWorth().entries().containsKey(key)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Animals: taming and feeding (Riding, Husbandry), breeding (Husbandry).

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.target == null
                || event.target.worldObj == null || event.target.worldObj.isRemote) {
            return;
        }
        Entity target = event.target;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        long due = tick() + 1L;
        int dimension = target.worldObj.provider.dimensionId;
        if (target instanceof EntityTameable && !((EntityTameable) target).isTamed()) {
            CountWatch.await(new CountWatch.Pending(player.getUniqueID(), dimension,
                    target.getEntityId(), true, 0.0F, due));
        } else if (LotrCountHooks.isMount(target) && player.getHeldItem() != null) {
            CountWatch.await(new CountWatch.Pending(player.getUniqueID(), dimension,
                    target.getEntityId(), false, ((EntityLivingBase) target).getHealth(), due));
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!(event.entity instanceof EntityAgeable) || event.world == null
                || event.world.isRemote
                || ((EntityAgeable) event.entity).getGrowingAge() != NEWBORN_AGE) {
            return;
        }
        EntityPlayerMP breeder = breederOf(event.world, (EntityAgeable) event.entity);
        if (breeder != null) {
            Thing thing = Things.of(event.entity);
            SkillCounter.act(breeder, SkillAct.BREED, thing, 1.0D,
                    SkillCounter.key(SkillAct.BREED, thing.getRepeatKey()), true);
            PerkAnimalEvents.bred(breeder, (EntityAgeable) event.entity, event.world,
                    PARENT_REACH);
        }
    }

    /** The player who fed the parents that bred this young the tick they bred, or null. */
    public static EntityPlayerMP breederOf(World world, EntityAgeable young) {
        List<?> near = world.getEntitiesWithinAABB(EntityAnimal.class,
                young.boundingBox.expand(PARENT_REACH, PARENT_REACH, PARENT_REACH));
        for (Object entry : near) {
            EntityAnimal parent = (EntityAnimal) entry;
            if (parent != young && parent.getGrowingAge() == JUST_BRED_AGE
                    && parent.func_146083_cb() instanceof EntityPlayerMP) {
                return (EntityPlayerMP) parent.func_146083_cb();
            }
        }
        return null;
    }

    // Riding and regions: sampled on each player's tick; a keg open is watched every tick.

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        int phase = player.getEntityId() + player.ticksExisted;
        if (phase % SAMPLE_TICKS == 0) {
            sampleRide(player);
        }
        sampleKeg(player);
        if (phase % REGION_TICKS == 0) {
            sampleRegions(player);
        }
    }

    private static void sampleRide(EntityPlayerMP player) {
        CountWatch.Rider rider = CountWatch.rider(player.getUniqueID());
        Entity mount = player.ridingEntity;
        int dimension = player.worldObj.provider.dimensionId;
        if (mount instanceof EntityLiving) {
            if (rider.dimension == dimension && rider.mountId == mount.getEntityId()) {
                double dx = mount.posX - rider.x;
                double dz = mount.posZ - rider.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 0.0D && distance <= MAX_RIDE_PER_SAMPLE) {
                    SkillCounter.act(player, SkillAct.RIDE, Things.of(mount), distance, "", false);
                }
                boolean tamed = LotrCountHooks.isTamed(mount);
                if (tamed && !rider.mountTamed) {
                    tamed(player, mount);
                }
                rider.mountTamed = tamed;
            } else {
                rider.mountTamed = LotrCountHooks.isTamed(mount);
            }
            rider.dimension = dimension;
            rider.mountId = mount.getEntityId();
            rider.x = mount.posX;
            rider.z = mount.posZ;
        } else {
            rider.mountId = -1;
            rider.dimension = Integer.MIN_VALUE;
        }
    }

    private static void sampleKeg(EntityPlayerMP player) {
        int mode = LotrCountHooks.kegMode(player.openContainer);
        int before = CountWatch.swapKegMode(player.getUniqueID(), mode);
        if (before == LotrCountHooks.KEG_EMPTY && mode == LotrCountHooks.KEG_BREWING) {
            ItemStack drink = LotrCountHooks.kegDrink(player.openContainer);
            PerkWorkEvents.brewed(player, drink);
            if (drink != null && drink.stackSize > 0) {
                Thing thing = Things.of(drink);
                SkillCounter.act(player, SkillAct.BREW, thing, drink.stackSize,
                        SkillCounter.key(SkillAct.BREW, thing.getRepeatKey()), true);
            }
        }
    }

    private static void sampleRegions(EntityPlayerMP player) {
        if (ProgressionService.playedCharacter(player) == null) {
            return;
        }
        Set<String> opened = LotrCountHooks.openedRegions(player);
        Set<String> before = CountWatch.swapRegions(player.getUniqueID(), opened);
        if (before == null) {
            return;
        }
        for (String region : opened) {
            if (!before.contains(region)) {
                discovered(player, "region", region);
            }
        }
    }

    /** A first find: {@code kind} is region, waypoint or marker, {@code id} what was found. */
    public static void discovered(EntityPlayerMP player, String kind, String id) {
        SkillCounter.act(player, SkillAct.DISCOVER, Thing.named(kind), 1.0D,
                SkillCounter.key(SkillAct.DISCOVER, kind + "|" + id), true);
    }

    private static void tamed(EntityPlayerMP player, Entity animal) {
        Thing thing = Things.of(animal);
        SkillCounter.act(player, SkillAct.TAME, thing, 1.0D,
                SkillCounter.key(SkillAct.TAME, thing.getRepeatKey()), true);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }
        for (CountWatch.Pending pending : CountWatch.due(tick())) {
            EntityPlayerMP player = player(server, pending);
            WorldServer world = server.worldServerForDimension(pending.dimension);
            Entity target = world == null ? null : world.getEntityByID(pending.entityId);
            if (player == null || target == null || target.isDead) {
                continue;
            }
            if (pending.taming) {
                if (target instanceof EntityTameable && ((EntityTameable) target).isTamed()
                        && player.getCommandSenderName().equals(
                                ((EntityTameable) target).func_152113_b())) {
                    tamed(player, target);
                }
            } else if (target instanceof EntityLivingBase) {
                float healed = ((EntityLivingBase) target).getHealth() - pending.healthBefore;
                if (healed > 0.0F) {
                    PerkAnimalEvents.fed(player, target, healed);
                    SkillCounter.act(player, SkillAct.FEED, Things.of(target), healed,
                            SkillCounter.key(SkillAct.FEED, target.getUniqueID().toString()),
                            true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.player != null) {
            CountWatch.forget(event.player.getUniqueID());
            PerkRuntime.forget(event.player.getUniqueID());
        }
    }

    private static EntityPlayerMP player(MinecraftServer server, CountWatch.Pending pending) {
        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP
                    && ((EntityPlayerMP) entry).getUniqueID().equals(pending.playerId)) {
                return (EntityPlayerMP) entry;
            }
        }
        return null;
    }

    private static long tick() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? 0L : server.getTickCounter();
    }
}
