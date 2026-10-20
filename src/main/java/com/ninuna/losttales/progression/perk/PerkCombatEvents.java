package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.compat.lotr.LotrCountHooks;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.progression.FellowshipCompanions;
import com.ninuna.losttales.progression.count.Things;
import com.ninuna.losttales.progression.skill.PerkEffect;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * The combat and defence perks (PROG-06): what a blow deals and what it
 * leaves behind, and what a blow to the player takes. Runs before the
 * counting reads the blow, on the server alone.
 */
public final class PerkCombatEvents {
    /** A mount moving this many blocks a tick gallops. */
    private static final double GALLOP_SPEED = 0.25D;
    /** A foe closing in at this many blocks a tick charges. */
    private static final double CHARGE_SPEED = 0.08D;
    /** A player moving less than this a tick holds their ground. */
    private static final double STANDING_SPEED = 0.03D;
    private static final Random RANDOM = new Random();
    private static final List<PoisonDue> POISONS = new ArrayList<PoisonDue>();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingHurt(LivingHurtEvent event) {
        EntityLivingBase target = event.entityLiving;
        if (event.isCanceled() || target == null || target.worldObj == null
                || target.worldObj.isRemote || !(event.ammount > 0.0F)
                || PerkRuntime.isCleaving()) {
            return;
        }
        Entity source = event.source.getEntity();
        if (source instanceof EntityPlayerMP && source != target) {
            event.ammount = struck((EntityPlayerMP) source, target, event.source, event.ammount);
        }
        if (target instanceof EntityPlayerMP) {
            event.ammount = taken((EntityPlayerMP) target, event.source, event.ammount);
        } else if (event.source == DamageSource.fall
                && target.riddenByEntity instanceof EntityPlayer) {
            event.ammount *= (float) (1.0D - Math.min(1.0D, Perks.of(
                    (EntityPlayer) target.riddenByEntity).total(PerkEffect.MOUNTED_FALL)));
        }
    }

    /** A blow the player strikes: what it deals, and what it leaves behind. */
    private static float struck(EntityPlayerMP player, EntityLivingBase target,
                                DamageSource source, float amount) {
        PerkIndex perks = Perks.of(player);
        Entity direct = source.getSourceOfDamage();
        boolean melee = direct == player;
        ItemStack weapon = melee ? player.getHeldItem() : LotrCountHooks.projectileItem(direct);
        PerkIndex.WeaponMatch match = melee ? Perks.weapon(weapon)
                : Perks.kind(LotrWeaponKinds.kindOfProjectile(direct), weapon);
        long now = player.worldObj.getTotalWorldTime();
        PerkRuntime.Moments moments = PerkRuntime.of(player);
        double bonus = perks.total(PerkEffect.WEAPON_DAMAGE, match);
        if (melee) {
            TakenPerk riposte = perks.first(PerkEffect.RIPOSTE);
            if (riposte != null && now - moments.lastBlock
                    <= (long) (riposte.param("seconds", 1.0D) * 20.0D)) {
                bonus += perks.total(PerkEffect.RIPOSTE, match);
            }
            if (player.fallDistance > 0.0F && !player.onGround && !player.isInWater()
                    && player.ridingEntity == null) {
                bonus += perks.total(PerkEffect.FALLING_STRIKE, match);
            }
            if (holdsGround(player) && closesIn(target, player)) {
                bonus += perks.total(PerkEffect.BRACE, match);
            }
            if (galloping(player) && !moments.chargeSpent) {
                double charge = perks.total(PerkEffect.CHARGE);
                if (charge > 0.0D) {
                    bonus += charge;
                    moments.chargeSpent = true;
                }
            }
            for (TakenPerk breaker : perks.taken(PerkEffect.BEAST_BREAKER)) {
                if (match.matches(breaker.getSkill().weapons())
                        && (target.ridingEntity != null
                        || breaker.getPerk().getWith().contains(Things.nameOf(target)))) {
                    bonus += breaker.amount();
                }
            }
        }
        float dealt = (float) (amount * (1.0D + bonus));
        double sunder = perks.total(PerkEffect.SUNDER, match);
        if (sunder > 0.0D && !source.isUnblockable()) {
            dealt = (float) passArmour(dealt, target.getTotalArmorValue(), sunder);
        }
        afterBlow(player, target, direct, melee, match, perks, dealt);
        return dealt;
    }

    /**
     * Damage before armour that leaves {@code share} of what the armour
     * would keep passing through, as vanilla shares a blow out.
     */
    static double passArmour(double amount, int armour, double share) {
        double kept = Math.min(20, Math.max(0, armour)) / 25.0D;
        if (kept <= 0.0D) {
            return amount;
        }
        double passed = Math.min(1.0D, share);
        return amount * (1.0D - kept * (1.0D - passed)) / (1.0D - kept);
    }

    private static void afterBlow(EntityPlayerMP player, EntityLivingBase target, Entity direct,
                                  boolean melee, PerkIndex.WeaponMatch match, PerkIndex perks,
                                  float dealt) {
        if (direct instanceof EntityArrow) {
            TakenPerk pinning = perks.first(PerkEffect.PINNING_SHOT);
            if (pinning != null) {
                int level = Math.max(1, (int) pinning.param("level", 1.0D));
                target.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id,
                        (int) Math.round(pinning.amount() * 20.0D), level - 1));
            }
            if (RANDOM.nextDouble() < perks.total(PerkEffect.ARROW_RECOVERY)) {
                ItemStack arrow = LotrPerkHooks.arrowItem((EntityArrow) direct);
                target.worldObj.spawnEntityInWorld(new EntityItem(target.worldObj,
                        target.posX, target.posY + 0.5D, target.posZ, arrow));
            }
        }
        if (!melee) {
            return;
        }
        if (target.ridingEntity != null
                && RANDOM.nextDouble() < perks.total(PerkEffect.UNHORSE, match)) {
            target.mountEntity(null);
        }
        double stagger = perks.total(PerkEffect.STAGGER, match);
        if (stagger > 0.0D) {
            double dx = target.posX - player.posX;
            double dz = target.posZ - player.posZ;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length > 1.0E-4D) {
                // Vanilla halves the motion before its own knockback, so the push is doubled.
                double push = stagger * 0.8D;
                target.addVelocity(dx / length * push, 0.05D, dz / length * push);
                target.velocityChanged = true;
            }
        }
        double poison = 0.0D;
        for (TakenPerk dagger : perks.taken(PerkEffect.DAGGER_WORK)) {
            if (match.matches(dagger.getPerk().getWith().isEmpty()
                    ? dagger.getSkill().weapons() : dagger.getPerk().getWith())) {
                poison += dagger.param("poison", 0.0D);
            }
        }
        if (poison > 0.0D) {
            synchronized (POISONS) {
                POISONS.add(new PoisonDue(target, poison));
            }
        }
        TakenPerk cleave = perks.first(PerkEffect.CLEAVE);
        double share = perks.total(PerkEffect.CLEAVE, match);
        if (cleave != null && share > 0.0D && !PerkRuntime.isCleaving()) {
            cleave(player, target, dealt * share, cleave.param("radius", 1.5D));
        }
    }

    /** A swing that also strikes the foes beside its target, as LOTR lets the player strike them. */
    private static void cleave(EntityPlayerMP player, EntityLivingBase target, double damage,
                               double radius) {
        List<?> near = target.worldObj.getEntitiesWithinAABBExcludingEntity(target,
                target.boundingBox.expand(radius, 0.5D, radius));
        PerkRuntime.setCleaving(true);
        try {
            for (Object entry : near) {
                if (!(entry instanceof EntityLivingBase) || entry == player
                        || entry == player.ridingEntity || ((Entity) entry).isDead) {
                    continue;
                }
                EntityLivingBase beside = (EntityLivingBase) entry;
                if (beside instanceof EntityPlayer || !LotrPerkHooks.mayStrike(player, beside)) {
                    continue;
                }
                beside.attackEntityFrom(DamageSource.causePlayerDamage(player), (float) damage);
            }
        } finally {
            PerkRuntime.setCleaving(false);
        }
    }

    /** A blow the player takes: what armour, falls, fire, companions and a last stand leave of it. */
    private static float taken(EntityPlayerMP player, DamageSource source, float amount) {
        PerkIndex perks = Perks.of(player);
        if (!perks.has(PerkEffect.ARMOUR_ABSORB) && !perks.has(PerkEffect.FALL_DAMAGE)
                && !perks.has(PerkEffect.MOUNTED_FALL) && !perks.has(PerkEffect.BURN_SHORTER)
                && !perks.has(PerkEffect.PHALANX) && !perks.has(PerkEffect.LAST_STAND)) {
            return amount;
        }
        double left = amount;
        if (source == DamageSource.fall) {
            left *= 1.0D - Math.min(1.0D, perks.total(PerkEffect.FALL_DAMAGE)
                    + (player.ridingEntity != null ? perks.total(PerkEffect.MOUNTED_FALL) : 0.0D));
        }
        if (source.isFireDamage() && player.isBurning()
                && RANDOM.nextDouble() < perks.total(PerkEffect.BURN_SHORTER)) {
            player.extinguish();
        }
        double absorb = perks.total(PerkEffect.ARMOUR_ABSORB);
        int armour = player.getTotalArmorValue();
        if (absorb > 0.0D && armour > 0 && !source.isUnblockable()) {
            double kept = Math.min(20, armour) / 25.0D;
            double more = absorb * Math.min(20, armour) / 20.0D;
            left *= Math.max(0.0D, 1.0D - kept - more) / (1.0D - kept);
        }
        TakenPerk phalanx = perks.first(PerkEffect.PHALANX);
        if (phalanx != null && FellowshipCompanions.anyWithin(player, phalanx.param("radius", 4.0D))) {
            left *= 1.0D - Math.min(1.0D, perks.total(PerkEffect.PHALANX));
        }
        TakenPerk lastStand = perks.first(PerkEffect.LAST_STAND);
        if (lastStand != null) {
            long now = player.worldObj.getTotalWorldTime();
            PerkRuntime.Moments moments = PerkRuntime.of(player);
            if (now > moments.lastStandUntil && now >= moments.lastStandReady
                    && player.getHealth() - left
                    < player.getMaxHealth() * lastStand.param("health", 0.25D)) {
                moments.lastStandUntil = now + (long) (lastStand.param("seconds", 5.0D) * 20.0D);
                moments.lastStandReady = now + (long) (lastStand.param("cooldown", 60.0D) * 20.0D);
            }
            if (now <= moments.lastStandUntil) {
                left *= 1.0D - Math.min(1.0D, lastStand.amount());
            }
        }
        return (float) left;
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled() || event.entityLiving == null || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote
                || !(event.source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP killer = (EntityPlayerMP) event.source.getEntity();
        Entity direct = event.source.getSourceOfDamage();
        PerkIndex.WeaponMatch match = direct == killer ? Perks.weapon(killer.getHeldItem())
                : Perks.kind(LotrWeaponKinds.kindOfProjectile(direct),
                        LotrCountHooks.projectileItem(direct));
        PerkIndex perks = Perks.of(killer);
        double heal = perks.total(PerkEffect.KILL_HEAL, match);
        if (heal > 0.0D) {
            killer.heal((float) heal);
        }
        for (TakenPerk speed : perks.taken(PerkEffect.KILL_SPEED)) {
            if (match.matches(speed.getSkill().weapons())) {
                int level = Math.max(1, (int) speed.param("level", 1.0D));
                killer.addPotionEffect(new PotionEffect(Potion.moveSpeed.id,
                        (int) Math.round(speed.amount() * 20.0D), level - 1));
            }
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
        if (player.isBlocking()) {
            moments.blocking = true;
        } else if (moments.blocking) {
            moments.blocking = false;
            moments.lastBlock = player.worldObj.getTotalWorldTime();
        }
        if (!galloping(player)) {
            moments.chargeSpent = false;
        }
        if (player.isSprinting()) {
            double share = Perks.of(player).total(PerkEffect.SPRINT_HUNGER,
                    Perks.weapon(player.getHeldItem()));
            if (share > 0.0D) {
                double dx = player.posX - player.prevPosX;
                double dz = player.posZ - player.prevPosZ;
                // Vanilla wears a sprinter 0.1 exhaustion a block; part of it comes back.
                player.addExhaustion((float) (-0.1D * Math.sqrt(dx * dx + dz * dz)
                        * Math.min(1.0D, share)));
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        List<PoisonDue> due;
        synchronized (POISONS) {
            if (POISONS.isEmpty()) {
                return;
            }
            due = new ArrayList<PoisonDue>(POISONS);
            POISONS.clear();
        }
        for (PoisonDue poison : due) {
            PotionEffect effect = poison.target.isDead ? null
                    : poison.target.getActivePotionEffect(Potion.poison);
            if (effect != null) {
                poison.target.addPotionEffect(new PotionEffect(Potion.poison.id,
                        (int) Math.round(effect.getDuration() * (1.0D + poison.share)),
                        effect.getAmplifier()));
            }
        }
    }

    public static void clear() {
        synchronized (POISONS) {
            POISONS.clear();
        }
    }

    private static boolean galloping(EntityPlayer player) {
        Entity mount = player.ridingEntity;
        if (!(mount instanceof EntityLiving)) {
            return false;
        }
        double dx = mount.posX - mount.prevPosX;
        double dz = mount.posZ - mount.prevPosZ;
        return dx * dx + dz * dz > GALLOP_SPEED * GALLOP_SPEED;
    }

    private static boolean holdsGround(EntityPlayer player) {
        double dx = player.posX - player.prevPosX;
        double dz = player.posZ - player.prevPosZ;
        return player.ridingEntity == null && dx * dx + dz * dz < STANDING_SPEED * STANDING_SPEED;
    }

    /** Whether the foe was coming at the player as it was struck. */
    private static boolean closesIn(EntityLivingBase foe, EntityPlayer player) {
        double toX = player.posX - foe.posX;
        double toZ = player.posZ - foe.posZ;
        double length = Math.sqrt(toX * toX + toZ * toZ);
        if (length < 1.0E-4D) {
            return false;
        }
        double moveX = foe.posX - foe.prevPosX;
        double moveZ = foe.posZ - foe.prevPosZ;
        return (moveX * toX + moveZ * toZ) / length > CHARGE_SPEED;
    }

    /** A poisoned dagger's poison to lengthen once LOTR has put it on. */
    private static final class PoisonDue {
        final EntityLivingBase target;
        final double share;

        PoisonDue(EntityLivingBase target, double share) {
            this.target = target;
            this.share = share;
        }
    }
}
