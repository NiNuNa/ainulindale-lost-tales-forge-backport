package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

/** Vanilla-compatible chat draw pass with heads and optional time-based entry easing. */
final class LostTalesChatOverlayRenderer {
    static final int CHAT_BACKDROP_RGB = 0x000000;
    private static final float BACKDROP_FADE_START = 0.75F;
    private static final Field DRAWN_LINES = findField("field_146253_i");
    private static final Field SCROLL_POSITION = findField("field_146250_j");
    private static final Field SCROLLED = findField("field_146251_k");
    private static int lastOffsetX;
    private static int lastOffsetY;

    private LostTalesChatOverlayRenderer() {}

    static boolean draw(Minecraft minecraft, int offsetX, int offsetY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.gameSettings.chatVisibility
                == EntityPlayer.EnumChatVisibility.HIDDEN
                || DRAWN_LINES == null || SCROLL_POSITION == null
                || SCROLLED == null) {
            return false;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        try {
            List<ChatLine> lines = getDrawnLines(chat);
            int scrollPosition = SCROLL_POSITION.getInt(chat);
            boolean scrolled = SCROLLED.getBoolean(chat);
            drawVanillaCompatible(
                    minecraft, chat, lines, scrollPosition, scrolled,
                    offsetX, offsetY);
            lastOffsetX = offsetX;
            lastOffsetY = offsetY;
            return true;
        } catch (IllegalAccessException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static List<ChatLine> getDrawnLines(GuiNewChat chat)
            throws IllegalAccessException {
        if (chat == null || DRAWN_LINES == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<ChatLine> lines = (List<ChatLine>)DRAWN_LINES.get(chat);
        return lines;
    }

    static int getScrollPosition(GuiNewChat chat)
            throws IllegalAccessException {
        return chat == null || SCROLL_POSITION == null
                ? 0 : SCROLL_POSITION.getInt(chat);
    }

    static float getEntryDisplacement() {
        return entryDisplacement(0);
    }

    static float getEntryDisplacement(int scrollPosition) {
        return entryDisplacement(scrollPosition);
    }

    static int getLastOffsetX() {
        return lastOffsetX;
    }

    static int getLastOffsetY() {
        return lastOffsetY;
    }

    private static void drawVanillaCompatible(
            Minecraft minecraft, GuiNewChat chat,
            List<ChatLine> lines, int scrollPosition, boolean scrolled,
            int offsetX, int offsetY) {
        int visibleLineCount = chat.func_146232_i();
        int eligibleLineCount = 0;
        int totalLineCount = lines.size();
        float opacity = minecraft.gameSettings.chatOpacity * 0.9F + 0.1F;
        if (totalLineCount <= 0) {
            return;
        }

        boolean open = chat.getChatOpen();
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        FontRenderer font = minecraft.fontRenderer;

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(offsetX + 2.0F,
                    offsetY + 20.0F + entryDisplacement(scrollPosition),
                    0.0F);
            GL11.glScalef(scale, scale, 1.0F);

            for (int index = 0;
                 index + scrollPosition < lines.size()
                         && index < visibleLineCount;
                 index++) {
                ChatLine line = lines.get(index + scrollPosition);
                if (line == null) {
                    continue;
                }
                int age = minecraft.ingameGUI.getUpdateCounter()
                        - line.getUpdatedCounter();
                if (age >= 200 && !open) {
                    continue;
                }
                double fade = 1.0D - age / 200.0D;
                fade = Math.max(0.0D,
                        Math.min(1.0D, fade * 10.0D));
                fade *= fade;
                int alpha = open ? 255 : (int)(255.0D * fade);
                alpha = (int)(alpha * opacity);
                alpha = (int)(alpha * entryOpacity(line));
                eligibleLineCount++;
                if (alpha <= 3) {
                    continue;
                }

                int y = -index * 9;
                drawChatBackdrop(
                        -2, y - 9, unscaledWidth + 4, y, alpha / 2);
                GL11.glEnable(GL11.GL_BLEND);
                IChatComponent component = line.func_151461_a();
                GL11.glPushMatrix();
                GL11.glTranslatef(0.0F, y - 8.0F, 0.0F);
                ChatHeadMarker.Data marker = findMarker(component);
                LostTalesChatVisualStyle.drawFormatted(font,
                        component, marker, 0, 0, alpha);
                drawHead(minecraft, font, component,
                        -0.5F, alpha);
                GL11.glPopMatrix();
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }

            if (open) {
                int fontHeight = font.FONT_HEIGHT;
                GL11.glTranslatef(-3.0F, 0.0F, 0.0F);
                int fullHeight = totalLineCount * fontHeight
                        + totalLineCount;
                int visibleHeight = eligibleLineCount * fontHeight
                        + eligibleLineCount;
                int scrollOffset = scrollPosition * visibleHeight
                        / Math.max(1, totalLineCount);
                int thumbHeight = visibleHeight * visibleHeight
                        / Math.max(1, fullHeight);
                if (fullHeight != visibleHeight) {
                    int thumbAlpha = scrollOffset > 0 ? 170 : 96;
                    int thumbColor = scrolled ? 13382451 : 3355562;
                    Gui.drawRect(0, -scrollOffset, 2,
                            -scrollOffset - thumbHeight,
                            thumbColor + (thumbAlpha << 24));
                    Gui.drawRect(2, -scrollOffset, 1,
                            -scrollOffset - thumbHeight,
                            13421772 + (thumbAlpha << 24));
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
        }
    }

    static int backdropFadeStart(int width) {
        return Math.max(0, Math.round(width * BACKDROP_FADE_START));
    }

    /** Neutral vanilla-black backdrop with a smooth transparent right edge. */
    private static void drawChatBackdrop(
            int left, int top, int right, int bottom, int alpha) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (right <= left || bottom <= top || safeAlpha <= 0) {
            return;
        }
        int fadeStart = Math.min(right,
                left + backdropFadeStart(right - left));
        Gui.drawRect(left, top, fadeStart, bottom,
                (safeAlpha << 24) | CHAT_BACKDROP_RGB);
        if (fadeStart >= right) {
            return;
        }

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB, 0);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(CHAT_BACKDROP_RGB, safeAlpha);
        tessellator.addVertex(fadeStart, top, 0.0D);
        tessellator.addVertex(fadeStart, bottom, 0.0D);
        tessellator.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static void drawHead(Minecraft minecraft, FontRenderer font,
                                 IChatComponent line, float y, int alpha) {
        int x = 0;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                float opacity = alpha / 255.0F;
                if (marker.accountIdentity) {
                    drawAccountHeadShadow(minecraft, marker, x, y,
                            opacity);
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            minecraft, marker.senderId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else {
                    drawCharacterHeadShadow(minecraft, marker, x, y,
                            opacity);
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            minecraft, marker.senderId, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                }
                return;
            }
            // getFormattedText() recursively includes a component's siblings.
            // Measuring only this node keeps the marker aligned after vanilla
            // has split the structured message into wrapped ChatLines.
            String nodeText = part.getChatStyle().getFormattingCode()
                    + part.getUnformattedTextForChat();
            x += font.getStringWidth(nodeText);
        }
    }

    private static void drawAccountHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            int x, float y, float opacity) {
        float red = ((LostTalesChatVisualStyle.SHADOW >> 16) & 0xFF)
                / 255.0F;
        float green = ((LostTalesChatVisualStyle.SHADOW >> 8) & 0xFF)
                / 255.0F;
        float blue = (LostTalesChatVisualStyle.SHADOW & 0xFF) / 255.0F;
        LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                minecraft, marker.senderId, x + 1.25F, y + 0.75F, 8.0F,
                red, green, blue, opacity * 0.55F);
    }

    private static void drawCharacterHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            int x, float y, float opacity) {
        float red = ((LostTalesChatVisualStyle.SHADOW >> 16) & 0xFF)
                / 255.0F;
        float green = ((LostTalesChatVisualStyle.SHADOW >> 8) & 0xFF)
                / 255.0F;
        float blue = (LostTalesChatVisualStyle.SHADOW & 0xFF) / 255.0F;
        LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                minecraft, marker.senderId, marker.skinId,
                x + 1.25F, y + 0.75F, 8.0F,
                red, green, blue, opacity * 0.55F);
    }

    private static ChatHeadMarker.Data findMarker(IChatComponent line) {
        if (line == null) {
            return null;
        }
        for (Object value : line) {
            if (value instanceof IChatComponent) {
                ChatHeadMarker.Data marker = ChatHeadMarker.decode(
                        (IChatComponent)value);
                if (marker != null) {
                    return marker;
                }
            }
        }
        return null;
    }

    private static float entryDisplacement(int scrollPosition) {
        if (!LostTalesConfig.enableChatAnimations || scrollPosition != 0) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        float progress = Math.min(1.0F,
                (System.nanoTime() - started) / (float)duration);
        return LostTalesChatMotion.message(progress).stackOffsetY;
    }

    private static float entryOpacity(ChatLine line) {
        if (!LostTalesConfig.enableChatAnimations || line == null
                || line.getUpdatedCounter()
                != LostTalesChatPresentation.getLastMessageUpdateCounter()) {
            return 1.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 1.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        return LostTalesChatMotion.message(
                (System.nanoTime() - started) / (float)duration).opacity;
    }

    private static Field findField(String name) {
        try {
            Field field = GuiNewChat.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
