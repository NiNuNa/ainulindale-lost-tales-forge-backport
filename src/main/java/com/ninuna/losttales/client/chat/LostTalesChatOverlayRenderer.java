package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.gui.animation.LostTalesGuiAnimationSample;
import com.ninuna.losttales.client.render.LostTalesSilhouetteRenderState;
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

/**
 * Vanilla-compatible chat draw pass with heads and optional time-based entry
 * easing. Lines are read from vanilla's history; while the chat screen is
 * open only the selected channel's view is drawn, with its own scroll
 * offset from {@link ClientChatChannelViews}, without the channel prefix,
 * and with the same opening motion every other Lost Tales screen uses. The
 * pass records the screen band of every line it draws in
 * {@link ChatLineBands}; all mouse-to-line mapping (hover card, clipboard,
 * component hits, the vanilla hit-test hook) resolves against those
 * recorded bands, so it always matches what is on screen.
 */
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
     * fills the full stride.
     */
    static final int LINE_HEIGHT = 11;
    /**
     * Text baseline offset inside a band. Two rows sit above the glyph
     * caps and two below them, so the 7px cap height is centred in the
     * 11px band; heads (8px at -0.5) land exactly centred as well.
     */
    private static final int TEXT_OFFSET = 9;
    /** Forge draws the chat 48px above the bottom; lines end 20px below that. */
    private static final int BASELINE_FROM_BOTTOM = 28;
    private static final float BACKDROP_FADE_START = 2.0F / 3.0F;
    /** Pixels the whole stack settles upward from when a view is switched. */
    private static final float VIEW_SWITCH_RISE = 4.0F;
    private static final Field DRAWN_LINES = findField("field_146253_i");
    private static final ChatLineBands BANDS = new ChatLineBands();

    private LostTalesChatOverlayRenderer() {}

    static boolean draw(Minecraft minecraft, int offsetX, int offsetY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.gameSettings.chatVisibility
                == EntityPlayer.EnumChatVisibility.HIDDEN
                || DRAWN_LINES == null) {
            return false;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        try {
            List<ChatLine> lines = getViewLines(chat);
            int scrollPosition = getViewScroll(chat, lines);
            drawVanillaCompatible(
                    minecraft, chat, lines, scrollPosition,
                    scrollPosition > 0, offsetX, offsetY);
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

    /** The selected channel while the screen is open; null (all) otherwise. */
    static ChatChannel currentView(GuiNewChat chat) {
        return chat != null && chat.getChatOpen()
                ? ClientChatChannelState.getSelected() : null;
    }

    /** Lines visible in the current view, newest first; never null. */
    static List<ChatLine> getViewLines(GuiNewChat chat)
            throws IllegalAccessException {
        List<ChatLine> drawn = getDrawnLines(chat);
        return ClientChatChannelViews.visibleLines(drawn, currentView(chat));
    }

    /** Scroll offset of the current view, clamped to its line count. */
    static int getViewScroll(GuiNewChat chat, List<ChatLine> viewLines) {
        return ClientChatChannelViews.getScroll(currentView(chat),
                viewLines == null ? 0 : viewLines.size(),
                visibleLineCount(chat));
    }

    /** Number of wrapped lines at the head of history with this id. */
    static int countLeadingLines(GuiNewChat chat, int chatLineId) {
        try {
            List<ChatLine> drawn = getDrawnLines(chat);
            int count = 0;
            while (drawn != null && count < drawn.size()
                    && drawn.get(count) != null
                    && drawn.get(count).getChatLineID() == chatLineId) {
                count++;
            }
            return count;
        } catch (IllegalAccessException ignored) {
            return 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    /** True when the open view drew at least one line this frame. */
    static boolean hasDrawnLines() {
        return BANDS.count() > 0;
    }

    /**
     * Top edge (GUI coordinates) of the history as drawn this frame: the
     * top of the highest line band, or the baseline when the view is
     * empty. Bands carry the entry and opening motion, so anything that
     * sits on this edge (the channel tabs) rides along with the stack.
     */
    static int historyTop(GuiNewChat chat, int screenHeight) {
        if (chat != null && chat.getChatOpen() && BANDS.count() > 0) {
            float top = Float.MAX_VALUE;
            for (int index = 0; index < BANDS.count(); index++) {
                top = Math.min(top, BANDS.topOf(index));
            }
            return Math.round(top);
        }
        return screenHeight - BASELINE_FROM_BOTTOM;
    }

    /**
     * The drawn band under a GUI-space point, or null. Resolved against
     * the bands recorded by the last draw of the same line list.
     */
    static Band bandAt(Minecraft minecraft, float mouseX, float mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        if (chat == null || !chat.getChatOpen()) {
            return null;
        }
        try {
            List<ChatLine> lines = getViewLines(chat);
            if (lines == null || !BANDS.describes(lines, lines.size())) {
                return null;
            }
            int band = BANDS.find(mouseX, mouseY);
            if (band < 0) {
                return null;
            }
            return new Band(BANDS.viewIndexOf(band),
                    BANDS.localX(band, mouseX), BANDS.topOf(band),
                    BANDS.bottomOf(band), BANDS.scale());
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Maps a GUI-space mouse position onto the component under it, using
     * the bands this renderer drew and skipping the components it did not
     * draw. Replaces {@code GuiNewChat.func_146236_a}, whose hardcoded 9px
     * math no longer matches what is on screen.
     */
    static Hit hitAt(Minecraft minecraft, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.ingameGUI == null
                || minecraft.fontRenderer == null) {
            return null;
        }
        GuiNewChat chat = minecraft.ingameGUI.getChatGUI();
        Band band = bandAt(minecraft, mouseX, mouseY);
        if (band == null) {
            return null;
        }
        try {
            List<ChatLine> lines = getViewLines(chat);
            if (lines == null || band.viewIndex >= lines.size()
                    || lines.get(band.viewIndex) == null) {
                return null;
            }
            IChatComponent lineRoot =
                    lines.get(band.viewIndex).func_151461_a();
            int cursor = 0;
            for (Object value : lineRoot) {
                if (!(value instanceof IChatComponent)) {
                    continue;
                }
                IChatComponent part = (IChatComponent)value;
                if (ChatChannelPrefixMarker.isHidden(part, true)) {
                    continue;
                }
                cursor += LostTalesChatVisualStyle.partWidth(
                        minecraft.fontRenderer, part, true);
                if (band.localX < cursor) {
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

    /** A drawn line band with the pointer already mapped into text space. */
    static final class Band {
        /** Index into the current view's line list. */
        final int viewIndex;
        /** Pointer x in the line's own unscaled text space. */
        final float localX;
        final float top;
        final float bottom;
        final float scale;

        private Band(int viewIndex, float localX, float top, float bottom,
                     float scale) {
            this.viewIndex = viewIndex;
            this.localX = localX;
            this.top = top;
            this.bottom = bottom;
            this.scale = scale;
        }
    }

    /**
     * Right edge (GUI coordinates) of the history's backdrop band before
     * its fade: the chat width setting at the chat scale, plus the band's
     * own margins, as drawn from the resting origin.
     */
    static int historyRight(GuiNewChat chat) {
        if (chat == null) {
            return 0;
        }
        float scale = chat.func_146244_h();
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        return Math.round(2.0F + (unscaledWidth + 4) * scale);
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
        boolean open = chat.getChatOpen();
        float scale = chat.func_146244_h();
        BANDS.reset(lines, totalLineCount, scale);
        if (totalLineCount <= 0) {
            return;
        }

        ChatChannel view = open ? ClientChatChannelState.getSelected() : null;
        int unscaledWidth = MathHelper.ceiling_float_int(
                chat.func_146228_f() / scale);
        FontRenderer font = minecraft.fontRenderer;
        // Switching tabs settles the new view upward with a short fade so
        // the change reads as motion instead of a hard cut. Time-based, so
        // a switch mid-transition simply restarts from the new view.
        float viewProgress = open
                ? LostTalesChatMotion.smoothStep(
                        ClientChatChannelViews.viewTransitionProgress())
                : 1.0F;
        // Opening the screen brings the history in with the same sampler
        // as every other Lost Tales screen; the tabs follow because they
        // stand on the bands.
        LostTalesGuiAnimationSample opening = open
                ? ClientChatChannelViews.openSample()
                : LostTalesGuiAnimationSample.SETTLED;
        float originX = offsetX + 2.0F + opening.getTranslationX();
        float originY = offsetY + 20.0F
                + entryDisplacement(view, scrollPosition)
                + VIEW_SWITCH_RISE * (1.0F - viewProgress)
                + opening.getTranslationY();

        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(originX, originY, 0.0F);
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
                alpha = (int)(alpha * (0.35F + 0.65F * viewProgress));
                alpha = (int)(alpha * opening.getOpacity());
                eligibleLineCount++;
                int y = -index * LINE_HEIGHT;
                float slide = entrySlide(line);
                if (open) {
                    // Recorded exactly as drawn: the same translate, slide
                    // and scale the quads below use. Recorded even while
                    // the line is still too faint to paint, so the tabs
                    // standing on the bands exist from the first frame of
                    // the opening fade instead of popping in later.
                    float bandLeft = originX + slide * scale;
                    BANDS.add(index + scrollPosition, bandLeft,
                            bandLeft + unscaledWidth * scale,
                            originY + (y - LINE_HEIGHT) * scale,
                            originY + y * scale);
                }
                if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
                    continue;
                }
                // Backdrop and text slide together so the newest message
                // enters as one piece instead of text moving over a static
                // panel. Bands connect seamlessly: each fills the full
                // stride, meeting the next line's band edge-to-edge.
                GL11.glPushMatrix();
                GL11.glTranslatef(slide, 0.0F, 0.0F);
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
                        component, marker, 0, 0, alpha, open);
                drawHead(minecraft, font, component,
                        -0.5F, alpha, open);
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
                                 IChatComponent line, float y, int alpha,
                                 boolean chatOpen) {
        int x = 0;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatChannelPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                float opacity = alpha / 255.0F;
                drawHeadShadow(minecraft, marker, x, y,
                        opacity * LostTalesChatVisualStyle.SHADOW_OPACITY);
                if (marker.npcIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawNpcHead(
                            minecraft, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else if (marker.accountIdentity) {
                    LostTalesCharacterHeadIconRenderer.drawAccountHead(
                            minecraft, marker.senderId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                } else {
                    LostTalesCharacterHeadIconRenderer.drawSnapshotHead(
                            minecraft, marker.senderId, marker.skinId,
                            x + 0.5F, y, 8.0F, 1.0F, opacity);
                }
                return;
            }
            // getFormattedText() recursively includes a component's siblings.
            // Measuring only this node keeps the marker aligned after the
            // structured message has been split into wrapped ChatLines.
            x += LostTalesChatVisualStyle.partWidth(font, part, chatOpen);
        }
    }

    /**
     * Flat shadow of the head's base face, one pixel down-right like the
     * text shadow (the half-pixel portrait offset lands it on the same
     * grid). Silhouette mode gives every skin the same shadow colour
     * instead of a darkened copy of its own pixels.
     */
    private static void drawHeadShadow(
            Minecraft minecraft, ChatHeadMarker.Data marker,
            int x, float y, float opacity) {
        if (opacity * 255.0F < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        float shadowX = x + 0.5F + LostTalesChatVisualStyle.SHADOW_OFFSET;
        float shadowY = y + LostTalesChatVisualStyle.SHADOW_OFFSET;
        LostTalesSilhouetteRenderState.begin(LostTalesChatVisualStyle.SHADOW);
        try {
            if (marker.npcIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedNpcHeadBase(
                        minecraft, marker.skinId, shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            } else if (marker.accountIdentity) {
                LostTalesCharacterHeadIconRenderer.drawTintedAccountHeadBase(
                        minecraft, marker.senderId, shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            } else {
                LostTalesCharacterHeadIconRenderer.drawTintedSnapshotHeadBase(
                        minecraft, marker.senderId, marker.skinId,
                        shadowX, shadowY, 8.0F,
                        1.0F, 1.0F, 1.0F, opacity);
            }
        } finally {
            LostTalesSilhouetteRenderState.end();
        }
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

    /**
     * Stack rise for the newest message. Only applies to the view that
     * actually received it, so a message in another channel's tab does not
     * nudge the history currently being read.
     */
    private static float entryDisplacement(ChatChannel view,
                                           int scrollPosition) {
        if (!LostTalesConfig.enableChatAnimations || scrollPosition != 0) {
            return 0.0F;
        }
        long started = LostTalesChatPresentation.getLastMessageNanos();
        if (started <= 0L) {
            return 0.0F;
        }
        ChatChannel lastChannel =
                LostTalesChatPresentation.getLastMessageChannel();
        if (view != null && lastChannel != null && lastChannel != view) {
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
