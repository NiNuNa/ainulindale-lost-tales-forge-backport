package com.ninuna.losttales.armour;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.perk.Perks;
import com.ninuna.losttales.progression.perk.TakenPerk;
import com.ninuna.losttales.progression.skill.PerkEffect;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;

/**
 * What the armour worn does, on the server alone: the weight set's speed
 * or footing (PROG-18) and the heavy pieces' slowdown (CONTENT-06), the
 * named sets' bonuses (CONTENT-07), and the armour skills' perks that
 * work in the world (PROG-17): protection, one share after another
 * (PROG-19), Soft Step, Standing Firm, Deft Movement and Elusive. Speed
 * and footing are attribute modifiers that never save, put right every
 * few ticks; vanilla, LOTR and other mods read them as they read any.
 * Registered on both buses.
 */
public final class ArmourEvents {
    private static final UUID SPEED = UUID.fromString("8d3c1f6e-2b7a-4c55-9e10-5a7f3b2c9d01");
    private static final UUID FOOTING = UUID.fromString("8d3c1f6e-2b7a-4c55-9e10-5a7f3b2c9d02");
    private static final UUID STANDING_FIRM = UUID.fromString("8d3c1f6e-2b7a-4c55-9e10-5a7f3b2c9d03");
    private static final int SAMPLE_TICKS = 5;
    /** A player moving less than this a tick stands still. */
    private static final double STANDING_SPEED = 0.03D;
    /** How far a foe notices a player when it has no follow range of its own. */
    private static final double DEFAULT_NOTICE = 16.0D;
    private static final Random RANDOM = new Random();
    /** Deft Movement's and Elusive's roll, once for each blow. */
    private static final BlowRoll EVADE = new BlowRoll();
    /** The poison each player last carried, in ticks, so a fresh dose is shortened once. */
    private static final Map<UUID, Integer> POISONS = new HashMap<UUID, Integer>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)
                || event.player.worldObj == null || event.player.worldObj.isRemote
                || (event.player.ticksExisted + event.player.getEntityId()) % SAMPLE_TICKS != 0) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        WornArmour worn = Armour.worn(player);
        PerkIndex perks = Perks.of(player);
        ArmourSettings settings = Armour.serverSettings();
        set(player.getEntityAttribute(SharedMonsterAttributes.movementSpeed), SPEED,
                "Armour Speed", ArmourRules.speed(worn, perks, settings), 1);
        set(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance), FOOTING,
                "Armour Footing", ArmourRules.footing(worn, perks, settings), 0);
        if (!standingFirm(player, perks)) {
            set(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),
                    STANDING_FIRM, "Standing Firm", 0.0D, 0);
        }
        shortenPoison(player, worn.left(SetEffect.POISON));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || !(event.entityLiving instanceof EntityPlayerMP)
                || event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote
                || !(event.ammount > 0.0F)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        PerkIndex perks = Perks.of(player);
        WornArmour worn = Armour.worn(player);
        boolean firm = standingFirm(player, perks);
        // Knockback comes after this event in the same blow, so the footing is set now.
        set(player.getEntityAttribute(SharedMonsterAttributes.knockbackResistance),
                STANDING_FIRM, "Standing Firm", firm ? 1.0D : 0.0D, 0);
        event.ammount = (float) (event.ammount * ArmourRules.left(worn, perks, kind(event.source),
                firm));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled() || !(event.entityLiving instanceof EntityPlayerMP)
                || event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) {
            return;
        }
        PerkIndex perks = Perks.of((EntityPlayer) event.entityLiving);
        double chance = 0.0D;
        if (event.source.isProjectile()) {
            chance = perks.total(PerkEffect.MISSILE_EVADE);
        } else if (isMelee(event.source)) {
            chance = perks.total(PerkEffect.MELEE_EVADE);
        }
        if (chance > 0.0D && EVADE.misses(event.entityLiving, event.source, chance, RANDOM)) {
            event.setCanceled(true);
        }
    }

    /**
     * Soft Step: a foe farther than its share of its notice range does not
     * pick a sneaking wearer, through the same target check the One Ring
     * uses, so it holds for LOTR's and other mods' creatures too. A foe the
     * wearer struck picks them whatever the range: the foe's own memory of
     * who struck it last ({@code getAITarget}, kept five seconds) says so,
     * while {@code softStepEndsOnStrike} is on.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        if (!(event.target instanceof EntityPlayerMP) || !(event.entityLiving instanceof EntityLiving)
                || event.entityLiving.worldObj == null || event.entityLiving.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.target;
        if (!player.isSneaking()) {
            return;
        }
        TakenPerk softStep = Perks.of(player).first(PerkEffect.SOFT_STEP);
        if (softStep == null) {
            return;
        }
        EntityLiving foe = (EntityLiving) event.entityLiving;
        IAttributeInstance range = foe.getEntityAttribute(SharedMonsterAttributes.followRange);
        if (ArmourRules.unnoticed(foe.getDistanceSqToEntity(player),
                range == null ? DEFAULT_NOTICE : range.getAttributeValue(), softStep.amount(),
                foe.getAITarget() == player, LostTalesConfig.softStepEndsOnStrike)) {
            foe.setAttackTarget(null);
        }
    }

    /** The server's armour files go to a joining player before any tooltip counts a set. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPlayerLoggedIn(PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            Armour.sendTo((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerLoggedOutEvent event) {
        if (event.player != null) {
            synchronized (POISONS) {
                POISONS.remove(event.player.getUniqueID());
            }
        }
    }

    public static void clear() {
        synchronized (POISONS) {
            POISONS.clear();
        }
        EVADE.clear();
    }

    /** What a blow is, as the protections tell blows apart. */
    static ArmourRules.Harm kind(DamageSource source) {
        return new ArmourRules.Harm(!source.isUnblockable(), source.isFireDamage(),
                source == DamageSource.fall, source.isProjectile());
    }

    /** A blow struck in hand by a creature or a player: no missile, fire, blast or spell. */
    static boolean isMelee(DamageSource source) {
        Entity striker = source.getEntity();
        return striker instanceof EntityLivingBase && source.getSourceOfDamage() == striker
                && !source.isProjectile() && !source.isExplosion() && !source.isFireDamage()
                && !source.isMagicDamage() && !source.isUnblockable();
    }

    /** Standing Firm holds while its wearer sneaks or stands still in four heavy pieces. */
    private static boolean standingFirm(EntityPlayerMP player, PerkIndex perks) {
        if (!perks.has(PerkEffect.STANDING_FIRM)) {
            return false;
        }
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        boolean still = player.ridingEntity == null
                && dx * dx + dz * dz < STANDING_SPEED * STANDING_SPEED;
        return player.isSneaking() || still;
    }

    /** A fresh or renewed poison lasts only {@code left} of what it would. */
    private static void shortenPoison(EntityPlayerMP player, double left) {
        PotionEffect poison = player.getActivePotionEffect(Potion.poison);
        UUID id = player.getUniqueID();
        synchronized (POISONS) {
            if (poison == null) {
                POISONS.remove(id);
                return;
            }
            Integer before = POISONS.get(id);
            int duration = poison.getDuration();
            if (left < 1.0D && (before == null || duration > before.intValue())) {
                duration = Math.max(1, (int) Math.round(duration * left));
                player.removePotionEffect(Potion.poison.id);
                player.addPotionEffect(new PotionEffect(Potion.poison.id, duration,
                        poison.getAmplifier(), poison.getIsAmbient()));
            }
            POISONS.put(id, Integer.valueOf(duration));
        }
    }

    /** Puts a modifier in place at {@code amount}, none at 0, replacing one of another size. */
    private static void set(IAttributeInstance attribute, UUID id, String name, double amount,
                            int operation) {
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
        if (amount != 0.0D) {
            attribute.applyModifier(new AttributeModifier(id, "Lost Tales " + name, amount,
                    operation).setSaved(false));
        }
    }
}
