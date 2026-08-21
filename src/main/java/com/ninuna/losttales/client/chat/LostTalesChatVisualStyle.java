package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.IChatComponent;

/** Lost Tales' ivory text and plum-black shadow treatment for chat. */
final class LostTalesChatVisualStyle {
    static final int IVORY = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_LABEL);
    static final int SHADOW = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_SHADOW);
    /** Shared translucent surface behind the indicator, popups, and button. */
    static final int SURFACE = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_BLACK, 0xB0);
    static final int SURFACE_HOVER = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_DARK, 0xCC);
    /** Alpha-free surface tones for popups that animate their own opacity. */
    static final int SURFACE_RGB = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.PLUM_BLACK);
    static final int SURFACE_HIGHLIGHT_RGB = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.PLUM_GRAY);

    private LostTalesChatVisualStyle() {}

    static void drawFormatted(FontRenderer font, IChatComponent line,
                              ChatHeadMarker.Data metadata,
                              int x, int y, int alpha) {
        if (font == null || line == null || alpha <= 3) {
            return;
        }
        drawComponentPass(font, line, metadata,
                x + 1, y + 1, alpha, true);
        drawComponentPass(font, line, metadata,
                x, y, alpha, false);
    }

    static void drawPlain(FontRenderer font, String text,
                          int x, int y, int alpha) {
        if (font == null || text == null || alpha <= 3) {
            return;
        }
        font.drawString(text, x + 1, y + 1,
                argb(SHADOW, alpha));
        font.drawString(text, x, y, argb(IVORY, alpha));
    }

    static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (rgb & 0xFFFFFF);
    }

    private static String removeExplicitWhite(String text) {
        return text.replace("\u00a7f", "").replace("\u00a7F", "");
    }

    private static void drawComponentPass(
            FontRenderer font, IChatComponent line,
            ChatHeadMarker.Data metadata, int x, int y,
            int alpha, boolean shadowPass) {
        int cursor = x;
        boolean afterHead = false;
        boolean identitySeen = false;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                afterHead = true;
            }

            ChatEmoji emoji = ChatEmojiMarker.decode(part);
            if (emoji != null) {
                int slotWidth = font.getStringWidth(formatting + text);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    float size = Math.min(
                            ChatEmoji.SPRITE_SIZE, slotWidth);
                    float spriteX = cursor
                            + Math.max(0.0F, (slotWidth - size) / 2.0F);
                    // Two rows above the text baseline keeps the sprite
                    // centred in the chat band while the text sits one
                    // pixel lower than the sprite's top edge.
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (shadowPass) {
                        ChatEmojiRenderer.drawTinted(minecraft, emoji,
                                spriteX, y - 2, size,
                                LostTalesSkyrimUiStyle.redF(SHADOW),
                                LostTalesSkyrimUiStyle.greenF(SHADOW),
                                LostTalesSkyrimUiStyle.blueF(SHADOW),
                                alpha);
                    } else {
                        ChatEmojiRenderer.draw(minecraft, emoji,
                                spriteX, y - 2, size, alpha);
                    }
                }
                cursor += slotWidth;
                continue;
            }

            String rendered;
            int color;
            Integer explicitColor = ChatColorMarker.decode(part);
            boolean replyIdentity = isReplyIdentity(part);
            boolean identityBracket = "<".equals(text)
                    || (identitySeen && text.startsWith(">"));
            if (shadowPass) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = SHADOW;
            } else if (explicitColor != null) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = explicitColor.intValue();
            } else if (metadata != null && identityBracket) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = metadata.nameColor;
            } else if (metadata != null && replyIdentity) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = metadata.nameColor;
            } else if (metadata != null && afterHead
                    && !identitySeen && marker == null) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = metadata.titleColor;
            } else {
                rendered = removeExplicitWhite(formatting + text);
                color = IVORY;
            }
            font.drawString(rendered, cursor, y, argb(color, alpha));
            cursor += font.getStringWidth(formatting + text);
            identitySeen |= replyIdentity;
        }
    }

    private static boolean isReplyIdentity(IChatComponent part) {
        ClickEvent click = part == null || part.getChatStyle() == null
                ? null : part.getChatStyle().getChatClickEvent();
        return click != null
                && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null
                && click.getValue().startsWith("/msg ");
    }

    /** Removes colour/reset codes but keeps bold/italic decorations and width. */
    static String styleCodesOnly(String formatting) {
        if (formatting == null || formatting.length() == 0) {
            return "";
        }
        StringBuilder kept = new StringBuilder(formatting.length());
        for (int index = 0; index + 1 < formatting.length(); index++) {
            if (formatting.charAt(index) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(
                    formatting.charAt(index + 1));
            if (code >= 'k' && code <= 'o') {
                kept.append('\u00a7').append(code);
            }
            index++;
        }
        return kept.toString();
    }

    /**
     * Removes embedded legacy colours and resets while retaining decorative
     * formatting and visible text. LOTR title display names may contain their
     * own colour code; allowing it through would override the RGB supplied to
     * FontRenderer for the remainder of that component, including the custom
     * shadow pass.
     */
    static String removeColorCodes(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        StringBuilder kept = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\u00a7' && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code >= 'k' && code <= 'o') {
                    kept.append(character).append(code);
                }
                // FontRenderer treats an unknown section-sign code as white,
                // so every non-decoration pair must be consumed as well.
                index++;
                continue;
            }
            kept.append(character);
        }
        return kept.toString();
    }
}
