package com.ninuna.losttales.client.chat;

import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.IChatComponent;

/**
 * Called by the coremod inside {@code GuiNewChat.func_146237_a} once
 * vanilla has wrapped a message and before the wrapped lines enter the
 * drawn history. A Lost Tales line (one carrying a body marker) is
 * re-wrapped by {@link ChatLineWrapper} so continuation lines indent
 * under the body; every other line keeps vanilla's result. Because the
 * hook sits inside vanilla's own method, the history, scroll, trimming and
 * resize re-wrap bookkeeping stay vanilla's, and a resize or GUI-scale
 * change lays the message out again from its original component at the
 * new width. Any failure returns vanilla's lines.
 */
public final class LostTalesChatWrapHooks {
    private static volatile boolean failureLogged;

    private LostTalesChatWrapHooks() {}

    public static ArrayList<IChatComponent> wrap(
            GuiNewChat chat, IChatComponent root,
            ArrayList<IChatComponent> vanillaLines) {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (chat == null || root == null || minecraft == null
                    || minecraft.fontRenderer == null) {
                return vanillaLines;
            }
            final FontRenderer font = minecraft.fontRenderer;
            // With chat colours off the renderer strips every code before
            // drawing, so the layout must measure the same stripped text.
            final boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
            int width = ChatWindowPlacement.wrapWidth(
                    chat.func_146228_f(), chat.func_146244_h());
            List<IChatComponent> lines = ChatLineWrapper.wrap(
                    new ChatLineWrapper.TextMetrics() {
                        @Override
                        public int width(String text) {
                            return font.getStringWidth(colours ? text
                                    : LostTalesChatVisualStyle.stripCodes(text));
                        }
                    }, root, width);
            if (lines == null || lines.isEmpty()) {
                return vanillaLines;
            }
            return new ArrayList<IChatComponent>(lines);
        } catch (RuntimeException exception) {
            if (!failureLogged) {
                failureLogged = true;
                FMLLog.warning("[LostTales] Chat line layout failed; "
                        + "falling back to vanilla wrapping: %s", exception);
            }
            return vanillaLines;
        }
    }
}
