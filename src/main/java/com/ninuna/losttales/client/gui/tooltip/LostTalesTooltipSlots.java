package com.ninuna.losttales.client.gui.tooltip;

import com.ninuna.losttales.block.vessel.VesselTooltip;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiRules;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/**
 * Item slots inside tooltips: what a vessel holds, drawn as its slots with
 * the items in them and their counts (CONTENT-09).
 *
 * <p>A tooltip is a list of strings, so the slots travel as a marked block
 * of lines: the lines of words listing the same items, each carrying
 * {@link #BLOCK}, the first also {@link #PANEL} and the id of the slots it
 * stands for. A line cannot carry an item's NBT, so the stacks wait here
 * under that id. {@link LostTalesTooltipHooks} draws the slots in place of
 * the whole block. Anything else drawing the lines shows the words, the
 * marks being private-use characters the font draws as nothing, and the
 * hook falls back to the words too for an id it no longer holds, so no
 * item is ever left out unsaid.</p>
 *
 * <p>As with the key icons, nothing is marked until the coremod has
 * patched the tooltip renderer.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesTooltipSlots {
    /** Every line of a marked block carries this. */
    static final char BLOCK = '';
    /** The block's first line carries this, then the slots' id in two characters. */
    static final char PANEL = '';
    /** The id's two bytes, shifted into a private-use range clear of the key icons'. */
    private static final int ID_BASE = 0xE400;
    private static final int ID_MASK = 0xFFFF;
    /** How many sets of slots are kept: a tooltip is built anew each frame and drawn at once. */
    static final int KEPT = 16;

    /** A slot: an item's icon with a pixel clear round it. */
    public static final int SLOT_SIZE = LostTalesUiItemIcon.ICON_TEXELS + 2;
    /** Between two slots, the windows' hairline parting them. */
    public static final int SLOT_GAP = WindowStyle.DIVIDER_WIDTH;
    /** From a slot's edge to its item. */
    private static final int ITEM_INSET = (SLOT_SIZE - LostTalesUiItemIcon.ICON_TEXELS) / 2;
    /**
     * The framed button round the slots keeps its outer ring of surface
     * outside the row it stands in, as the window frame stands outside its
     * window. Here that ring is the tooltip's own surface, and the ink
     * lines up with the words above and below it.
     */
    static final int OUTER_RING = LostTalesUiFramedButton.EDGE
            - LostTalesUiWindowFrame.EDGE_WIDTH;
    /** From the ink's outer edge to the slots: the ink and the clear pixels inside it. */
    static final int SLOTS_INSET = LostTalesUiFramedButton.INSET - OUTER_RING;
    /**
     * Where a slot's count stands from its icon, as vanilla places it: its
     * advance ending a pixel past the icon, so the last stroke lands on the
     * icon's last column, and its top nine rows down.
     */
    private static final int COUNT_RIGHT = LostTalesUiItemIcon.ICON_TEXELS + 1;
    private static final int COUNT_TOP = 9;

    private static final Slots[] KEPT_SLOTS = new Slots[KEPT];
    private static int nextId;
    private static Slots newest;

    /** The stacks one marked block stands for, as the hook draws them. */
    static final class Slots {
        final int id;
        /** One per slot shown, null for an empty one. */
        final ItemStack[] stacks;
        /** The line under the slots counting the items past them; null when every item shows. */
        final String more;
        final VesselTooltip.Grid grid;
        /** The row the slots take in the tooltip, from the frame's ink on one side to its ink on the other. */
        final int width;
        final int height;

        Slots(int id, ItemStack[] stacks, String more) {
            this.id = id;
            this.stacks = stacks;
            this.more = more;
            this.grid = VesselTooltip.grid(stacks.length, SLOT_SIZE, SLOT_GAP);
            this.width = this.grid.width + 2 * SLOTS_INSET;
            this.height = this.grid.height + 2 * SLOTS_INSET;
        }

        boolean holds(ItemStack[] others, String otherMore) {
            if (others.length != this.stacks.length || !(this.more == null
                    ? otherMore == null : this.more.equals(otherMore))) {
                return false;
            }
            for (int slot = 0; slot < others.length; slot++) {
                if (!ItemStack.areItemStacksEqual(this.stacks[slot], others[slot])) {
                    return false;
                }
            }
            return true;
        }
    }

    private LostTalesTooltipSlots() {}

    /**
     * The lines standing for slots holding {@code stacks} (one per slot
     * shown, null for an empty one) and, under them, {@code more} where
     * some items do not show: {@code lines}, the same items in words,
     * marked as one block. Unmarked where slots cannot be drawn in
     * tooltips on this installation, or where there are none.
     */
    public static List<String> mark(ItemStack[] stacks, String more,
                                    List<String> lines) {
        List<String> words = lines == null ? new ArrayList<String>() : lines;
        if (words.isEmpty() || stacks == null || stacks.length == 0
                || !LostTalesTooltipIcons.isAvailable()) {
            return words;
        }
        int id = keep(stacks, more);
        List<String> marked = new ArrayList<String>(words.size());
        for (int index = 0; index < words.size(); index++) {
            String line = words.get(index);
            marked.add(index == 0
                    ? String.valueOf(BLOCK) + PANEL + idChars(id) + line
                    : BLOCK + line);
        }
        return marked;
    }

    /** Forgets every set of slots kept: the session is over. */
    public static synchronized void clear() {
        for (int index = 0; index < KEPT; index++) {
            KEPT_SLOTS[index] = null;
        }
        newest = null;
        nextId = 0;
    }

    /**
     * Keeps {@code stacks} under a new id, or answers the newest one's
     * where it holds the same: the same tooltip, built again this frame.
     * Copies are kept, so a stack changing afterwards changes nothing here.
     */
    static synchronized int keep(ItemStack[] stacks, String more) {
        if (newest != null && newest.holds(stacks, more)) {
            return newest.id;
        }
        ItemStack[] copies = new ItemStack[stacks.length];
        for (int slot = 0; slot < stacks.length; slot++) {
            copies[slot] = stacks[slot] == null ? null : stacks[slot].copy();
        }
        int id = nextId;
        nextId = (nextId + 1) & ID_MASK;
        newest = new Slots(id, copies, more);
        KEPT_SLOTS[id % KEPT] = newest;
        return id;
    }

    /** Whether the line belongs to a marked block. */
    static boolean inBlock(String line) {
        return line != null && line.indexOf(BLOCK) >= 0;
    }

    /** The slots a block's first line stands for, or null for any other line or an id no longer kept. */
    static synchronized Slots slotsOf(String line) {
        int at = line == null ? -1 : line.indexOf(PANEL);
        if (at < 0 || at + 2 >= line.length()) {
            return null;
        }
        int high = line.charAt(at + 1) - ID_BASE;
        int low = line.charAt(at + 2) - ID_BASE;
        if (high < 0 || high > 255 || low < 0 || low > 255) {
            return null;
        }
        int id = high << 8 | low;
        Slots kept = KEPT_SLOTS[id % KEPT];
        return kept != null && kept.id == id ? kept : null;
    }

    /** The line's words, its marks taken out. */
    static String words(String line) {
        if (line == null) {
            return "";
        }
        StringBuilder words = new StringBuilder(line.length());
        for (int index = 0; index < line.length(); index++) {
            char at = line.charAt(index);
            if (at == PANEL) {
                index += 2;
            } else if (at != BLOCK) {
                words.append(at);
            }
        }
        return words.toString();
    }

    private static String idChars(int id) {
        return String.valueOf((char)(ID_BASE + (id >> 8 & 255)))
                + (char)(ID_BASE + (id & 255));
    }

    /**
     * Draws the slots from ({@code x}, {@code y}), a whole display pixel,
     * where the frame's ink begins: one framed button round them all, its
     * ink alone on the tooltip's own surface, so no second surface lies over
     * it; the windows' hairlines parting the slots; the items lit and posed
     * as in a slot, each casting the one shadow; a damaged item's bar as
     * vanilla draws it; and every count over one in the windows' ink.
     */
    static void draw(Minecraft minecraft, FontRenderer font, Slots slots,
                     int x, int y) {
        VesselTooltip.Grid grid = slots.grid;
        LostTalesUiFramedButton.drawInk(x - OUTER_RING, y - OUTER_RING,
                slots.width + 2 * OUTER_RING, slots.height + 2 * OUTER_RING,
                0.0F, 255);
        int gridX = x + SLOTS_INSET;
        int gridY = y + SLOTS_INSET;
        drawHairlines(grid, gridX, gridY);
        GL11.glPushAttrib(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_ENABLE_BIT);
        try {
            // A block's faces sort by depth. Nothing the screen drew under
            // the tooltip, its own slots' items least of all, may hide one,
            // and the depth the items leave must not hide what follows.
            clearDepth();
            for (int slot = 0; slot < grid.slots; slot++) {
                ItemStack stack = slots.stacks[slot];
                if (stack != null) {
                    LostTalesUiItemIcon.drawFitted(minecraft, stack,
                            gridX + grid.slotX(slot) + ITEM_INSET,
                            gridY + grid.slotY(slot) + ITEM_INSET,
                            LostTalesUiItemIcon.ICON_TEXELS, 255);
                }
            }
            clearDepth();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            for (int slot = 0; slot < grid.slots; slot++) {
                ItemStack stack = slots.stacks[slot];
                if (stack != null) {
                    drawOverlay(minecraft, font, stack,
                            gridX + grid.slotX(slot) + ITEM_INSET,
                            gridY + grid.slotY(slot) + ITEM_INSET);
                }
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * The hairlines parting the slots, as the windows part two controls:
     * one standing between two slots side by side, full at its middle and
     * nothing at its ends, and one lying the grid's width between two rows.
     */
    private static void drawHairlines(VesselTooltip.Grid grid, int x, int y) {
        for (int slot = 0; slot + 1 < grid.slots; slot++) {
            if (grid.slotY(slot + 1) == grid.slotY(slot)) {
                WindowStyle.drawDivider(x + grid.slotX(slot) + grid.size,
                        y + grid.slotY(slot), grid.size,
                        WindowStyle.DIVIDER_ALPHA);
            }
        }
        for (int row = 1; row < grid.rows; row++) {
            int top = y + row * (grid.size + grid.gap) - grid.gap;
            LostTalesUiRules.drawRule(x, x + grid.width, top, top + grid.gap,
                    WindowStyle.DIVIDER_ALPHA);
        }
    }

    /** The whole depth buffer cleared, whatever the depth mask was. */
    private static void clearDepth() {
        GL11.glDepthMask(true);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
    }

    /** A damaged item's bar, then its count where it holds more than one. */
    private static void drawOverlay(Minecraft minecraft, FontRenderer font,
                                    ItemStack stack, int x, int y) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT);
        try {
            // Given words of its own, vanilla draws those, here none, in
            // place of the count, and the bar as in every slot.
            RenderItem.getInstance().renderItemOverlayIntoGUI(font,
                    minecraft.getTextureManager(), stack, x, y, "");
        } catch (RuntimeException ignored) {
            // A broken modded bar must never take the tooltip down.
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (stack.stackSize > 1) {
            String count = String.valueOf(stack.stackSize);
            LostTalesUiInk.drawText(font, count,
                    x + COUNT_RIGHT - font.getStringWidth(count),
                    y + COUNT_TOP, LostTalesUiInk.IVORY, 255);
        }
    }
}
