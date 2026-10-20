package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.compat.lotr.LotrCountHooks;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.progression.skill.PerkEffect;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityAgeable;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemShears;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

/**
 * The perks of riding and the herds (PROG-06): a mount's speed and jump
 * under its rider, taming, feeding, shearing, breeding and butchering,
 * and Steadfast's footing. The server's alone; attribute changes never
 * save with the creature.
 */
public final class PerkAnimalEvents {
    private static final UUID LONG_ROAD = UUID.fromString("5b5f4a52-6c86-4a2e-9a51-6a0b1b5a8a01");
    private static final UUID RIDER_OF_THE_MARK = UUID.fromString("5b5f4a52-6c86-4a2e-9a51-6a0b1b5a8a02");
    private static final UUID STEADFAST = UUID.fromString("5b5f4a52-6c86-4a2e-9a51-6a0b1b5a8a03");
    /** Vanilla's ages: a parent the tick it bred, a newborn. */
    private static final int JUST_BRED_AGE = 6000;
    private static final int NEWBORN_AGE = -24000;
    private static final int SAMPLE_TICKS = 10;
    private static final Random RANDOM = new Random();
    private static final List<Shearing> SHEARINGS = new ArrayList<Shearing>();

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.target == null
                || event.target.worldObj == null || event.target.worldObj.isRemote) {
            return;
        }
        EntityPlayer player = event.entityPlayer;
        PerkIndex perks = Perks.of(player);
        if (LotrCountHooks.isMount(event.target) && !LotrCountHooks.isTamed(event.target)) {
            int calm = (int) Math.round(perks.total(PerkEffect.TAME_EASE));
            if (calm > 0) {
                LotrPerkHooks.calm(event.target, calm);
            }
        }
        ItemStack held = player.getHeldItem();
        if (event.target instanceof EntitySheep && !((EntitySheep) event.target).getSheared()
                && held != null && held.getItem() instanceof ItemShears
                && perks.has(PerkEffect.SHEAR_BONUS)) {
            synchronized (SHEARINGS) {
                SHEARINGS.add(new Shearing((EntitySheep) event.target,
                        (int) Math.round(perks.total(PerkEffect.SHEAR_BONUS))));
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<Shearing> due;
        synchronized (SHEARINGS) {
            if (SHEARINGS.isEmpty()) {
                return;
            }
            due = new ArrayList<Shearing>(SHEARINGS);
            SHEARINGS.clear();
        }
        for (Shearing shearing : due) {
            EntitySheep sheep = shearing.sheep;
            if (!sheep.isDead && sheep.getSheared()) {
                sheep.worldObj.spawnEntityInWorld(new EntityItem(sheep.worldObj, sheep.posX,
                        sheep.posY + 1.0D, sheep.posZ,
                        new ItemStack(Blocks.wool, shearing.more, sheep.getFleeceColor())));
            }
        }
    }

    /** A mount was fed under Caretaker: it heals that much more. */
    public static void fed(EntityPlayerMP player, Entity mount, float healed) {
        double more = Perks.of(player).total(PerkEffect.FEED_HEAL);
        if (more > 0.0D && healed > 0.0F && mount instanceof EntityLivingBase) {
            ((EntityLivingBase) mount).heal((float) (healed * more));
        }
    }

    /** Young bred by a player: Master of Beasts grows it sooner, Breeder readies the parents. */
    public static void bred(EntityPlayerMP breeder, EntityAgeable young, World world, double reach) {
        PerkIndex perks = Perks.of(breeder);
        double grow = Math.min(1.0D, perks.total(PerkEffect.GROW_FASTER));
        if (grow > 0.0D) {
            young.setGrowingAge((int) Math.round(NEWBORN_AGE * (1.0D - grow)));
        }
        double ready = Math.min(1.0D, perks.total(PerkEffect.BREED_COOLDOWN));
        if (ready > 0.0D) {
            for (Object entry : world.getEntitiesWithinAABB(EntityAnimal.class,
                    young.boundingBox.expand(reach, reach, reach))) {
                EntityAnimal parent = (EntityAnimal) entry;
                if (parent != young && parent.getGrowingAge() == JUST_BRED_AGE) {
                    parent.setGrowingAge((int) Math.round(JUST_BRED_AGE * (1.0D - ready)));
                }
            }
        }
    }

    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (!(event.entityLiving instanceof EntityAnimal) || event.drops == null
                || !(event.source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        double chance = Perks.of((EntityPlayer) event.source.getEntity())
                .total(PerkEffect.BUTCHER);
        if (!(chance > 0.0D)) {
            return;
        }
        List<EntityItem> more = new ArrayList<EntityItem>();
        for (EntityItem drop : event.drops) {
            ItemStack stack = drop.getEntityItem();
            if (stack != null && (stack.getItem() instanceof ItemFood
                    || stack.getItem() == Items.leather) && RANDOM.nextDouble() < chance) {
                ItemStack one = stack.copy();
                one.stackSize = 1;
                more.add(new EntityItem(drop.worldObj, drop.posX, drop.posY, drop.posZ, one));
            }
        }
        event.drops.addAll(more);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote
                || (event.player.ticksExisted + event.player.getEntityId()) % SAMPLE_TICKS != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PerkIndex perks = Perks.of(player);
        raise(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),
                STEADFAST, "Steadfast", Math.min(1.0D, perks.total(PerkEffect.STEADFAST)), 0);
        PerkRuntime.Moments moments = PerkRuntime.of(player);
        Entity mount = player.ridingEntity;
        int mountId = mount instanceof EntityLivingBase ? mount.getEntityId() : -1;
        if (moments.raisedMount != -1 && moments.raisedMount != mountId) {
            Entity before = player.worldObj.getEntityByID(moments.raisedMount);
            if (before instanceof EntityLivingBase) {
                lower((EntityLivingBase) before);
            }
            moments.raisedMount = -1;
        }
        if (mountId == -1) {
            return;
        }
        EntityLivingBase ridden = (EntityLivingBase) mount;
        double speed = perks.total(PerkEffect.MOUNT_SPEED);
        double jump = perks.total(PerkEffect.MOUNT_JUMP);
        raise(ridden.getEntityAttribute(SharedMonsterAttributes.movementSpeed),
                LONG_ROAD, "Long Road", speed, 1);
        if (ridden instanceof EntityHorse) {
            raise(ridden.getEntityAttribute(LotrPerkHooks.horseJump()),
                    RIDER_OF_THE_MARK, "Rider of the Mark", jump, 1);
        }
        moments.raisedMount = speed > 0.0D || jump > 0.0D ? mountId : -1;
    }

    /** Takes a perk's raise off a mount its rider left. */
    private static void lower(EntityLivingBase mount) {
        remove(mount.getEntityAttribute(SharedMonsterAttributes.movementSpeed), LONG_ROAD);
        IAttribute jump = LotrPerkHooks.horseJump();
        if (mount instanceof EntityHorse && jump != null) {
            remove(mount.getEntityAttribute(jump), RIDER_OF_THE_MARK);
        }
    }

    /** Puts a raise in place at {@code amount}, none at 0, replacing one of another size. */
    private static void raise(IAttributeInstance attribute, UUID id, String name,
                              double amount, int operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && current.getAmount() == amount) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(current);
        }
        if (amount > 0.0D) {
            attribute.applyModifier(new AttributeModifier(id, "Lost Tales " + name,
                    amount, operation).setSaved(false));
        }
    }

    private static void remove(IAttributeInstance attribute, UUID id) {
        AttributeModifier current = attribute == null ? null : attribute.getModifier(id);
        if (current != null) {
            attribute.removeModifier(current);
        }
    }

    public static void clear() {
        synchronized (SHEARINGS) {
            SHEARINGS.clear();
        }
    }

    /** A shearing whose wool shows a tick later. */
    private static final class Shearing {
        final EntitySheep sheep;
        final int more;

        Shearing(EntitySheep sheep, int more) {
            this.sheep = sheep;
            this.more = more;
        }
    }
}
