package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.List;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Which runs wear a backdrop, the room it takes, and its colour. */
public final class ChatRunBackdropsTest {
    private static final int PAD = ChatRunBackdrops.PAD;
    private static final int PLUM_BLACK = LostTalesColors.rgb(LostTalesColors.PLUM_BLACK);

    private static ChatComponentText mention(String name) {
        return ChatMentionMarker.apply(new ChatComponentText("@" + name),
                ChatMentionColors.PLAYER_RGB, name, null);
    }

    private static ChatComponentText link(String text, long messageId) {
        ChatComponentText run = new ChatComponentText(text);
        return messageId == 0L
                ? ChatChannelLinkMarker.apply(run, 0x64B082, "global", 0)
                : ChatChannelLinkMarker.applyMessage(run, 0x64B082, "global", messageId);
    }

    private static ChatComponentText achievement(String text) {
        ChatComponentText run = new ChatComponentText(text);
        run.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ACHIEVEMENT,
                        new ChatComponentText("achievement.openInventory"))));
        return run;
    }

    /** Mentions, links, shares and achievements wear one; web links, plain text and spoilers do not. */
    @Test
    public void onlyLinkableRunsWearABackdrop() {
        assertEquals(ChatRunBackdrops.Kind.PLAYER, ChatRunBackdrops.kindOf(mention("Nils")));
        assertEquals(ChatRunBackdrops.Kind.LINK, ChatRunBackdrops.kindOf(link("#Global", 0L)));
        assertEquals(ChatRunBackdrops.Kind.SHARE, ChatRunBackdrops.kindOf(
                ChatShowcaseMarker.createIcon(ChatShareKind.ITEM, 3)));
        assertEquals(ChatRunBackdrops.Kind.ACHIEVEMENT,
                ChatRunBackdrops.kindOf(achievement("Taking Inventory")));
        assertEquals(ChatRunBackdrops.Kind.NONE,
                ChatRunBackdrops.kindOf(new ChatComponentText("hello")));
        ChatComponentText web = new ChatComponentText("https://example.org");
        web.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.org")));
        assertEquals(ChatRunBackdrops.Kind.NONE, ChatRunBackdrops.kindOf(web));
        // A spoiler hides whatever is inside it, its backdrop too.
        ChatComponentText hidden = mention("Nils");
        hidden.getChatStyle().setObfuscated(Boolean.TRUE);
        assertEquals(ChatRunBackdrops.Kind.NONE, ChatRunBackdrops.kindOf(hidden));
        assertEquals(0, ChatRunBackdrops.pads(hidden));
    }

    /** A run alone pads both sides; an element of several runs pads where it opens and closes. */
    @Test
    public void theBackdropsPaddingIsWhereItOpensAndCloses() {
        assertEquals(2 * PAD, ChatRunBackdrops.pads(mention("Nils")));
        assertEquals(2 * PAD, ChatRunBackdrops.pads(link("#Global", 0L)));
        // A link to a message: its name, the arrow and the bubble.
        assertEquals(PAD, ChatRunBackdrops.padBefore(link("#Global", 1234L)));
        assertEquals(0, ChatRunBackdrops.padAfter(link("#Global", 1234L)));
        assertEquals(0, ChatRunBackdrops.pads(
                link(ChatChannelLinkMarker.MESSAGE_SEPARATOR, 1234L)));
        assertEquals(0, ChatRunBackdrops.padBefore(link(ChatChannelLinkMarker.ICON_SLOT, 1234L)));
        assertEquals(PAD, ChatRunBackdrops.padAfter(link(ChatChannelLinkMarker.ICON_SLOT, 1234L)));
        // A link to one of this client's own lines, as the Server Console's
        // "used /command in #Channel" names the command, is one element
        // too: the same three pieces, one backdrop.
        ChatComponentText localName = ChatChannelLinkMarker.apply(
                new ChatComponentText("#Global"), 0x64B082, "global", 42);
        ChatComponentText localArrow = ChatChannelLinkMarker.apply(
                new ChatComponentText(ChatChannelLinkMarker.MESSAGE_SEPARATOR),
                0x64B082, "global", 42);
        ChatComponentText localSlot = ChatChannelLinkMarker.apply(
                new ChatComponentText(ChatChannelLinkMarker.ICON_SLOT),
                0x64B082, "global", 42);
        assertEquals(PAD, ChatRunBackdrops.padBefore(localName));
        assertEquals(0, ChatRunBackdrops.padAfter(localName));
        assertEquals(0, ChatRunBackdrops.pads(localArrow));
        assertEquals(0, ChatRunBackdrops.padBefore(localSlot));
        assertEquals(PAD, ChatRunBackdrops.padAfter(localSlot));
        // A share: its icon opens, its name closes; the backdrop is their frame.
        IChatComponent icon = ChatShowcaseMarker.createIcon(ChatShareKind.ITEM, 3);
        IChatComponent name = ChatShowcaseMarker.createText(ChatShareKind.ITEM, 3,
                " Iron Sword", EnumChatFormatting.WHITE, 0xFFFFFF);
        assertEquals(PAD, ChatRunBackdrops.padBefore(icon));
        assertEquals(0, ChatRunBackdrops.padAfter(icon));
        assertEquals(0, ChatRunBackdrops.padBefore(name));
        assertEquals(PAD, ChatRunBackdrops.padAfter(name));
        // Every piece of a name split over rows closes its own backdrop.
        assertEquals(PAD, ChatRunBackdrops.pads(name, " Iron"));
        // An achievement is one run, its name alone.
        assertEquals(2 * PAD, ChatRunBackdrops.pads(achievement("Taking Inventory")));
    }

    /** Pings are seafoam on slate blue; the rest stand on the darkest shade of their own family. */
    @Test
    public void aBackdropIsTheDarkestShadeOfItsWordsFamily() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.SLATE_BLUE),
                ChatRunBackdrops.backdropRgb(mention("Nils"), ChatMentionColors.PLAYER_RGB,
                        PLUM_BLACK, true));
        assertEquals(LostTalesColors.rgb(LostTalesColors.HARBOR_BLUE),
                ChatRunBackdrops.backdropRgb(link("#Global", 0L),
                        LostTalesColors.rgb(LostTalesColors.FERN_GREEN), PLUM_BLACK, true));
        // Ivory's darkest is the window's own plum black, so a step up.
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_DARK),
                ChatRunBackdrops.backdropRgb(link("#Global", 0L),
                        LostTalesColors.rgb(LostTalesColors.IVORY), PLUM_BLACK, true));
        // With the game's chat colours off every word is ivory, and so is every family.
        assertEquals(LostTalesColors.rgb(LostTalesColors.PLUM_DARK),
                ChatRunBackdrops.backdropRgb(mention("Nils"), ChatMentionColors.PLAYER_RGB,
                        PLUM_BLACK, false));
    }

    /** Every piece of one element answers to one key; two elements never share one. */
    @Test
    public void everyPieceOfAnElementSharesItsKey() {
        assertEquals(ChatRunBackdrops.keyOf(ChatShowcaseMarker.createIcon(ChatShareKind.ITEM, 3)),
                ChatRunBackdrops.keyOf(ChatShowcaseMarker.createText(ChatShareKind.ITEM, 3,
                        " Iron Sword", EnumChatFormatting.WHITE, 0xFFFFFF)));
        assertNotEquals(ChatRunBackdrops.keyOf(mention("Nils")),
                ChatRunBackdrops.keyOf(mention("Sam")));
        assertEquals(ChatRunBackdrops.keyOf(link("#Global", 1234L)),
                ChatRunBackdrops.keyOf(link(ChatChannelLinkMarker.ICON_SLOT, 1234L)));
    }

    /**
     * An achievement arrives as vanilla builds it, its name in square
     * brackets with its hover on the whole, and stands as one run of its
     * name, keeping its colour and hover.
     */
    @Test
    public void anAchievementLosesItsBrackets() {
        ChatComponentText wrapped = new ChatComponentText("[");
        wrapped.appendSibling(new ChatComponentText("Taking Inventory"));
        wrapped.appendText("]");
        wrapped.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GREEN)
                .setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ACHIEVEMENT,
                        new ChatComponentText("achievement.openInventory"))));
        IChatComponent run = LostTalesChatPresentation.unbracketedAchievement(wrapped);
        assertEquals("Taking Inventory", run.getUnformattedText());
        assertTrue(run.getSiblings().isEmpty());
        assertEquals(EnumChatFormatting.GREEN, run.getChatStyle().getColor());
        assertTrue(ChatInteractions.isAchievement(run));
        assertEquals(null, LostTalesChatPresentation.unbracketedAchievement(
                new ChatComponentText("[not one]")));
    }

    /** A lit backdrop's words turn ivory. */
    @Test
    public void litWordsTurnIvory() {
        int seafoam = ChatMentionColors.PLAYER_RGB;
        assertEquals(seafoam, ChatRunBackdrops.wordsRgb(seafoam, 0.0F));
        assertEquals(LostTalesChatVisualStyle.IVORY, ChatRunBackdrops.wordsRgb(seafoam, 1.0F));
    }

    /**
     * The padding is room the layout gives: a mention that fits a row
     * bare moves down whole once its backdrop's four pixels are counted.
     */
    @Test
    public void wrappingMakesRoomForTheBackdrop() {
        ChatLineWrapper.TextMetrics sixEach = new ChatLineWrapper.TextMetrics() {
            @Override
            public int width(String text) {
                return 6 * text.length();
            }
        };
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(new ChatComponentText("hi "));
        root.appendSibling(mention("Nils"));
        // "hi " and "@Nils" are 48 pixels bare, 52 with the backdrop.
        List<IChatComponent> bare = ChatLineWrapper.wrap(sixEach, root, 52);
        assertEquals(1, bare.size());
        List<IChatComponent> tight = ChatLineWrapper.wrap(sixEach, root, 50);
        assertEquals(2, tight.size());
        assertTrue(ChatLineWrapper.partWidth(sixEach, mention("Nils"))
                == sixEach.width("@Nils") + 2 * PAD);
        assertFalse(ChatLineWrapper.partWidth(sixEach, new ChatComponentText("@Nils"))
                != sixEach.width("@Nils"));
    }
}
