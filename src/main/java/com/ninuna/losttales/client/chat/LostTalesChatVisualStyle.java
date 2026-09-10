package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * Lost Tales' ivory text and plum-black shadow treatment for chat. Every
 * chat element — text, emojis, head icons, item and marker icons — draws
 * its shadow as a flat {@link #SHADOW} silhouette offset by one pixel at
 * two-thirds opacity, so the relationship between content and shadow is
 * identical at every GUI scale; inline glyphs share the box and baseline
 * rules of {@link ChatInlineIcons}.
 *
 * <p>Every one of those draws goes through {@link #beginContent()} first,
 * which turns blending on. It has to: {@code Gui.drawRect} — which every
 * panel, strip and bar in the chat is built from — <em>disables</em>
 * blending when it is done, and text or a sprite drawn after one would
 * otherwise land opaque, shadow and all. Asking each call site to
 * remember that is how a translucent shadow keeps coming back as a
 * solid one, so nothing here relies on the state it is handed.</p> Channel prefix components are skipped
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

    /**
     * The open chat's history panel and the rows framing it, in the
     * palette colour the client chose; plum black until it chooses.
     * Read on every draw, so a choice made in a window's menu shows the
     * same frame.
     */
    static int backdropRgb() {
        return paletteRgb(LostTalesConfig.chatBackgroundColor,
                LostTalesColors.PLUM_BLACK);
    }

    /** The line under the pointer, in the client's chosen palette colour; mauve until it chooses. */
    static int selectedLineRgb() {
        return paletteRgb(LostTalesConfig.chatSelectedLineColor,
                LostTalesColors.MAUVE);
    }

    /** A line that @-mentions this player, in the client's chosen palette colour. */
    static int mentionLineRgb() {
        return paletteRgb(LostTalesConfig.chatMentionLineColor,
                LostTalesColors.MULBERRY);
    }

    /** The line a reply's quote jumped to, while it is lit, in the chosen colour. */
    static int replyHighlightRgb() {
        return paletteRgb(LostTalesConfig.chatReplyHighlightColor,
                LostTalesColors.APRICOT);
    }

    private static int paletteRgb(String name, int fallback) {
        return LostTalesColors.rgb(LostTalesColors.paletteColor(name, fallback));
    }
    /** Shadow offset shared by text, sprites, and icons. */
    static final int SHADOW_OFFSET = 1;
    /** Shared palette opacity for every chat text, icon and portrait shadow. */
    static final float SHADOW_OPACITY = LostTalesSkyrimUiStyle.SHADOW_OPACITY;
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

    /**
     * Puts the pipeline into the state every chat element is drawn in:
     * blended, so a shadow's two-thirds opacity and a fading line's alpha both
     * mean what they say. Called by every draw in this class, and by the
     * chat's sprite drawing, so no call site has to know what the last
     * rectangle left behind.
     */
    /** Width of the hairline the chat divides two controls with. */
    static final int DIVIDER_WIDTH = 1;
    /** Quiet enough to divide without reading as an edge of its own. */
    static final int DIVIDER_ALPHA = 0x66;

    /**
     * A divider between two controls: a one-pixel column of the chat's
     * ivory, strongest at its middle and fading to nothing at both ends,
     * so it parts them without drawing an edge across their strip. The
     * tab row and the input bar both come through here, so their
     * dividers cannot drift apart.
     */
    static void drawDivider(int x, int top, int height, int alpha) {
        LostTalesChatOverlayRenderer.drawVerticalRule(x, x + DIVIDER_WIDTH,
                top, top + height, alpha);
    }

    /**
     * How long a control takes to cross from its resting artwork to its
     * hovered one. Quick enough to feel like an answer to the pointer
     * rather than an animation, slow enough not to be the hard swap it
     * would otherwise be.
     */
    private static final double HOVER_FADE_SECONDS = 0.05D;

    /**
     * One step of a control's crossfade toward {@code hovered}: 0 while
     * it rests, 1 while the pointer is on it, and on the way between
     * them the share of the hovered artwork to lay over the resting one.
     * Every control the chat draws in two states steps with this, so
     * they all answer the pointer at the same pace.
     */
    static float hoverFade(float progress, boolean hovered,
                           double elapsed) {
        float target = hovered ? 1.0F : 0.0F;
        float value = (float)LostTalesChatMotion.approach(progress, target,
                elapsed, HOVER_FADE_SECONDS);
        return Math.abs(target - value) < 0.02F ? target : value;
    }

    /**
     * One colour a share of the way to another, so a control's tones
     * cross with its artwork rather than snapping at the same moment.
     */
    static int blend(int fromRgb, int toRgb, float progress) {
        if (progress <= 0.0F) {
            return fromRgb;
        }
        if (progress >= 1.0F) {
            return toRgb;
        }
        int red = channel(fromRgb, toRgb, progress, 16);
        int green = channel(fromRgb, toRgb, progress, 8);
        int blue = channel(fromRgb, toRgb, progress, 0);
        return (red << 16) | (green << 8) | blue;
    }

    private static int channel(int fromRgb, int toRgb, float progress,
                               int shift) {
        int from = (fromRgb >> shift) & 0xFF;
        int to = (toRgb >> shift) & 0xFF;
        return from + Math.round((to - from) * progress);
    }

    static void beginContent() {
        LostTalesSkyrimUiStyle.beginContent();
    }

    static void drawFormatted(FontRenderer font, IChatComponent line,
                              ChatHeadMarker.Data metadata,
                              int x, int y, int alpha, boolean chatOpen) {
        if (font == null || line == null || alpha < MIN_VISIBLE_ALPHA) {
            return;
        }
        beginContent();
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
        drawColored(font, text, x, y, rgb, alpha, 1.0F);
    }

    /**
     * A string carrying vanilla's section-sign codes, drawn in the
     * palette: each colour code becomes the palette's tone of that
     * colour ({@link #paletteRgb}), the decorations stay, and a reset
     * returns to ivory. What the hover cards for achievements and text
     * components draw with, so a tooltip reads in the same sixteen
     * colours as the line it hangs from.
     */
    static void drawLegacyFormatted(FontRenderer font, String text,
                                    int x, int y, int alpha) {
        if (font == null || text == null) {
            return;
        }
        int cursor = x;
        for (LegacyRun run : legacyRuns(text)) {
            drawColored(font, run.text, cursor, y, run.rgb, alpha);
            cursor += font.getStringWidth(run.text);
        }
    }

    /**
     * The runs a section-sign-coded string is drawn as: the text of each
     * with its decoration codes ahead of it, and the palette colour it
     * is drawn in. A colour code starts a run and clears the
     * decorations, as vanilla does; a reset does both and returns to
     * ivory; a code with nothing after it is not a code.
     */
    static List<LegacyRun> legacyRuns(String text) {
        List<LegacyRun> runs = new ArrayList<LegacyRun>();
        if (text == null || text.length() == 0) {
            return runs;
        }
        int rgb = IVORY;
        StringBuilder styles = new StringBuilder();
        StringBuilder pending = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '\u00a7' || index + 1 >= text.length()) {
                pending.append(character);
                continue;
            }
            char code = Character.toLowerCase(text.charAt(index + 1));
            EnumChatFormatting formatting = formattingOf(code);
            index++;
            if (formatting == null) {
                // FontRenderer treats an unknown code as white; the pair
                // is consumed and the run goes on.
                continue;
            }
            // Text before a code is drawn as it stood; the code shapes
            // what follows.
            if (pending.length() > 0) {
                runs.add(new LegacyRun(prefix(styles) + pending, rgb));
                pending.setLength(0);
            }
            if (formatting.isFancyStyling()) {
                styles.append('\u00a7').append(code);
                continue;
            }
            styles.setLength(0);
            rgb = formatting == EnumChatFormatting.RESET ? IVORY
                    : paletteRgb(formatting);
        }
        if (pending.length() > 0) {
            runs.add(new LegacyRun(prefix(styles) + pending, rgb));
        }
        return runs;
    }

    private static String prefix(StringBuilder styles) {
        return styles.length() == 0 ? "" : styles.toString();
    }

    private static EnumChatFormatting formattingOf(char code) {
        for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
            String written = formatting.toString();
            if (written.length() == 2 && written.charAt(1) == code) {
                return formatting;
            }
        }
        return null;
    }

    /** One run of a legacy-coded string: what is drawn, and in what colour. */
    static final class LegacyRun {
        /** The text with its decoration codes ahead of it, colours gone. */
        final String text;
        final int rgb;

        LegacyRun(String text, int rgb) {
            this.text = text;
            this.rgb = rgb & 0xFFFFFF;
        }
    }

    /**
     * As above for text drawn inside a scaled matrix: the offset is
     * given in that matrix's units so the shadow still lands one screen
     * pixel away, which is what every other shadow in the chat does.
     * A scale of one is the plain case.
     */
    static void drawColored(FontRenderer font, String text,
                            int x, int y, int rgb, int alpha, float scale) {
        if (font == null || text == null || alpha < MIN_VISIBLE_ALPHA) {
            return;
        }
        beginContent();
        int shadow = shadowAlpha(alpha);
        if (shadow > 0) {
            int offset = scale <= 0.0F ? SHADOW_OFFSET
                    : Math.max(1, Math.round(SHADOW_OFFSET / scale));
            font.drawString(text, x + offset, y + offset,
                    argb(SHADOW, shadow));
        }
        font.drawString(text, x, y, argb(rgb, alpha));
    }

    static int argb(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24)
                | (rgb & 0xFFFFFF);
    }

    /**
     * The palette entry a vanilla colour code stands for, so text the
     * chat did not compose itself — an achievement from vanilla or from
     * LOTR, another mod's notice, a player's own {@code &}-codes — reads
     * in the same sixteen colours everything else does. The mapping
     * keeps each code's identity: green stays green, yellow yellow,
     * only in the palette's own tones. Vanilla's white is the chat's
     * ivory, which is what {@link #removeExplicitWhite} already assumes.
     */
    static int paletteRgb(EnumChatFormatting formatting) {
        if (formatting == null) {
            return IVORY;
        }
        switch (formatting) {
            case BLACK:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.PLUM_BLACK);
            case DARK_BLUE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.INDIGO);
            case DARK_GREEN:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SEA_GREEN);
            case DARK_AQUA:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.TEAL);
            case DARK_RED:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.RUST);
            case DARK_PURPLE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MULBERRY);
            case GOLD:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.APRICOT);
            case GRAY:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.ROSE_GRAY);
            case DARK_GRAY:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MAUVE);
            case BLUE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.STEEL_BLUE);
            case GREEN:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.MEADOW_GREEN);
            case AQUA:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SEAFOAM);
            case RED:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.SALMON);
            case LIGHT_PURPLE:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.ORCHID);
            case YELLOW:
                return LostTalesSkyrimUiStyle.rgb(
                        LostTalesSkyrimUiStyle.HONEY);
            default:
                return IVORY;
        }
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
        int declared = ChatInlineIcons.declaredWidth(part);
        if (declared >= 0) {
            return declared;
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
    /** Whether the game's chat-links option is on: what gates a link, a command, a share. */
    static boolean chatLinksEnabled() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft == null || minecraft.gameSettings == null
                || minecraft.gameSettings.chatLinks;
    }

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
        // The hovered run is known by the row it is on and its place
        // there, never by identity: a row's iterator hands out copies.
        // Nothing on any other row answers to it.
        IChatComponent hovered = LostTalesChatPresentation.isHoveredLineRow(line)
                ? LostTalesChatPresentation.hoveredComponent() : null;
        // The sender is one thing under the pointer — the opening
        // bracket, the head, the name, the title and the closing
        // bracket — so resting on any of them underlines the whole
        // span, in the name's colour. Whether the pointer is on it is
        // the hover card's answer, measured against each part's own
        // pixels with the closing bracket's trailing gap left out, so
        // the rule, the card and the hand cursor agree to the pixel.
        boolean personHovered =
                LostTalesChatPresentation.isHoveredSenderRow(line);
        boolean identitySpan = false;
        // The rule under whatever is lit: one rectangle per unbroken
        // run of lit parts, in the colour the first of them is drawn
        // in, closed by the first part that is not lit and drawn once
        // its extent is known — so a name reads as one thing with its
        // head, brackets and title, and a link as one line.
        int ruleStart = -1;
        int ruleEnd = 0;
        int ruleColor = 0;
        int ruleTrailing = 0;
        int index = -1;
        for (Object value : line) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            index++;
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
            if (layout != null) {
                // Zero-width layout metadata; an indent marker insets a
                // continuation line under the message body.
                cursor += layout.indent(chatOpen);
                continue;
            }
            ChatReactionMarker.Data reaction = ChatReactionMarker.decode(part);
            if (reaction != null) {
                // A reaction chip is never underlined: it lights as a
                // whole under the pointer instead, on the same hit the
                // hand cursor and the click read. Its emoji and count
                // carry their own shadows, so the shadow pass leaves it.
                if (ruleStart >= 0) {
                    drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor,
                            alpha);
                    ruleStart = -1;
                }
                if (!shadowPass) {
                    drawReactionChip(font, reaction, cursor, y, alpha,
                            LostTalesChatPresentation.isHoveredRun(line, index),
                            colours);
                }
                cursor += reaction.width;
                continue;
            }
            String text = part.getUnformattedTextForChat();
            String formatting = part.getChatStyle().getFormattingCode();
            if (ChatSpoilerMarker.isRevealed(part)) {
                // A revealed spoiler is read in the clear: only the
                // style's own obfuscation goes — a sender's typed &k
                // lives in the text and stays whatever it was.
                formatting = formatting.replace("§k", "");
            }
            if (isWebLink(part)) {
                // A link another mod or Forge underlined for good is
                // drawn plain: the hover rule underlines it, as it does
                // every other run that answers to a click.
                formatting = formatting.replace("§n", "");
            }
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(part);
            if (marker != null) {
                afterHead = true;
            }
            boolean replyIdentity = ChatSenderSpan.isSenderName(part);
            // The span opens on the bracket, the name or the head —
            // an NPC's bracket carries no whisper, so its head opens
            // it — and closes after the run the closing bracket starts.
            if (!identitySpan && (replyIdentity || marker != null
                    || "<".equals(text))) {
                identitySpan = true;
            }
            boolean closesSpan = identitySpan && marker == null
                    && text.startsWith(">");
            boolean underlined = false;
            int underlineColor = IVORY;
            int width;

            ChatEmoji emoji = ChatEmojiMarker.decode(part);
            ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
            if (emoji != null) {
                width = measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(), emoji,
                            ChatInlineIcons.boxLeft(cursor, width),
                            ChatInlineIcons.boxTop(y, width),
                            ChatInlineIcons.contentSize(width), alpha,
                            shadowPass);
                }
            } else if (share != null && share.icon) {
                width = measure(font, formatting, text, colours);
                if (ChatEmojiMarker.reservesFullSlot(text)) {
                    drawShareIcon(share, cursor, y, width, alpha,
                            shadowPass);
                }
            } else if (ChatReplyMarker.isIconSlot(part)) {
                // The bubble a reply's quote opens with, in the quote's
                // own tone, standing on the caps as the typing line's
                // bubble does. A piece of the quote, it answers the
                // quote's click, but the rule starts after it: a line
                // under a bubble reads as a smudge.
                width = ChatReplyMarker.ICON_SLOT_WIDTH;
                Integer quoteColor = ChatReplyMarker.colorOf(part);
                ChatIconSheet bubble = ChatIconSheet.SPEECH_BUBBLE;
                bubble.drawSilhouette(shadowPass ? SHADOW
                                : !colours || quoteColor == null ? IVORY
                                        : quoteColor.intValue(),
                        cursor, y + (7 - bubble.getHeight()) / 2, alpha);
            } else if (ChatChannelLinkMarker.isIconSlot(part)) {
                // The bubble of a link to a message, in the link's own
                // colour, centred in the slot its two spaces reserve. It
                // is a piece of the link and answers the click like the
                // name before it, but the rule stops short of it: a line
                // under a bubble reads as a smudge.
                width = measure(font, formatting, text, colours);
                Integer linkColor = ChatChannelLinkMarker.colorOf(part);
                ChatInlineIcons.drawSheetSprite(ChatIconSheet.SPEECH_BUBBLE,
                        ChatInlineIcons.boxLeft(cursor, width),
                        ChatInlineIcons.boxTop(y, width),
                        ChatInlineIcons.contentSize(width),
                        !colours || linkColor == null ? IVORY
                                : linkColor.intValue(),
                        alpha, shadowPass);
            } else {
                String rendered;
                int color;
                Integer prefixColor = ChatPrefixMarker.decode(part);
                Integer explicitColor = ChatColorMarker.decode(part);
                if (explicitColor == null) {
                    // The chevron a body opens with wears the sender's colour.
                    explicitColor = ChatBodyMarker.decode(part);
                }
                if (explicitColor == null) {
                    // A mention re-resolves as it is drawn, so one built
                    // before this client learned the roles behind the name
                    // catches up instead of keeping the fallback forever.
                    explicitColor = ChatMentionColors.liveMentionColor(
                            ChatMentionMarker.decode(part));
                }
                if (explicitColor == null) {
                    explicitColor = ChatTitleMarker.colorOf(part);
                }
                if (explicitColor == null) {
                    explicitColor = ChatReplyMarker.colorOf(part);
                }
                if (explicitColor == null) {
                    explicitColor = ChatChannelLinkMarker.colorOf(part);
                }
                boolean identityBracket = "<".equals(text)
                        || (identitySeen && text.startsWith(">"));
                // The colour the glyphs are drawn in; the rule under a
                // lit run takes the same, read off the inline codes
                // where the text carries its own.
                int glyphColor;
                if (!colours) {
                    rendered = stripCodes(formatting + text);
                    color = shadowPass ? SHADOW : IVORY;
                    glyphColor = IVORY;
                } else if (shadowPass) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = SHADOW;
                    glyphColor = SHADOW;
                } else if (share != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = share.textColor;
                    glyphColor = color;
                } else if (prefixColor != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = prefixColor.intValue();
                    glyphColor = color;
                } else if (explicitColor != null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = explicitColor.intValue();
                    glyphColor = color;
                } else if (metadata != null && identityBracket) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.nameColor;
                    glyphColor = color;
                } else if (metadata != null && replyIdentity) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.nameColor;
                    glyphColor = color;
                } else if (metadata != null && afterHead
                        && !identitySeen && marker == null) {
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = metadata.titleColor;
                    glyphColor = color;
                } else if (part.getChatStyle().getColor() != null) {
                    // A line from vanilla, LOTR or any other mod says what
                    // colour it wants in vanilla's sixteen; it is drawn in
                    // the palette's nearest, so an achievement is still
                    // green (or yellow, or purple) but in the chat's own
                    // greens and yellows rather than beside them.
                    rendered = styleCodesOnly(formatting)
                            + removeColorCodes(text);
                    color = paletteRgb(part.getChatStyle().getColor());
                    glyphColor = color;
                } else {
                    rendered = removeExplicitWhite(formatting + text);
                    color = IVORY;
                    glyphColor = lastInlineColor(rendered, IVORY);
                }
                // Asked again for every run: an inline glyph between two of
                // them is drawn by code of its own, and whatever that leaves
                // behind must not decide what the next run's shadow looks
                // like.
                beginContent();
                font.drawString(rendered, cursor, y, argb(color, alpha));
                int declared = ChatInlineIcons.declaredWidth(part);
                width = declared >= 0 ? declared
                        : measure(font, formatting, text, colours);
                if (personHovered && identitySpan) {
                    // Every part of the sender, the head's slot and the
                    // title and the spacers between included, under one
                    // rule in the name's colour.
                    underlined = true;
                    underlineColor = !colours ? IVORY
                            : marker != null ? marker.nameColor
                            : metadata != null ? metadata.nameColor
                            : glyphColor;
                } else if (marker == null) {
                    // A run that answers to a click is underlined while
                    // the pointer rests on it — or on a run acting with
                    // it, so a reply's quote and the pieces of one link
                    // light together — in its own colour, on the row the
                    // font's own underline takes, so it reads as usable
                    // before it is used. A run that only carries a card
                    // stays plain: the rule promises a click. The head
                    // slot a quote wears is a run of the quote and lights
                    // with it, so the rule runs under the quote whole.
                    underlined = hovered != null
                            && (rendered.trim().length() > 0
                                    || ChatReplyMarker.headOf(part) != null)
                            && ChatInteractions.answersClick(part,
                                    chatLinksEnabled())
                            && sharesInteraction(part, line, index, hovered);
                    underlineColor = glyphColor;
                }
            }

            if (underlined && width > 0) {
                if (ruleStart < 0) {
                    ruleStart = cursor;
                    ruleColor = shadowPass ? SHADOW : underlineColor;
                }
                ruleEnd = cursor + width;
                // The last run's trailing spaces belong to the gap
                // after it, not to what is lit.
                ruleTrailing = ChatInlineIcons.declaredWidth(part) >= 0 ? 0
                        : trailingSpaceWidth(font, text);
            } else if (ruleStart >= 0) {
                drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor,
                        alpha);
                ruleStart = -1;
            }
            cursor += width;
            identitySeen |= replyIdentity;
            if (closesSpan) {
                identitySpan = false;
            }
        }
        if (ruleStart >= 0) {
            drawRule(ruleStart, ruleEnd - ruleTrailing, y, ruleColor, alpha);
        }
    }

    /**
     * One reaction chip: a pill a pixel taller than the line's glyphs
     * on either side, corners cut, holding the emoji at its native size
     * and the count. A chip the reader is in wears the accent on its
     * edge and its count; the hovered one takes the hover fill, the way
     * every panel row in the palette does.
     */
    private static void drawReactionChip(FontRenderer font,
                                         ChatReactionMarker.Data chip,
                                         int x, int y, int alpha,
                                         boolean hovered, boolean colours) {
        int left = x;
        int right = x + chip.width;
        int top = y - 3;
        int bottom = y + 9;
        int accent = LostTalesColors.rgb(LostTalesColors.HONEY);
        int fill = argb(hovered ? LostTalesColors.rgb(LostTalesColors.PANEL_HOVER)
                : SURFACE_RGB, Math.round(alpha * 0.9F));
        int edge = argb(chip.mine && colours ? accent : hovered ? IVORY
                : LostTalesColors.rgb(LostTalesColors.BORDER_DIM), alpha);
        Gui.drawRect(left + 1, top + 1, right - 1, bottom - 1, fill);
        Gui.drawRect(left + 1, top, right - 1, top + 1, edge);
        Gui.drawRect(left + 1, bottom - 1, right - 1, bottom, edge);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, edge);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, edge);
        beginContent();
        ChatInlineIcons.drawEmoji(Minecraft.getMinecraft(), chip.emoji,
                left + ChatReactionMarker.PAD,
                y + ChatInlineIcons.CONTENT_TOP_OFFSET,
                ChatInlineIcons.CONTENT_SIZE, alpha);
        beginContent();
        drawColored(font, chip.countText(), left + ChatReactionMarker.PAD
                + ChatReactionMarker.ICON + ChatReactionMarker.GAP, y,
                chip.mine && colours ? accent : IVORY, alpha);
    }

    /**
     * The row under a run's glyphs the font draws its own underline on:
     * one above the row the shadow of the descenders reaches.
     */
    private static final int UNDERLINE_ROW = 8;

    /**
     * One rule from {@code start} to {@code end}, the last pixel left
     * to the glyph's own trailing gap so the rule ends with the glyphs.
     */
    private static void drawRule(int start, int end, int y, int color,
                                 int alpha) {
        if (end - 1 > start) {
            LostTalesChatOverlayRenderer.fillRect(start, y + UNDERLINE_ROW,
                    end - 1, y + UNDERLINE_ROW + 1, argb(color, alpha));
        }
    }

    /** Whether the run opens a web address on a click. */
    private static boolean isWebLink(IChatComponent part) {
        ClickEvent click = part.getChatStyle() == null ? null
                : part.getChatStyle().getChatClickEvent();
        return click != null && click.getAction() == ClickEvent.Action.OPEN_URL;
    }

    /** The width of the spaces a run ends with. */
    private static int trailingSpaceWidth(FontRenderer font, String text) {
        int spaces = 0;
        for (int at = text.length() - 1; at >= 0 && text.charAt(at) == ' ';
             at--) {
            spaces++;
        }
        return spaces * font.getCharWidth(' ');
    }

    /**
     * The colour the last inline colour code of a run leaves its glyphs
     * in, in the palette's tone, or {@code fallback} when the run
     * carries none.
     */
    private static int lastInlineColor(String rendered, int fallback) {
        int color = fallback;
        for (int at = 0; at + 1 < rendered.length(); at++) {
            if (rendered.charAt(at) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(rendered.charAt(at + 1));
            for (EnumChatFormatting formatting : EnumChatFormatting.values()) {
                if (formatting.isColor()
                        && formatting.getFormattingCode() == code) {
                    color = paletteRgb(formatting);
                    break;
                }
            }
            at++;
        }
        return color;
    }

    /**
     * Whether the run at {@code index} of {@code line} acts with the
     * hovered one, which is on the same row: it is the hovered run, or
     * it answers exactly as that run does — the runs of one reply quote
     * share the message they lead to, the pieces of one link share its
     * address — so all of it is underlined together. Nothing on
     * another row ever acts with it.
     */
    private static boolean sharesInteraction(IChatComponent part,
                                             IChatComponent line, int index,
                                             IChatComponent hovered) {
        if (part == null || hovered == null) {
            return false;
        }
        if (LostTalesChatPresentation.isHoveredRun(line, index)) {
            return true;
        }
        if (ChatReplyMarker.isMarker(part)) {
            return ChatReplyMarker.isMarker(hovered);
        }
        if (ChatChannelLinkMarker.isMarker(part)) {
            return ChatChannelLinkMarker.sameLink(part, hovered);
        }
        ClickEvent own = ChatInteractions.genuineClick(part);
        ClickEvent theirs = ChatInteractions.genuineClick(hovered);
        return own != null && theirs != null
                && own.getAction() == theirs.getAction()
                && own.getValue() != null
                && own.getValue().equals(theirs.getValue());
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
