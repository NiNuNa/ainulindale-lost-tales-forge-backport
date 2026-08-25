package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.gui.style.LostTalesColors;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
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
 * <p>A share token whose item or marker the client can already resolve
 * — {@code [i:Stone Sword]}, {@code [m:Northgate]} — is shown as the
 * preview it will be in chat: the bracket, the icon, the real name, in
 * the rarity's or marker's colour. The raw text is untouched — it is
 * what goes on the wire — but while it is edited a resolved token
 * behaves as one character: the caret can stand on either side of it
 * and never inside, Left and Right step across it whole, Backspace
 * behind it and Delete before it remove all of it, a click lands on its
 * nearer edge, and a selection takes it whole or not at all. All of
 * that is one rule — {@link #snapOutsideTokens} — applied where every
 * caret and selection movement already converges
 * ({@link #setCursorPosition}, {@link #setSelectionPos}), so no key
 * needs handling of its own; an incomplete or unresolvable token is
 * plain text and edits as such.</p>
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
    private final int fieldHeight;
    /** The raw text the previews below were resolved for. */
    private String previewedText;
    private List<TokenPreview> previews = Collections.emptyList();

    ChatInputField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
        this.font = font;
        this.fieldHeight = height;
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
        List<TokenPreview> resolved = previewsFor(text);
        if (!resolved.isEmpty()) {
            drawWithPreviews(text, resolved, scrollOffset, blink);
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

    /** One resolved token: its raw span and its chat preview. */
    private static final class TokenPreview {
        final int start;
        final int end;
        final ChatShareKind kind;
        final ItemStack stack;
        final String markerIcon;
        final String name;
        /** The brackets' and name's colour (white reads as ivory). */
        final int rgb;
        /** The marker artwork's exact colour; white stays untinted. */
        final int iconRgb;
        /** Display width: bracket, icon slot, name, closing bracket. */
        final int width;

        TokenPreview(int start, int end, ChatShareKind kind,
                     ItemStack stack, String markerIcon, String name,
                     int rgb, int iconRgb, int width) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.stack = stack;
            this.markerIcon = markerIcon;
            this.name = name;
            this.rgb = rgb;
            this.iconRgb = iconRgb;
            this.width = width;
        }
    }

    /**
     * The previews for the given raw text, rebuilt only when it changes.
     * Only tokens the client can resolve right now — the same match the
     * send will make — become previews; the rest stay literal text.
     */
    private List<TokenPreview> previewsFor(String text) {
        if (text.equals(this.previewedText)) {
            return this.previews;
        }
        this.previewedText = text;
        this.previews = buildPreviews(text);
        return this.previews;
    }

    private List<TokenPreview> buildPreviews(String text) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || text.indexOf('[') < 0) {
            return Collections.emptyList();
        }
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(text);
        if (tokens.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatShareCandidates.ItemEntry> items =
                ChatShareCandidates.items(minecraft.thePlayer);
        List<ChatShareCandidates.MarkerEntry> markers =
                ChatShareCandidates.markers();
        List<TokenPreview> result = new ArrayList<TokenPreview>();
        int index = 0;
        for (ChatShareTokenParser.Token token : tokens) {
            if (index++ >= ChatShareTokenParser.MAX_TOKENS) {
                break;
            }
            if (token.kind == ChatShareKind.ITEM) {
                for (ChatShareCandidates.ItemEntry entry : items) {
                    if (entry.matchesToken(token)) {
                        result.add(itemPreview(token, entry.stack));
                        break;
                    }
                }
            } else {
                for (ChatShareCandidates.MarkerEntry entry : markers) {
                    if (entry.matchesToken(token)) {
                        result.add(preview(token, null,
                                entry.marker.getIconName(),
                                ChatShareTokenParser.plainName(
                                        entry.marker.getName()),
                                ChatInlineIcons.markerTextRgb(
                                        entry.marker.getColorName()),
                                ChatInlineIcons.markerRgb(
                                        entry.marker.getColorName())));
                        break;
                    }
                }
            }
        }
        return result;
    }

    private TokenPreview itemPreview(ChatShareTokenParser.Token token,
                                     ItemStack stack) {
        EnumRarity rarity = stack.getRarity();
        EnumChatFormatting formatting = rarity == null
                || rarity.rarityColor == null
                ? EnumChatFormatting.WHITE : rarity.rarityColor;
        int rgb = LostTalesChatPresentation.rarityRgb(formatting);
        return preview(token, stack, "",
                ChatShareTokenParser.plainName(stack.getDisplayName()),
                rgb, rgb);
    }

    private TokenPreview preview(ChatShareTokenParser.Token token,
                                 ItemStack stack, String markerIcon,
                                 String name, int rgb, int iconRgb) {
        int width = this.font.getStringWidth("[")
                + ChatInlineIcons.SLOT_WIDTH
                + this.font.getStringWidth(" " + name + "]");
        return new TokenPreview(token.start, token.end, token.kind, stack,
                markerIcon, name, rgb, iconRgb, width);
    }

    /**
     * The field with previews in it. Same structure as the plain path:
     * the visible span is cut to the box, drawn, and the caret and
     * selection are placed on it — every position through the one
     * display model, so nothing drawn and nothing hit can disagree.
     */
    private void drawWithPreviews(String text, List<TokenPreview> resolved,
                                  int scrollOffset, int blink) {
        int visibleEnd = scrollOffset + fittingRawCount(text, resolved,
                scrollOffset, getWidth());
        int left = this.xPosition;
        int top = this.yPosition;
        int[] colors = colorsOf(text.substring(scrollOffset, visibleEnd));
        int x = left;
        int cursor = scrollOffset;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            if (preview.end > visibleEnd) {
                break;
            }
            x = drawPlainRuns(text, colors, scrollOffset, cursor,
                    preview.start, x, top);
            x = drawPreview(preview, x, top);
            cursor = preview.end;
        }
        drawPlainRuns(text, colors, scrollOffset, cursor, visibleEnd,
                x, top);

        int caret = getCursorPosition();
        boolean caretInside = caret >= scrollOffset && caret <= visibleEnd;
        boolean caretVisible = isFocused() && blink / 6 % 2 == 0
                && caretInside;
        int caretX = left + displayedX(text, resolved, scrollOffset,
                Math.max(scrollOffset, Math.min(caret, visibleEnd)));
        boolean caretBetween = caret < text.length()
                || text.length() >= getMaxStringLength();
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
        int selection = getSelectionEnd();
        if (selection != caret && caretInside) {
            int clamped = Math.max(scrollOffset,
                    Math.min(selection, visibleEnd));
            int selectionX = left + displayedX(text, resolved, scrollOffset,
                    clamped);
            drawSelection(caretX, top - 1, selectionX - 1,
                    top + 1 + this.font.FONT_HEIGHT);
        }
    }

    /** Runs of one colour over the raw range, like the plain path's. */
    private int drawPlainRuns(String text, int[] colors, int colorsBase,
                              int from, int to, int x, int y) {
        int cursor = x;
        int start = from;
        while (start < to) {
            int end = start + 1;
            while (end < to && colors[end - colorsBase]
                    == colors[start - colorsBase]) {
                end++;
            }
            String run = text.substring(start, end);
            LostTalesChatVisualStyle.drawColored(this.font, run, cursor, y,
                    colors[start - colorsBase], 255);
            cursor += this.font.getStringWidth(run);
            start = end;
        }
        return cursor;
    }

    /** The token as chat will show it: bracket, icon, name, bracket. */
    private int drawPreview(TokenPreview preview, int x, int y) {
        Minecraft minecraft = Minecraft.getMinecraft();
        LostTalesChatVisualStyle.drawColored(this.font, "[", x, y,
                preview.rgb, 255);
        x += this.font.getStringWidth("[");
        float boxX = ChatInlineIcons.boxLeft(x, ChatInlineIcons.SLOT_WIDTH);
        float boxY = ChatInlineIcons.boxTop(y, ChatInlineIcons.SLOT_WIDTH);
        float size = ChatInlineIcons.contentSize(ChatInlineIcons.SLOT_WIDTH);
        if (preview.kind == ChatShareKind.ITEM) {
            ChatInlineIcons.drawItem(minecraft, preview.stack, boxX, boxY,
                    size, 255);
        } else {
            ChatInlineIcons.drawMarker(minecraft, preview.markerIcon,
                    preview.iconRgb, boxX, boxY, size, 255);
        }
        x += ChatInlineIcons.SLOT_WIDTH;
        String tail = " " + preview.name + "]";
        LostTalesChatVisualStyle.drawColored(this.font, tail, x, y,
                preview.rgb, 255);
        return x + this.font.getStringWidth(tail);
    }

    /**
     * Raw characters from {@code from} that fit {@code room} display
     * pixels: plain characters one by one, a previewed token as one
     * piece — a token the room cannot take whole ends the visible span
     * before it.
     */
    private int fittingRawCount(String text, List<TokenPreview> resolved,
                                int from, int room) {
        int x = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            while (cursor < preview.start) {
                int width = this.font.getCharWidth(text.charAt(cursor));
                if (x + width > room) {
                    return cursor - from;
                }
                x += width;
                cursor++;
            }
            if (x + preview.width > room) {
                return cursor - from;
            }
            x += preview.width;
            cursor = preview.end;
        }
        while (cursor < text.length()) {
            int width = this.font.getCharWidth(text.charAt(cursor));
            if (x + width > room) {
                return cursor - from;
            }
            x += width;
            cursor++;
        }
        return cursor - from;
    }

    /**
     * The display x of a raw index, measured from {@code from}: inside a
     * previewed token the position is proportional, so a selection band
     * over one still reads as a span.
     */
    private int displayedX(String text, List<TokenPreview> resolved,
                           int from, int index) {
        int x = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            if (preview.start >= index) {
                break;
            }
            x += this.font.getStringWidth(
                    text.substring(cursor, preview.start));
            if (preview.end <= index) {
                x += preview.width;
                cursor = preview.end;
                continue;
            }
            return x + preview.width * (index - preview.start)
                    / (preview.end - preview.start);
        }
        return x + this.font.getStringWidth(text.substring(cursor, index));
    }

    /**
     * The raw index a click at {@code x} lands the caret on: between two
     * plain characters as vanilla puts it, and on the nearer edge of a
     * previewed token, which edits as one piece.
     */
    private int rawIndexAtX(String text, List<TokenPreview> resolved,
                            int from, int x) {
        int cx = 0;
        int cursor = from;
        for (TokenPreview preview : resolved) {
            if (preview.start < cursor) {
                continue;
            }
            while (cursor < preview.start) {
                int width = this.font.getCharWidth(text.charAt(cursor));
                if (cx + width > x) {
                    return cursor;
                }
                cx += width;
                cursor++;
            }
            if (cx + preview.width > x) {
                return (x - cx) * 2 > preview.width
                        ? preview.end : preview.start;
            }
            cx += preview.width;
            cursor = preview.end;
        }
        while (cursor < text.length()) {
            int width = this.font.getCharWidth(text.charAt(cursor));
            if (cx + width > x) {
                return cursor;
            }
            cx += width;
            cursor++;
        }
        return text.length();
    }

    /**
     * The nearest position outside every resolved token: a position
     * strictly inside one moves to the edge the motion came from — past
     * the whole token for a step that entered it — and, when there is no
     * motion to read a direction from, to the nearer edge, the same rule
     * a click uses. Positions already on a boundary stay exactly where
     * they are.
     */
    private int snapOutsideTokens(int position, int from) {
        List<TokenPreview> resolved = previewsFor(getText());
        for (int index = 0; index < resolved.size(); index++) {
            TokenPreview preview = resolved.get(index);
            if (position > preview.start && position < preview.end) {
                if (position > from) {
                    return preview.end;
                }
                if (position < from) {
                    return preview.start;
                }
                return (position - preview.start) * 2
                        > preview.end - preview.start
                        ? preview.end : preview.start;
            }
        }
        return position;
    }

    @Override
    public void setCursorPosition(int position) {
        super.setCursorPosition(
                snapOutsideTokens(position, getCursorPosition()));
    }

    @Override
    public void setSelectionPos(int position) {
        super.setSelectionPos(
                snapOutsideTokens(position, getSelectionEnd()));
    }

    /**
     * Backspace directly after a resolved token, and Delete directly
     * before one, remove the whole token; with a selection present the
     * selection is deleted exactly as vanilla deletes it, already
     * token-whole because its ends can only rest on boundaries.
     */
    @Override
    public void deleteFromCursor(int amount) {
        int adjusted = amount;
        if (getCursorPosition() == getSelectionEnd()) {
            int cursor = getCursorPosition();
            List<TokenPreview> resolved = previewsFor(getText());
            for (int index = 0; index < resolved.size(); index++) {
                TokenPreview preview = resolved.get(index);
                if (adjusted < 0 && cursor == preview.end) {
                    adjusted = preview.start - preview.end;
                    break;
                }
                if (adjusted > 0 && cursor == preview.start) {
                    adjusted = preview.end - preview.start;
                    break;
                }
            }
        }
        super.deleteFromCursor(adjusted);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        String text = getText();
        List<TokenPreview> resolved = previewsFor(text);
        boolean inside = mouseX >= this.xPosition
                && mouseX < this.xPosition + getWidth()
                && mouseY >= this.yPosition
                && mouseY < this.yPosition + this.fieldHeight;
        if (resolved.isEmpty() || !isStyled() || !inside || button != 0
                || !isFocused()) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        int scrollOffset;
        try {
            scrollOffset = LINE_SCROLL_OFFSET.getInt(this);
        } catch (IllegalAccessException unreadable) {
            super.mouseClicked(mouseX, mouseY, button);
            return;
        }
        setCursorPosition(rawIndexAtX(text, resolved, scrollOffset,
                mouseX - this.xPosition));
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
