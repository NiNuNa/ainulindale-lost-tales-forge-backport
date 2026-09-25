package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.IChatComponent;

/**
 * The backdrop a linkable run stands on, the way Discord draws a
 * mention: a darker shade behind the words, two clear pixels either side
 * of them, and the four corner pixels cut so it reads rounded. A player
 * mention is seafoam on slate blue. A role mention, a channel or message
 * link, a shared item, map marker or quest, and an achievement keep
 * their own colour, on the darkest shade of its family
 * ({@link LostTalesColors#darkestShade}). A web link has none, and nor
 * has anything a spoiler hides. The pointer lights a backdrop one shade
 * and turns its words ivory, where any other run is underlined.
 *
 * <p>A backdrop is laid over whatever its row stands on — the window's
 * band, a mention's tint, the typing well — at {@link #OPACITY} of its
 * words' own opacity, so it fades and comes in with them and a little of
 * what lies behind shows through, as through Discord's mention pill. It
 * frames what it stands under, so a share and an achievement wear no
 * square brackets. Its padding is part of the run's width, so wrapping,
 * drawing and every hit test make room for it alike. A run that opens a
 * backdrop has two pixels before it, and a run that closes one has two
 * after it: a mention and an achievement do both, a share's icon opens
 * and its name closes. A run split over two rows is a backdrop on
 * each.</p>
 */
final class ChatRunBackdrops {
    /** The clear pixels between a backdrop's edge and its words. */
    static final int PAD = 2;
    /** Where a backdrop starts against its text's top: two rows above the capitals. */
    static final int TOP = -2;
    /** Where it ends: two rows below the capitals, the descenders and their shadow inside. */
    static final int BOTTOM = LostTalesUiInk.CAP_HEIGHT + 2;
    /** A player mention's backdrop. */
    static final int PLAYER_RGB = LostTalesColors.rgb(LostTalesColors.SLATE_BLUE);
    /** How opaque a backdrop is against its words. */
    static final float OPACITY = 2.0F / 3.0F;

    /** What a run's backdrop stands for. */
    enum Kind { NONE, PLAYER, ROLE, LINK, SHARE, ACHIEVEMENT }

    /** One backdrop laid out on a row: its edges, the words they ride on, its colour and its light. */
    private static final class Pill {
        final int left;
        final int firstWord;
        final int rgb;
        final float lit;
        int right;
        int lastWord;

        Pill(int left, int firstWord, int rgb, float lit) {
            this.left = left;
            this.firstWord = firstWord;
            this.lastWord = firstWord;
            this.rgb = rgb;
            this.lit = lit;
        }
    }

    private ChatRunBackdrops() {}

    /** What a run's backdrop stands for; none for a run without one. */
    static Kind kindOf(IChatComponent part) {
        if (part == null || part.getChatStyle() == null
                || part.getChatStyle().getObfuscated()) {
            return Kind.NONE;
        }
        ChatMentionMarker.Data mention = ChatMentionMarker.decode(part);
        if (mention != null) {
            return mention.isRole() ? Kind.ROLE : Kind.PLAYER;
        }
        if (ChatChannelLinkMarker.isMarker(part)) {
            return Kind.LINK;
        }
        if (ChatShowcaseMarker.decode(part) != null) {
            return Kind.SHARE;
        }
        return ChatInteractions.isAchievement(part) ? Kind.ACHIEVEMENT : Kind.NONE;
    }

    /** The clear pixels before a run: {@link #PAD} for one that opens a backdrop. */
    static int padBefore(IChatComponent part) {
        Kind kind = kindOf(part);
        return kind != Kind.NONE && opens(kind, part, textOf(part)) ? PAD : 0;
    }

    /** The clear pixels after a run: {@link #PAD} for one that closes a backdrop. */
    static int padAfter(IChatComponent part) {
        Kind kind = kindOf(part);
        return kind != Kind.NONE && closes(kind, part, textOf(part)) ? PAD : 0;
    }

    /** Both of a run's paddings together. */
    static int pads(IChatComponent part) {
        return pads(part, part.getUnformattedTextForChat());
    }

    /**
     * Both paddings of a piece of {@code part} that reads {@code text}:
     * what a run split over rows takes on each of them.
     */
    static int pads(IChatComponent part, String text) {
        Kind kind = kindOf(part);
        if (kind == Kind.NONE) {
            return 0;
        }
        String plain = LostTalesChatVisualStyle.removeColorCodes(text);
        return (opens(kind, part, plain) ? PAD : 0)
                + (closes(kind, part, plain) ? PAD : 0);
    }

    /**
     * Whether a run opens its backdrop: a mention and an achievement
     * always do, a link to a channel too, a link to a message with its
     * name (the arrow and the bubble come after), and a share with its
     * icon.
     */
    private static boolean opens(Kind kind, IChatComponent part, String text) {
        switch (kind) {
            case PLAYER:
            case ROLE:
                return true;
            case LINK:
                return !ChatChannelLinkMarker.linksMessage(part)
                        || (!ChatChannelLinkMarker.ICON_SLOT.equals(text)
                                && !ChatChannelLinkMarker.MESSAGE_SEPARATOR.equals(text));
            case SHARE:
                return ChatShowcaseMarker.decode(part).icon;
            case ACHIEVEMENT:
                return true;
            default:
                return false;
        }
    }

    /**
     * Whether a run closes its backdrop: a mention, an achievement and a
     * link to a channel always do, a link to a message with its bubble,
     * and a share with its name.
     */
    private static boolean closes(Kind kind, IChatComponent part, String text) {
        switch (kind) {
            case PLAYER:
            case ROLE:
                return true;
            case LINK:
                return !ChatChannelLinkMarker.linksMessage(part)
                        || ChatChannelLinkMarker.ICON_SLOT.equals(text);
            case SHARE:
                return !ChatShowcaseMarker.decode(part).icon;
            case ACHIEVEMENT:
                return true;
            default:
                return false;
        }
    }

    private static String textOf(IChatComponent part) {
        return LostTalesChatVisualStyle.removeColorCodes(part.getUnformattedTextForChat());
    }

    /**
     * The colour of the backdrop under a run whose words are drawn in
     * {@code wordsRgb}, on a surface of {@code surfaceRgb}: slate blue
     * under a player mention, the darkest shade of the words' own family
     * under anything else. With the game's chat colours off, every
     * backdrop is ivory's family, as every word is ivory.
     */
    static int backdropRgb(IChatComponent part, int wordsRgb, int surfaceRgb,
                           boolean colours) {
        if (colours && kindOf(part) == Kind.PLAYER) {
            return PLAYER_RGB;
        }
        return LostTalesColors.darkestShade(colours ? wordsRgb
                : LostTalesUiInk.IVORY, surfaceRgb);
    }

    /**
     * What tells one backdropped element from another: every piece of one
     * mention, link, share or achievement answers the same, on every row
     * it spans.
     */
    static String keyOf(IChatComponent part) {
        ChatShowcaseMarker.Data share = ChatShowcaseMarker.decode(part);
        if (share != null) {
            return "share:" + share.kind + ":" + share.showcaseId;
        }
        if (ChatInteractions.isAchievement(part)) {
            HoverEvent hover = part.getChatStyle().getChatHoverEvent();
            return "achievement:" + (hover.getValue() == null ? ""
                    : hover.getValue().getUnformattedText());
        }
        ClickEvent click = part.getChatStyle().getChatClickEvent();
        return click == null || click.getValue() == null ? "" : click.getValue();
    }

    /**
     * How far a run's backdrop is lit this frame: all the way while the
     * pointer rests on its element in the message {@code chatLineId},
     * not at all while it does not, and between them on the controls' own
     * crossfade. Every piece of the element reads the same, on every row.
     */
    static float litShare(int chatLineId, IChatComponent part) {
        IChatComponent hovered = LostTalesChatPresentation.hoveredComponent();
        String key = keyOf(part);
        boolean lit = chatLineId != 0 && hovered != null
                && LostTalesChatPresentation.isHoveredLine(chatLineId)
                && kindOf(hovered) != Kind.NONE && key.equals(keyOf(hovered));
        return LostTalesChatPresentation.backdropHoverFade(chatLineId, key, lit);
    }

    /** The words of a backdrop lit {@code lit}: their own colour crossed toward ivory. */
    static int wordsRgb(int rgb, float lit) {
        return lit <= 0.0F ? rgb
                : LostTalesUiInk.blend(rgb, LostTalesUiInk.IVORY, lit);
    }

    /**
     * Draws the backdrops of one row in its text space, the text's top at
     * zero, before its words: each run that wears one, from the run that
     * opens it to the one that closes it or the row's end, on a surface
     * of {@code surfaceRgb} under words drawn at {@code wordsAlpha}. In a
     * row the pointer moves, a backdrop's edges ride the words at either
     * end of it, so it stretches as the gaps between them open.
     */
    static void draw(FontRenderer font, IChatComponent row, int chatLineId,
                     int surfaceRgb, int wordsAlpha, boolean chatOpen,
                     ChatRowMotion motion) {
        if (font == null || row == null || wordsAlpha <= 0) {
            return;
        }
        boolean colours = LostTalesChatVisualStyle.chatColoursEnabled();
        int words = motion == null ? 0 : LostTalesChatVisualStyle.countWords(row, chatOpen);
        int word = 0;
        int cursor = 0;
        Pill pill = null;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            ChatLayoutMarker.Data layout = ChatLayoutMarker.decode(part);
            if (layout != null) {
                cursor += layout.indent(chatOpen);
                continue;
            }
            if (ChatStampMarker.isMarker(part) || ChatReactionMarker.isAddButton(part)) {
                pill = finish(pill, wordsAlpha, motion, words);
                cursor += ChatInlineIcons.declaredWidth(part);
                continue;
            }
            ChatReactionMarker.Data reaction = ChatReactionMarker.decode(part);
            if (reaction != null) {
                pill = finish(pill, wordsAlpha, motion, words);
                cursor += reaction.width;
                continue;
            }
            int before = padBefore(part);
            int after = padAfter(part);
            int width = LostTalesChatVisualStyle.partWidth(font, part, chatOpen)
                    - before - after;
            int firstWord = word;
            if (motion != null) {
                word += LostTalesChatVisualStyle.wordsIn(part);
            }
            if (kindOf(part) == Kind.NONE) {
                pill = finish(pill, wordsAlpha, motion, words);
            } else {
                if (pill == null || before > 0) {
                    pill = finish(pill, wordsAlpha, motion, words);
                    int wordsRgb = LostTalesChatVisualStyle.runRgb(part);
                    pill = new Pill(cursor + before - PAD, firstWord,
                            backdropRgb(part, wordsRgb, surfaceRgb, colours),
                            litShare(chatLineId, part));
                }
                // Two clear pixels past the run's last ink: its last
                // glyph's own spacing column and one more.
                int ink = LostTalesChatVisualStyle.drawsSlot(part) ? width
                        : width - LostTalesChatVisualStyle.trailingSpaceWidth(font,
                                part.getUnformattedTextForChat()) - 1;
                pill.right = cursor + before + ink + PAD;
                pill.lastWord = Math.max(pill.firstWord, word - 1);
                if (after > 0) {
                    pill = finish(pill, wordsAlpha, motion, words);
                }
            }
            cursor += before + width + after;
        }
        finish(pill, wordsAlpha, motion, words);
    }

    /** Draws a laid-out backdrop, if there is one; answers null, the row's next one not yet begun. */
    private static Pill finish(Pill pill, int wordsAlpha, ChatRowMotion motion,
                               int words) {
        if (pill == null || pill.right <= pill.left) {
            return null;
        }
        float left = pill.left;
        float right = pill.right;
        if (motion != null) {
            left += motion.wordX(pill.firstWord, words);
            right += motion.wordX(pill.lastWord, words);
        }
        fill(left, TOP, right, BOTTOM, LostTalesUiInk.blend(pill.rgb,
                LostTalesChatVisualStyle.lighterShadeOf(pill.rgb), pill.lit),
                wordsAlpha);
        return null;
    }

    /**
     * Lays a backdrop from {@code left} to {@code right} and {@code top} to
     * {@code bottom} over what is already drawn there, in {@code rgb} at
     * {@link #OPACITY} of {@code wordsAlpha}, less its four corner pixels
     * so it reads rounded.
     */
    static void fill(float left, float top, float right, float bottom,
                     int rgb, int wordsAlpha) {
        int alpha = Math.round(Math.max(0, Math.min(255, wordsAlpha)) * OPACITY);
        if (right - left < 2.0F || bottom - top < 2.0F || alpha <= 0) {
            return;
        }
        Tessellator tessellator = LostTalesSkyrimUiStyle.beginQuads(false);
        tessellator.setColorRGBA_I(rgb & 0xFFFFFF, alpha);
        quad(tessellator, left + 1.0F, top, right - 1.0F, bottom);
        quad(tessellator, left, top + 1.0F, left + 1.0F, bottom - 1.0F);
        quad(tessellator, right - 1.0F, top + 1.0F, right, bottom - 1.0F);
        LostTalesSkyrimUiStyle.endQuads(tessellator, false);
    }

    private static void quad(Tessellator tessellator, float left, float top,
                             float right, float bottom) {
        tessellator.addVertex(left, bottom, 0.0D);
        tessellator.addVertex(right, bottom, 0.0D);
        tessellator.addVertex(right, top, 0.0D);
        tessellator.addVertex(left, top, 0.0D);
    }
}
