package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IChatComponent;

/**
 * Resolves a clicked wrapped line back to the message it belongs to.
 *
 * <p>A message is copied as it was <em>said</em>, not as it is drawn:
 * the words alone, without the timestamp, the channel prefix, the
 * sender or the chevron the chat opens a body with. The client already
 * keeps the packet each line was built from, so that is what is read
 * first; a line with no packet behind it — an NPC's speech, a stray
 * line adopted from vanilla's own chat — falls back to the text its
 * sender marker carries, and then to the drawn rows themselves.</p>
 */
final class LostTalesChatClipboard {
    private LostTalesChatClipboard() {}

    /** {@code mouseX}/{@code mouseY} are GUI coordinates, as the screen sees them. */
    static boolean copy(GuiNewChat chat, Minecraft minecraft,
                        int mouseX, int mouseY) {
        String text = messageTextAt(chat, minecraft, mouseX, mouseY);
        if (text.length() == 0) {
            return false;
        }
        GuiScreen.setClipboardString(text);
        return true;
    }

    /** Copies text already resolved from a line; empty copies nothing. */
    static boolean copy(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        GuiScreen.setClipboardString(text);
        return true;
    }

    /**
     * The whole message body under the pointer, empty when there is
     * none. Resolved once so a menu opened over a line can act on the
     * message later, when the pointer has moved on.
     */
    static String messageTextAt(GuiNewChat chat, Minecraft minecraft,
                                int mouseX, int mouseY) {
        if (chat == null || minecraft == null || !chat.getChatOpen()) {
            return "";
        }
        try {
            // A whole-pixel press samples the pixel's centre, the best
            // estimate of where inside it the pointer actually was.
            LostTalesChatOverlayRenderer.Band band =
                    LostTalesChatOverlayRenderer.bandAt(
                            minecraft, mouseX + 0.5F, mouseY + 0.5F);
            List<ChatLine> lines = band == null ? null : band.lines;
            if (band == null || lines == null
                    || band.viewIndex >= lines.size()
                    || lines.get(band.viewIndex) == null) {
                return "";
            }
            return messageTextOf(lines, band.viewIndex);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    /**
     * The whole body of the message one of whose lines is at
     * {@code index}, empty when it has none: what a control acting on a
     * message already known copies, rather than hit-testing for it again.
     */
    static String messageTextOf(List<ChatLine> lines, int index) {
        if (lines == null || index < 0 || index >= lines.size()
                || lines.get(index) == null) {
            return "";
        }
        String text = resolveChannelMessage(lines, index);
        if (text.length() == 0) {
            text = wordsOfMessage(lines, index);
        }
        return text;
    }

    private static String resolveChannelMessage(List<ChatLine> lines,
                                                int clickedIndex) {
        int counter = lines.get(clickedIndex).getUpdatedCounter();
        int chatLineId = lines.get(clickedIndex).getChatLineID();
        String remembered = rememberedMessage(chatLineId);
        if (remembered.length() > 0) {
            return remembered;
        }
        ChatHeadMarker.Data direct = findMarker(
                lines.get(clickedIndex).func_151461_a());
        if (direct != null && direct.copyText.length() > 0) {
            return direct.copyText;
        }
        for (int distance = 1; distance < lines.size(); distance++) {
            int index = clickedIndex - distance;
            if (index >= 0) {
                ChatLine line = lines.get(index);
                if (sameMessage(line, chatLineId, counter)) {
                    ChatHeadMarker.Data marker = findMarker(
                            line.func_151461_a());
                    if (marker != null && marker.copyText.length() > 0) {
                        return marker.copyText;
                    }
                }
            }
            index = clickedIndex + distance;
            if (index >= lines.size()) {
                continue;
            }
            ChatLine line = lines.get(index);
            if (sameMessage(line, chatLineId, counter)) {
                ChatHeadMarker.Data marker = findMarker(
                        line.func_151461_a());
                if (marker != null && marker.copyText.length() > 0) {
                    return marker.copyText;
                }
            }
        }
        return "";
    }

    /**
     * The message the packet behind this line was sent with, empty when
     * the client keeps no packet for it. This is the message itself
     * rather than a reading of what was drawn, so a grouped
     * continuation — which carries no sender marker of its own — copies
     * exactly what an ungrouped one does, emoji shortcodes and all.
     */
    private static String rememberedMessage(int chatLineId) {
        ClientChatMessages.Remembered remembered =
                ClientChatMessages.get(
                        ClientChatMessageIds.messageIdOf(chatLineId));
        return remembered == null ? "" : remembered.packet.getMessage();
    }

    private static boolean sameMessage(ChatLine line, int chatLineId,
                                       int updateCounter) {
        return line != null && (chatLineId != 0
                ? line.getChatLineID() == chatLineId
                : line.getUpdatedCounter() == updateCounter);
    }

    private static ChatHeadMarker.Data findMarker(IChatComponent line) {
        for (Object value : line) {
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(
                    (IChatComponent)value);
            if (marker != null) {
                return marker;
            }
        }
        return null;
    }

    /**
     * The words of every row of the message at {@code index}, top row
     * first: what a message with no head marker to answer for it — a
     * grouped continuation — is read back as. A message's rows are
     * contiguous in the list and its own last row comes first there, so
     * the run is walked from its far end back to this one.
     */
    private static String wordsOfMessage(List<ChatLine> lines, int index) {
        int counter = lines.get(index).getUpdatedCounter();
        int chatLineId = lines.get(index).getChatLineID();
        int top = index;
        while (top + 1 < lines.size()
                && sameMessage(lines.get(top + 1), chatLineId, counter)) {
            top++;
        }
        StringBuilder text = new StringBuilder();
        for (int row = top; row >= 0; row--) {
            if (!sameMessage(lines.get(row), chatLineId, counter)) {
                break;
            }
            String words = words(lines.get(row).func_151461_a()).trim();
            if (words.length() == 0) {
                continue;
            }
            if (text.length() > 0) {
                // The space a line break dropped, put back.
                text.append(' ');
            }
            text.append(words);
        }
        return text.toString();
    }

    /**
     * A row's own words: everything the chat itself puts around a
     * message — the channel prefix, the timestamp, the chevron a body
     * opens with — is left out, and an emoji is read back as the
     * shortcode it was typed as rather than as the blank slot its
     * sprite is drawn into.
     */
    private static String words(IChatComponent line) {
        StringBuilder text = new StringBuilder();
        for (Object value : line) {
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isMarker(part)
                    || ChatBodyMarker.isMarker(part)) {
                continue;
            }
            ChatEmoji emoji = ChatEmojiMarker.decode(part);
            if (emoji != null) {
                if (ChatEmojiMarker.reservesFullSlot(
                        part.getUnformattedTextForChat())) {
                    text.append(':').append(emoji.getName()).append(':');
                }
                continue;
            }
            text.append(part.getUnformattedTextForChat());
        }
        return text.toString();
    }
}
