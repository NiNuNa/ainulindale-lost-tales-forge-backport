package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;

/**
 * Lost Tales' ivory text and plum-black shadow treatment for chat. Every
 * chat element — text, emotes, head icons, item and marker icons — draws
 * its shadow as a flat {@link #SHADOW} silhouette offset by one pixel at
 * half opacity, so the relationship between content and shadow is
 * identical at every GUI scale. Channel prefix components are skipped
 * entirely while the chat screen is open (the tabs already say which
 * channel a line belongs to).
 */
final class LostTalesChatVisualStyle {
    static final int IVORY = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_LABEL);
    static final int SHADOW = LostTalesSkyrimUiStyle.rgb(
            LostTalesSkyrimUiStyle.HUD_SHADOW);
    /** Shared translucent surface behind popups. */
    static final int SURFACE = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_BLACK, 0xB0);
    static final int SURFACE_HOVER = LostTalesSkyrimUiStyle.withAlpha(
            LostTalesSkyrimUiStyle.PLUM_DARK, 0xCC);
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

    /** Measured width of one component as the renderer advances past it. */
    static int partWidth(FontRenderer font, IChatComponent part) {
        return font.getStringWidth(part.getChatStyle().getFormattingCode()
                + part.getUnformattedTextForChat());
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
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatChannelPrefixMarker.isHidden(part, chatOpen)) {
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
                        ChatEmojiRenderer.drawShadow(minecraft, emoji,
                                spriteX, y - 2, size, SHADOW, alpha);
                    } else {
                        ChatEmojiRenderer.draw(minecraft, emoji,
                                spriteX, y - 2, size, alpha);
                    }
                }
                cursor += slotWidth;
                continue;
            }

            ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
            if (share != null && share.icon) {
                int slotWidth = font.getStringWidth(formatting + text);
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
            boolean replyIdentity = isReplyIdentity(part);
            boolean identityBracket = "<".equals(text)
                    || (identitySeen && text.startsWith(">"));
            if (shadowPass) {
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
            cursor += font.getStringWidth(formatting + text);
            identitySeen |= replyIdentity;
        }
    }

    private static void drawShareIcon(ChatShowcaseMarker.Data share,
                                      int cursor, int y, int slotWidth,
                                      int alpha, boolean shadowPass) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (share.kind == ChatShareKind.ITEM) {
            ItemStack stack = ClientChatShowcaseStore.getItem(share.showcaseId);
            if (stack == null) {
                return;
            }
            float size = Math.min(ChatItemRenderer.ICON_SIZE, slotWidth);
            float iconX = cursor + Math.max(0.0F, (slotWidth - size) / 2.0F);
            if (shadowPass) {
                ChatItemRenderer.drawShadow(minecraft, stack, iconX, y - 1,
                        size, SHADOW, alpha);
            } else {
                ChatItemRenderer.draw(minecraft, stack, iconX, y - 1,
                        size, alpha);
            }
            return;
        }
        ClientChatShowcaseStore.Marker marker =
                ClientChatShowcaseStore.getMarker(share.showcaseId);
        if (marker == null) {
            return;
        }
        float size = Math.min(ChatMapMarkerRenderer.ICON_SIZE, slotWidth);
        float iconX = cursor + Math.max(0.0F, (slotWidth - size) / 2.0F);
        if (shadowPass) {
            ChatMapMarkerRenderer.drawShadow(minecraft, marker.iconName,
                    iconX, y - 2, size, SHADOW, alpha);
        } else {
            ChatMapMarkerRenderer.draw(minecraft, marker.iconName,
                    marker.colorName, iconX, y - 2, size, alpha);
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
