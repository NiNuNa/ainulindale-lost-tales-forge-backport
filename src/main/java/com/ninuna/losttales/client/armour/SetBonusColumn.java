package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.armour.Armour;
import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.SetBonusesInForce;
import com.ninuna.losttales.client.gui.inventory.ContainerScreenAccess;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiCutName;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.util.LostTalesLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * The set bonuses in force, in the effect column left of an inventory
 * that shows the player's potion effects (CONTENT-12): a popup of the
 * windows for each, under vanilla's potion boxes, holding the worn
 * helmet, the set's name in ivory and what the bonus does in the aside
 * tone, each line cut and fading at its end and sliding along while the
 * pointer rests on the entry (LOOK-37).
 *
 * <p>It stands in the screen's button list, so the screen draws it in its
 * own pass, after its background and before its slots, the item held on
 * the pointer and the tooltip, which stand over it. It is never enabled
 * and never takes a press: the pointer keeps its arrow over it and a
 * click goes to the screen as if nothing stood there. What it shows is
 * read from the armour worn every frame, so an entry comes the moment its
 * set is complete and goes the moment the set is broken.</p>
 */
@SideOnly(Side.CLIENT)
public final class SetBonusColumn extends GuiButton {
    /** An id no screen's actions answer to; the column is never pressed anyway. */
    private static final int ID = -24187;
    private static boolean failureLogged;

    private final GuiContainer screen;
    private boolean failed;

    public SetBonusColumn(GuiContainer screen) {
        super(ID, 0, 0, 0, 0, "");
        this.screen = screen;
        this.enabled = false;
    }

    @Override
    public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
        return false;
    }

    @Override
    public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
        if (!this.visible || this.failed || minecraft == null || minecraft.thePlayer == null
                || minecraft.fontRenderer == null) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            draw(minecraft, minecraft.thePlayer, mouseX, mouseY);
        } catch (ContainerScreenAccess.Unavailable unavailable) {
            // The accessor has said so once; this screen stops asking.
            this.failed = true;
        } catch (RuntimeException exception) {
            this.failed = true;
            if (!failureLogged) {
                failureLogged = true;
                LostTalesLog.warning("The set bonuses beside %s could not be drawn: %s",
                        this.screen.getClass().getName(), exception.toString());
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void draw(Minecraft minecraft, EntityPlayer player, int mouseX, int mouseY)
            throws ContainerScreenAccess.Unavailable {
        List<SetBonusesInForce.Entry> entries = SetBonusesInForce.of(Armour.worn(player),
                Armour.settings(player));
        if (entries.isEmpty()) {
            return;
        }
        int[] lines = new int[entries.size()];
        for (int index = 0; index < lines.length; index++) {
            lines[index] = entries.get(index).effectLines();
        }
        List<SetBonusColumnLayout.Box> boxes = SetBonusColumnLayout.place(
                ContainerScreenAccess.guiLeft(this.screen),
                ContainerScreenAccess.guiTop(this.screen), this.screen.height,
                potionsShown(player), lines);
        ItemStack icon = icon(player);
        for (int index = 0; index < boxes.size(); index++) {
            drawEntry(minecraft, entries.get(index), boxes.get(index), icon, mouseX, mouseY);
        }
    }

    /** The potion boxes vanilla draws in the column: none unless it made room for them. */
    private int potionsShown(EntityPlayer player) throws ContainerScreenAccess.Unavailable {
        if (!(this.screen instanceof InventoryEffectRenderer)
                || !ContainerScreenAccess.showsEffects((InventoryEffectRenderer) this.screen)) {
            return 0;
        }
        return player.getActivePotionEffects().size();
    }

    private void drawEntry(Minecraft minecraft, SetBonusesInForce.Entry entry,
                           SetBonusColumnLayout.Box box, ItemStack icon, int mouseX, int mouseY) {
        WindowStyle.drawPopup(box.left, box.top, box.right(), box.bottom(), 1.0F);
        if (icon != null) {
            LostTalesUiInk.beginContent();
            LostTalesUiItemIcon.drawFitted(minecraft, icon, box.iconLeft(), box.iconTop(),
                    SetBonusColumnLayout.ICON, 255);
        }
        LostTalesUiInk.beginContent();
        FontRenderer font = minecraft.fontRenderer;
        boolean rested = LostTalesUiHitBox.contains(mouseX, mouseY, box.left, box.top,
                box.width, box.height);
        String key = entry.key();
        LostTalesUiCutName.drawText(font, SetBonusWords.name(entry), box.textLeft(),
                box.textTop(), box.textRoom(), LostTalesUiInk.IVORY, 255, this, key, rested);
        List<String> effects = SetBonusWords.effects(entry);
        for (int line = 0; line < box.effectLines && line < effects.size(); line++) {
            LostTalesUiCutName.drawText(font, effects.get(line), box.textLeft(),
                    box.textTop() + (line + 1) * WindowStyle.LINE_HEIGHT, box.textRoom(),
                    WindowStyle.asideRgb(), 255, this, key + "#" + line, rested);
        }
    }

    /** The worn helmet, every set's face; another worn piece where none is worn. */
    private static ItemStack icon(EntityPlayer player) {
        ItemStack helmet = player.getCurrentArmor(ArmourSlot.HELMET.getInventoryIndex());
        if (helmet != null) {
            return helmet;
        }
        for (ArmourSlot slot : ArmourSlot.values()) {
            ItemStack worn = player.getCurrentArmor(slot.getInventoryIndex());
            if (worn != null) {
                return worn;
            }
        }
        return null;
    }
}
