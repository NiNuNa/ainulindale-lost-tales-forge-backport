package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Lost Tales' ivory text and plum-black shadow treatment for chat. Every
 * chat element — text, emotes, head icons, item and marker icons — draws
 * its shadow as a flat {@link #SHADOW} silhouette offset by one pixel at
 * half opacity, so the relationship between content and shadow is
 * identical at every GUI scale; inline glyphs share the box and baseline
 * rules of {@link ChatInlineIcons}. Channel prefix components are skipped
 * entirely while the chat screen is open (the tabs already say which
 * channel a line belongs to), and layout markers advance the cursor
 * without drawing.
 */
final class LostTalesChatVisualStyle {
    static final int IVORY = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_LABEL);
    static final int SHADOW = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_SHADOW);
    /**
     * The one opacity of every chat surface — backdrop, strips, tabs,
     * bars, popups: half. Text and icons are always fully opaque.
     */
    static final int SURFACE_ALPHA = 0x80;
    /** Shared translucent surface behind popups. */
    static final int SURFACE = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_BLACK, SURFACE_ALPHA);
    static final int SURFACE_HOVER = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_DARK, SURFACE_ALPHA);
    /** Alpha-free surface tones for popups that animate their own opacity. */
    static final int SURFACE_RGB = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.PLUM_BLACK);
    static final int SURFACE_HIGHLIGHT_RGB = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.PLUM_GRAY);
    /** Shadow offset shared by text, sprites, and icons. */
    static final int SHADOW_OFFSET = 1;
    /** Shadows sit at half the opacity of the content they belong to. */
    static final float SHADOW_OPACITY = 0.5F;
    /**
     * Lowest alpha FontRenderer honours: a colour whose alpha is below four
     * is treated as opaque, so a near-invisible shadow would flash at full
     * strength. Anything under this is not drawn at all.
     */
    static final int MIN_VISIBLE_ALPHA = 4;

    private LostTalesChatVisualStyle() {}

    /**
     * Shadow alpha for content drawn at {@code alpha}, or 0 when the shadow
     * would fall under {@link #MIN_VISIBLE_ALPHA} and must be skipped.
     */
    static int shadowAlpha(int alpha) {
        int shadow = Math.round(
                Math.max(0, Math.min(255, alpha)) * SHADOW_OPACITY);
        return shadow < MIN_VISIBLE_ALPHA ? 0 : shadow;
    }

    static void drawFormatted(FontRenderer font, IChatComponent line,
                              ChatHeadMarker.Data metadata,
                              int x, int y, int alpha, boolean chatOpen) {
        if (font == null || line == null || alpha < MIN_VISIBLE_ALPHA) {
            return;
        }
        int shadow = shadowAlpha(alpha);
        if (shadow > 0) {
            drawComponentPass(font, line, metadata,
                    x + SHADOW_OFFSET, y + SHADOW_OFFSET, shadow, true,
                    chatOpen);
        }
        drawComponentPass(font, line, metadata, x, y, alpha, false,
                chatOpen);
    }

    static void drawPlain(FontRenderer font, String text,
                          int x, int y, int alpha) {
        drawColored(font, text, x, y, IVORY, alpha);
    }

    /** Text in an explicit colour with the shared shadow treatment. */
    static void drawColored(FontRenderer font, String text,
                            int x, int y, int rgb, int alpha) {
        if (font == null || text == null || alpha < MIN_VISIBLE_ALPHA) {
            return;
        }
        int shadow = shadowAlpha(alpha);
        if (shadow > 0) {
            font.drawString(text, x + SHADOW_OFFSET, y + SHADOW_OFFSET,
                    argb(SHADOW, shadow));
        }
        font.drawString(text, x, y, argb(rgb, alpha));
    }

    static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (rgb & 0xFFFFFF);
    }

    /**
     * Width of one component as the renderer advances past it: the text
     * measured with its style's formatting code, or, for an indent marker,
     * the inset recorded for the current chat state. Every walk over a
     * line — drawing, head placement, hit testing, the hover card — must
     * advance by this, so they all ask here.
     */
    static int partWidth(FontRenderer font, IChatComponent part,
                         boolean chatOpen) {
        ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
        if (layout != null) {
            return layout.indent(chatOpen);
        }
        return measure(font, part.getChatStyle().getFormattingCode(),
                part.getUnformattedTextForChat(), chatColoursEnabled());
    }

    /**
     * Vanilla's "chat colours" option. Off, every formatting code — ours
     * and the sender's — is stripped before measuring and drawing, and the
     * whole line is plain ivory, exactly as vanilla renders colourless
     * chat. Measuring and drawing always agree because both ask here.
     */
    static boolean chatColoursEnabled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.gameSettings == null
                || minecraft.gameSettings.chatColours;
    }

    /** Every section-sign code removed, colour and decoration alike. */
    static String stripCodes(String text) {
        if (text == null || text.indexOf('§') < 0) {
            return text == null ? "" : text;
        }
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(text);
        return stripped == null ? "" : stripped;
    }

    private static int measure(FontRenderer font, String formatting,
                               String text, boolean colours) {
        return font.getStringWidth(colours ? formatting + text
                : stripCodes(formatting + text));
    }

    private static String removeExplicitWhite(String text) {
        return text.replace("\u00a7f", "").replace("\u00a7F", "");
    }

    private static void drawComponentPass(
            FontRenderer font, IChatComponent line,
            ChatHeadMarker.Data metadata, int x, int y,
            int alpha, boolean shadowPass, boolean chatOpen) {
        int cursor = x;
        boolean afterHead = false;
        boolean identitySeen = false;
        boolean colours = chatColoursEnabled();
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatChannelPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
            if (layout != null) {
                // Zero-width layout metadata; an indent marker insets a
                // continuation line under the message body.
                cursor += layout.indent(chatOpen);
                continue;
            }
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                afterHead = true;
            }

            ChatEmoji emoji = ChatEmojiMarker.decode(part);
            if (emoji != null) {
                int slotWidth = measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(), emoji,
                            ChatInlineIcons.boxLeft(cursor, slotWidth),
                            ChatInlineIcons.boxTop(y, slotWidth),
                            ChatInlineIcons.contentSize(slotWidth), alpha,
                            shadowPass);
                }
                cursor += slotWidth;
                continue;
            }

            ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
            if (share != null && share.icon) {
                int slotWidth = measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    drawShareIcon(share, cursor, y, slotWidth, alpha,
                            shadowPass);
                }
                cursor += slotWidth;
                continue;
            }

            String rendered;
            int color;
            Integer prefixColor = ChatChannelPrefixMarker.decode(part);
            Integer explicitColor = ChatColorMarker.decode(part);
            if (explicitColor == null) {
                explicitColor = ChatTitleMarker.colorOf(part);
            }
            boolean replyIdentity = isReplyIdentity(part);
            boolean identityBracket = "<".equals(text)
                    || (identitySeen && text.startsWith(">"));
            if (!colours) {
                rendered = stripCodes(formatting + text);
                color = shadowPass ? SHADOW : IVORY;
            } else if (shadowPass) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = SHADOW;
            } else if (share != null) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = share.textColor;
            } else if (prefixColor != null) {
                rendered = styleCodesOnly(formatting)
                        + removeColorCodes(text);
                color = prefixColor.intValue();
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
            cursor += measure(font, formatting, text, colours);
            identitySeen |= replyIdentity;
        }
    }

    private static void drawShareIcon(ChatShowcaseMarker.Data share,
                                      int cursor, int y, int slotWidth,
                                      int alpha, boolean shadowPass) {
        Minecraft minecraft = Minecraft.getMinecraft();
        float boxX = ChatInlineIcons.boxLeft(cursor, slotWidth);
        float boxY = ChatInlineIcons.boxTop(y, slotWidth);
        float size = ChatInlineIcons.contentSize(slotWidth);
        if (share.kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(share.showcaseId);
            if (stack != null) {
                ChatInlineIcons.drawItem(minecraft, stack, boxX, boxY, size,
                        alpha, shadowPass);
            }
            return;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(share.showcaseId);
        if (marker != null) {
            ChatInlineIcons.drawMarker(minecraft, marker.iconName,
                    ChatInlineIcons.markerRgb(marker.colorName),
                    boxX, boxY, size, alpha, shadowPass);
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
