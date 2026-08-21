package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.render.player.LostTalesCharacterHeadIconRenderer;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
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
    static final int CHAT_BACKDROP_RGB =
            LostTalesColors.rgb(LostTalesColors.PLUM_BLACK);
    /** Backdrop for lines that @-mention the local player. */
    static final int PING_BACKDROP_RGB =
            LostTalesColors.rgb(LostTalesColors.WINE);
    /**
     * Vertical distance between chat lines. Vanilla uses the 9px font
     * height, which cannot contain a 10px emote sprite; an 11px stride
     * gives the sprite room. Bands are contiguous — each line's backdrop
     * fills the full stride. Everything that maps mouse positions back to
     * lines must use this.
     */
    static final int LINE_HEIGHT = 11;
    /**
     * Text baseline offset inside a band. Two rows sit above the glyph
     * caps and two below them, so the 7px cap height is centred in the
     * 11px band; heads (8px at -0.5) land exactly centred as well.
     */
    private static final int TEXT_OFFSET = 9;
    private static final float BACKDROP_FADE_START = 2.0F / 3.0F;
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

    /**
     * Maps a raw mouse position onto the component under it, using this
     * renderer's line layout. Replaces {@code GuiNewChat.func_146236_a},
     * whose hardcoded 9px math no longer matches what is on screen.
     */
    static Hit hitAt(Minecraft minecraft, int rawMouseX, int rawMouseY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.fontRenderer == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        try {
            List<ChatLine> lines = getDrawnLines(chat);
            if (lines == null || lines.isEmpty()) {
                return null;
            }
            int scrollPosition = getScrollPosition(chat);
            net.minecraft.client.gui.ScaledResolution resolution =
                    new net.minecraft.client.gui.ScaledResolution(
                            minecraft, minecraft.displayWidth,
                            minecraft.displayHeight);
            int scaleFactor = resolution.getScaleFactor();
            float chatScale = chat.func_146244_h();
            int lineIndex = LostTalesChatClipboard.lineIndexAt(
                    rawMouseX, rawMouseY, scaleFactor, chatScale,
                    MathHelper.floor_float(
                            chat.func_146228_f() / chatScale),
                    Math.min(visibleLineCount(chat), lines.size()),
                    scrollPosition, scrollPosition == 0
                            ? getEntryDisplacement() : 0.0F,
                    lines.size());
            if (lineIndex < 0) {
                return null;
            }
            IChatComponent lineRoot =
                    lines.get(lineIndex).func_151461_a();
            int x = MathHelper.floor_float(
                    (rawMouseX / scaleFactor - 3) / chatScale);
            int cursor = 0;
            for (Object value : lineRoot) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                cursor += minecraft.fontRenderer.getStringWidth(
                        part.getChatStyle().getFormattingCode()
                                + part.getUnformattedTextForChat());
                if (x < cursor) {
                    return new Hit(part, lineRoot);
                }
            }
            return null;
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** A clicked component together with the wrapped line that holds it. */
    static final class Hit {
        final IChatComponent component;
        final IChatComponent line;

        private Hit(IChatComponent component, IChatComponent line) {
            this.component = component;
            this.line = line;
        }
    }

    /** Lines that fit the user's configured chat pixel height at 11px. */
    static int visibleLineCount(GuiNewChat chat) {
        return Math.max(1, chat.func_146232_i() * 9 / LINE_HEIGHT);
    }

    private static void drawVanillaCompatible(
            Minecraft minecraft, GuiNewChat chat,
            List<ChatLine> lines, int scrollPosition, boolean scrolled,
            int offsetX, int offsetY) {
        int visibleLineCount = visibleLineCount(chat);
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

                int y = -index * LINE_HEIGHT;
                // Backdrop and text slide together so the newest message
                // enters as one piece instead of text moving over a static
                // panel. Bands connect seamlessly: each fills the full
                // stride, meeting the next line's band edge-to-edge.
                GL11.glPushMatrix();
                GL11.glTranslatef(entrySlide(line), 0.0F, 0.0F);
                drawChatBackdrop(
                        -2, y - LINE_HEIGHT, unscaledWidth + 4, y,
                        alpha / 2,
                        LostTalesChatPresentation.isPingedLine(
                                line.getChatLineID())
                                ? PING_BACKDROP_RGB
                                : CHAT_BACKDROP_RGB);
                GL11.glEnable(GL11.GL_BLEND);
                IChatComponent component = line.func_151461_a();
                GL11.glPushMatrix();
                GL11.glTranslatef(0.0F, y - (float)TEXT_OFFSET, 0.0F);
                ChatHeadMarker.Data marker = findMarker(component);
                LostTalesChatVisualStyle.drawFormatted(font,
                        component, marker, 0, 0, alpha);
                drawHead(minecraft, font, component,
                        -0.5F, alpha);
                GL11.glPopMatrix();
                GL11.glPopMatrix();
                GL11.glDisable(GL11.GL_ALPHA_TEST);
            }

            if (open) {
                GL11.glTranslatef(-3.0F, 0.0F, 0.0F);
                int fullHeight = totalLineCount * LINE_HEIGHT;
                int visibleHeight = eligibleLineCount * LINE_HEIGHT;
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

    /** Palette backdrop band with a smooth transparent right edge. */
    private static void drawChatBackdrop(
            int left, int top, int right, int bottom, int alpha,
            int backdropRgb) {
        int safeAlpha = Math.max(0, Math.min(255, alpha));
        if (right <= left || bottom <= top || safeAlpha <= 0) {
            return;
        }
        int fadeStart = Math.min(right,
                left + backdropFadeStart(right - left));
        Gui.drawRect(left, top, fadeStart, bottom,
                (safeAlpha << 24) | backdropRgb);
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
        tessellator.setColorRGBA_I(backdropRgb, 0);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.setColorRGBA_I(backdropRgb, safeAlpha);
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
                if (marker.npcIdentity) {
                    drawNpcHeadShadow(minecraft, marker, x, y, opacity);
                    LostTalesCharacterHeadIconRenderer.drawNpcHead(
                            minecraft, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else if (marker.accountIdentity) {
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

    private static void drawNpcHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            int x, float y, float opacity) {
        LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                minecraft, marker.skinId, x + 1.25F, y + 0.75F, 8.0F,
                LostTalesColors.redF(LostTalesChatVisualStyle.SHADOW),
                LostTalesColors.greenF(LostTalesChatVisualStyle.SHADOW),
                LostTalesColors.blueF(LostTalesChatVisualStyle.SHADOW),
                opacity);
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
                red, green, blue, opacity);
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
                red, green, blue, opacity);
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

    /** Horizontal entry offset for lines of the newest message only. */
    private static float entrySlide(ChatLine line) {
        if (!LostTalesConfig.enableChatAnimations || line == null
                || line.getUpdatedCounter()
                != LostTalesChatPresentation.getLastMessageUpdateCounter()) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatAnimationDurationMillis) * 1000000L;
        return LostTalesChatMotion.message(
                (System.nanoTime() - started) / (float)duration)
                .slideOffsetX;
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
