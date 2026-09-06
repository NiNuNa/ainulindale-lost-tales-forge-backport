package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * Called from patched LOTR code: the hover card LOTR draws for one of
 * its achievements in the chat, drawn as the chat's own card instead.
 *
 * <p>LOTR draws that card itself, after every chat screen, through a
 * {@code GuiScreen} of its own ({@code LOTRGuiAchievementHoverEvent})
 * and vanilla's hovering-text routine, so the yellow and grey of its
 * lines never met the palette every other card in the chat is drawn
 * in. The coremod puts a call to {@link #drawLines} at the head of
 * that routine: the same lines, drawn by
 * {@link LostTalesChatHoverCard#drawTextCard}, which maps every colour
 * code onto the palette exactly as the cards for vanilla achievements
 * and text components are. The hook answers whether it drew; when it
 * did not, LOTR's own drawing runs as before, so a failure here costs
 * the palette and nothing else. Without the patch LOTR's card keeps
 * its own colours.</p>
 */
public final class LostTalesLotrAchievementHoverHook {

    private LostTalesLotrAchievementHoverHook() {}

    /**
     * Draws the card for the lines at the pointer, in the palette.
     *
     * @return whether the card was drawn; false hands the draw back to
     *         LOTR
     */
    public static boolean drawLines(GuiScreen screen, List<?> lines,
                                    int mouseX, int mouseY) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (screen == null || lines == null || lines.isEmpty()
                    || minecraft == null || minecraft.fontRenderer == null) {
                return false;
            }
            List<String> text = new ArrayList<String>(lines.size());
            for (Object line : lines) {
                text.add(line == null ? "" : line.toString());
            }
            LostTalesChatHoverCard.drawTextCard(minecraft, text, mouseX,
                    mouseY, screen.width, screen.height);
            return true;
        } catch (RuntimeException failed) {
            return false;
        }
    }

    /** The one-line form of {@link #drawLines}. */
    public static boolean drawLine(GuiScreen screen, String line,
                                   int mouseX, int mouseY) {
        return line != null && drawLines(screen,
                Collections.singletonList(line), mouseX, mouseY);
    }
}
