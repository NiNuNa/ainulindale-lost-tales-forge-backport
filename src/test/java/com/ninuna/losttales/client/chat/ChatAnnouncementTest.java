package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatSystemLineClassifier;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A join, a leave, a death or an achievement is a sentence the server
 * says, and ends with its full stop, as the console's entries do;
 * anything else — a {@code /say} above all — stays exactly as it was
 * said, and so does a line that already ends a sentence.
 */
public final class ChatAnnouncementTest {

    @Test
    public void anAnnouncementEndsWithAFullStop() {
        ChatComponentTranslation joined = new ChatComponentTranslation(
                "multiplayer.player.joined", "Player609");
        IChatComponent shown = LostTalesChatPresentation.asAnnouncement(
                joined, ChatSystemLineClassifier.kindOf(joined));
        assertTrue(shown.getUnformattedText().endsWith("."));
        // The server's own line is left as it came.
        assertFalse(joined.getUnformattedText().endsWith("."));

        ChatComponentTranslation earned = new ChatComponentTranslation(
                "chat.lotr.achievement", "Player609",
                new ChatComponentText("[First Steps]"));
        assertTrue(LostTalesChatPresentation.asAnnouncement(earned,
                ChatSystemLineClassifier.kindOf(earned)).getUnformattedText()
                .endsWith("."));
    }

    @Test
    public void aSentenceAlreadyEndedOrAnotherLineIsLeftAsItIs() {
        ChatComponentText ended = new ChatComponentText("Player609 left the game.");
        assertSame(ended, LostTalesChatPresentation.asAnnouncement(ended,
                ChatSystemLineClassifier.Kind.LEAVE));
        ChatComponentText said = new ChatComponentText("[Server] hello");
        assertSame(said, LostTalesChatPresentation.asAnnouncement(said,
                ChatSystemLineClassifier.Kind.OTHER));
        assertEquals("[Server] hello", said.getUnformattedText());
    }
}
