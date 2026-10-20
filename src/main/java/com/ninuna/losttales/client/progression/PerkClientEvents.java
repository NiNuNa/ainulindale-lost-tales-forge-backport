package com.ninuna.losttales.client.progression;

import com.ninuna.losttales.compat.lotr.LotrCountHooks;
import com.ninuna.losttales.compat.lotr.LotrPerkHooks;
import com.ninuna.losttales.compat.lotr.LotrWeaponKinds;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.progression.perk.PerkFood;
import com.ninuna.losttales.progression.perk.Perks;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.WeaponKind;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * The perks only the client shows (PROG-06): Eagle Eye's zoom while a
 * bow is drawn, Horse Sense's figures by the crosshair (Nils,
 * 2026-10-19), and the cook's name on marked food.
 */
public final class PerkClientEvents {
    /** A horse's speed attribute in blocks a second. */
    private static final double BLOCKS_PER_SECOND = 43.17D;
    private static final int LINE_GAP = 8;

    @SubscribeEvent
    public void onFovUpdate(FOVUpdateEvent event) {
        EntityPlayerSP player = event.entity;
        if (player == null || !player.isUsingItem() || player.getItemInUse() == null
                || LotrWeaponKinds.kindOf(player.getItemInUse()) != WeaponKind.BOW) {
            return;
        }
        double zoom = Perks.of(player).total(PerkEffect.BOW_ZOOM,
                Perks.weapon(player.getItemInUse()));
        if (zoom > 0.0D) {
            float drawn = Math.min(1.0F, player.getItemInUseDuration()
                    / (float) LotrPerkHooks.drawTicks(player.getItemInUse()));
            event.newfov *= (float) (1.0D - Math.min(0.9D, zoom) * drawn);
        }
    }

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.CROSSHAIRS) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayerSP player = minecraft.thePlayer;
        if (player == null || minecraft.currentScreen != null || minecraft.gameSettings.hideGUI
                || !Perks.of(player).has(PerkEffect.HORSE_SENSE)) {
            return;
        }
        Entity mount = player.ridingEntity;
        MovingObjectPosition aim = minecraft.objectMouseOver;
        if (!LotrCountHooks.isMount(mount) && aim != null && aim.entityHit != null) {
            mount = aim.entityHit;
        }
        if (!LotrCountHooks.isMount(mount)) {
            return;
        }
        String line = horseSense((EntityLivingBase) mount);
        int width = minecraft.fontRenderer.getStringWidth(line);
        int x = LostTalesUiInk.centredStart(event.resolution.getScaledWidth(), width);
        int y = event.resolution.getScaledHeight() / 2 + LINE_GAP;
        LostTalesUiInk.drawText(minecraft.fontRenderer, line, x, y,
                LostTalesColors.HUD_LABEL & 0xFFFFFF, 255);
    }

    /** A mount's health, speed and jump in one line. */
    private static String horseSense(EntityLivingBase mount) {
        String health = StatCollector.translateToLocalFormatted(
                "gui.losttales.skill.horse_sense.health",
                Math.round(mount.getHealth()), Math.round(mount.getMaxHealth()));
        IAttributeInstance speed = mount.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        String line = health;
        if (speed != null) {
            line += StatCollector.translateToLocalFormatted("gui.losttales.skill.horse_sense.speed",
                    String.format(Locale.ROOT, "%.1f",
                            speed.getAttributeValue() * BLOCKS_PER_SECOND));
        }
        IAttribute jumpAttribute = LotrPerkHooks.horseJump();
        IAttributeInstance jump = mount instanceof EntityHorse && jumpAttribute != null
                ? mount.getEntityAttribute(jumpAttribute) : null;
        if (jump != null) {
            line += StatCollector.translateToLocalFormatted("gui.losttales.skill.horse_sense.jump",
                    String.format(Locale.ROOT, "%.1f", jumpHeight(jump.getAttributeValue())));
        }
        return line;
    }

    /** How high a horse of this jump strength clears, in blocks. */
    static double jumpHeight(double strength) {
        return -0.1817584952D * strength * strength * strength
                + 3.689713992D * strength * strength + 2.128599134D * strength - 0.343930367D;
    }

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        String cook = PerkFood.cookName(event.itemStack);
        if (cook != null && cook.length() > 0 && event.toolTip != null) {
            event.toolTip.add(StatCollector.translateToLocalFormatted(
                    "gui.losttales.skill.cooked_by", cook));
        }
    }
}
