package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An element the pointer uses is one element however many runs it is
 * drawn as: the brackets and name of an achievement, the brackets, icon
 * and name of a share, the pieces of one link all light, underline and
 * answer together, and two elements side by side never do.
 */
public final class ChatInteractionsElementTest {

    @Test
    public void anAchievementsBracketsAndNameAreOneElement() {
        IChatComponent open = achievement("[", "achievement.openInventory");
        IChatComponent name = achievement("First Steps",
                "achievement.openInventory");
        IChatComponent close = achievement("]", "achievement.openInventory");
        assertTrue(ChatInteractions.sameElement(open, name));
        assertTrue(ChatInteractions.sameElement(close, name));
        assertTrue(ChatInteractions.sameElement(name, open));
        IChatComponent other = achievement("Getting Wood",
                "achievement.mineWood");
        assertFalse(ChatInteractions.sameElement(other, name));
        assertFalse(ChatInteractions.sameElement(
                new ChatComponentText("["), name));
    }

    @Test
    public void aSharesBracketIconAndNameAreOneElement() {
        IChatComponent open = ChatShowcaseMarker.createText(
                ChatShareKind.MARKER, 3, "[", EnumChatFormatting.WHITE,
                0xFFFFFF);
        IChatComponent icon = ChatShowcaseMarker.createIcon(
                ChatShareKind.MARKER, 3);
        IChatComponent name = ChatShowcaseMarker.createText(
                ChatShareKind.MARKER, 3, " Northgate]",
                EnumChatFormatting.WHITE, 0xFFFFFF);
        assertTrue(ChatInteractions.sameElement(open, icon));
        assertTrue(ChatInteractions.sameElement(icon, name));
        assertTrue(ChatInteractions.sameElement(name, open));
        IChatComponent another = ChatShowcaseMarker.createText(
                ChatShareKind.MARKER, 4, "[", EnumChatFormatting.WHITE,
                0xFFFFFF);
        assertFalse(ChatInteractions.sameElement(another, name));
    }

    @Test
    public void aLinksPiecesAreOneElementAndTwoLinksAreTwo() {
        IChatComponent one = link("https://example.org/a", "example");
        IChatComponent two = link("https://example.org/a", ".org");
        IChatComponent elsewhere = link("https://example.org/b", "b");
        assertTrue(ChatInteractions.sameElement(one, two));
        assertFalse(ChatInteractions.sameElement(one, elsewhere));
    }

    private static IChatComponent achievement(String text, String statId) {
        ChatComponentText run = new ChatComponentText(text);
        run.setChatStyle(new ChatStyle().setChatHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_ACHIEVEMENT,
                new ChatComponentText(statId))));
        return run;
    }

    private static IChatComponent link(String url, String text) {
        ChatComponentText run = new ChatComponentText(text);
        run.setChatStyle(new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, url)));
        return run;
    }
}
