package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import org.lwjgl.opengl.GL11;

/**
 * The chat's input field, drawn the way the rest of the chat is drawn.
 *
 * <p>Vanilla's {@code GuiTextField} draws its text with
 * {@code drawStringWithShadow}, whose shadow is a quarter of the text's
 * own colour at full opacity — a different shadow from every other glyph
 * in the chat, right beside them on the same bar. Everything else about
 * the field is vanilla's: this replaces the drawing alone, using the
 * shared {@link LostTalesChatVisualStyle} treatment for the text, the
 * caret and the selection.</p>
 *
 * <p>A mention being typed is drawn in the colour it will have once it
 * is sent — a role's own, a player's role, or the shared mention honey —
 * so the bar reads as the line it is about to become. The rest of the
 * text stays ivory.</p>
 *
 * <p>Two of vanilla's fields have no accessor — how far the text is
 * scrolled and the caret's blink — so they are read reflectively by both
 * their names, verified to be the ints they are. Without them the field
 * falls back to vanilla's own drawing rather than showing the wrong
 * text.</p>
 */
final class ChatInputField extends GuiTextField {
    private static final Field LINE_SCROLL_OFFSET =
            resolve("lineScrollOffset", "field_146225_q");
    private static final Field CURSOR_COUNTER =
            resolve("cursorCounter", "field_146214_l");
    private static boolean fallbackLogged;

    private final FontRenderer font;

    ChatInputField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.font = font;
    }

    /** Whether the field can be drawn in the chat's own style. */
    static boolean isStyled() {
        return LINE_SCROLL_OFFSET != null && CURSOR_COUNTER != null;
    }

    @Override
    public void drawTextBox() {
        if (!getVisible()) {
            return;
        }
        if (!isStyled()) {
            logFallbackOnce();
            super.drawTextBox();
            return;
        }
        int scrollOffset;
        int blink;
        try {
            scrollOffset = LINE_SCROLL_OFFSET.getInt(this);
            blink = CURSOR_COUNTER.getInt(this);
        } catch (IllegalAccessException unreadable) {
            logFallbackOnce();
            super.drawTextBox();
            return;
        }
        String text = getText();
        if (scrollOffset < 0 || scrollOffset > text.length()) {
            // Vanilla keeps this in range; anything else is not ours to
            // draw from.
            super.drawTextBox();
            return;
        }
        String visible = this.font.trimStringToWidth(
                text.substring(scrollOffset), getWidth());
        int caret = getCursorPosition() - scrollOffset;
        int selection = Math.min(getSelectionEnd() - scrollOffset,
                visible.length());
        boolean caretInside = caret >= 0 && caret <= visible.length();
        boolean caretVisible = isFocused() && blink / 6 % 2 == 0
                && caretInside;
        int left = this.xPosition;
        int top = this.yPosition;
        int cursorX = left;
        // The whole visible run is coloured at once, so a mention split
        // by the caret keeps one colour across the break.
        int[] colors = colorsOf(visible);
        if (visible.length() > 0) {
            int headEnd = caretInside ? caret : visible.length();
            cursorX = drawRuns(visible, colors, 0, headEnd, left, top);
        }
        // Vanilla puts the caret between characters while there is text
        // to its right, and after the last one otherwise. The bar stands
        // on the boundary between the two runs; the runs themselves are
        // never shifted for it, so the text stays still as the caret
        // walks through it.
        boolean caretBetween = getCursorPosition() < text.length()
                || text.length() >= getMaxStringLength();
        int caretX = cursorX;
        if (!caretInside) {
            caretX = caret > 0 ? left + getWidth() : left;
        }
        if (visible.length() > 0 && caretInside && caret < visible.length()) {
            drawRuns(visible, colors, caret, visible.length(), cursorX, top);
        }
        if (caretVisible) {
            if (caretBetween) {
                Gui.drawRect(caretX, top - 1, caretX + 1,
                        top + 1 + this.font.FONT_HEIGHT,
                        LostTalesChatVisualStyle.argb(CARET_RGB, 0xFF));
            } else {
                LostTalesChatVisualStyle.drawColored(this.font, "_", caretX,
                        top, CARET_RGB, 255);
            }
        }
        if (selection != caret && caretInside) {
            int selectionX = left + this.font.getStringWidth(
                    visible.substring(0, Math.max(0, selection)));
            drawSelection(caretX, top - 1, selectionX - 1,
                    top + 1 + this.font.FONT_HEIGHT);
        }
    }

    /** The caret in the palette's ivory, like the text it stands in. */
    private static final int CARET_RGB =
            LostTalesColors.rgb(LostTalesColors.IVORY);

    /**
     * The colour of every character of the visible text: ivory, except
     * where an {@code @name} reaches somebody, which wears that
     * somebody's colour.
     */
    private int[] colorsOf(String visible) {
        int[] colors = new int[visible.length()];
        for (int index = 0; index < colors.length; index++) {
            colors[index] = LostTalesChatVisualStyle.IVORY;
        }
        ChatChannel channel = ClientChatChannelState.getSelectedChannel();
        int cursor = 0;
        while (cursor < visible.length()) {
            int at = visible.indexOf('@', cursor);
            if (at < 0) {
                break;
            }
            int end = at + 1;
            while (end < visible.length() && ChatMentionColors
                    .isMentionCharacter(visible.charAt(end))) {
                end++;
            }
            boolean opensWord = at == 0 || !ChatMentionColors
                    .isMentionCharacter(visible.charAt(at - 1));
            int color = opensWord && end > at + 1
                    ? ChatMentionColors.colorOf(
                            visible.substring(at + 1, end), channel)
                    : -1;
            if (color >= 0) {
                for (int index = at; index < end; index++) {
                    colors[index] = color;
                }
            }
            cursor = Math.max(end, at + 1);
        }
        return colors;
    }

    /**
     * Draws {@code [from, to)} of the visible text as runs of one
     * colour, and answers where the text ends.
     */
    private int drawRuns(String visible, int[] colors, int from, int to,
                         int x, int y) {
        int cursor = x;
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && colors[end] == colors[start]) {
                end++;
            }
            String run = visible.substring(start, end);
            LostTalesChatVisualStyle.drawColored(this.font, run, cursor, y,
                    colors[start], 255);
            cursor += this.font.getStringWidth(run);
            start = end;
        }
        return cursor;
    }

    /**
     * The selection band, as vanilla draws it: an inverting quad, so the
     * text under it stays legible whatever colour it is.
     */
    private void drawSelection(int left, int top, int right, int bottom) {
        int fromX = Math.min(left, right);
        int toX = Math.max(left, right);
        int fromY = Math.min(top, bottom);
        int toY = Math.max(top, bottom);
        toX = Math.min(toX, this.xPosition + getWidth());
        fromX = Math.min(fromX, this.xPosition + getWidth());
        Tessellator tessellator = Tessellator.instance;
        GL11.glColor4f(0.0F, 0.0F, 1.0F, 1.0F);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glLogicOp(GL11.GL_OR_REVERSE);
        tessellator.startDrawingQuads();
        tessellator.addVertex(toX, fromY, 0.0D);
        tessellator.addVertex(fromX, fromY, 0.0D);
        tessellator.addVertex(fromX, toY, 0.0D);
        tessellator.addVertex(toX, toY, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void logFallbackOnce() {
        if (fallbackLogged) {
            return;
        }
        fallbackLogged = true;
        FMLLog.warning("[%s] The chat input field keeps vanilla's drawing; "
                + "its text shadow will not match the rest of the chat",
                LostTalesMetaData.MOD_ID);
    }

    /** One of vanilla's own int fields, by either of its names. */
    private static Field resolve(String... names) {
        for (String name : names) {
            try {
                Field field = GuiTextField.class.getDeclaredField(name);
                if (field.getType() != int.class
                        || Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                continue;
            } catch (RuntimeException inaccessible) {
                return null;
            }
        }
        return null;
    }
}
