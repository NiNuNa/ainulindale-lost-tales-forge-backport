package com.ninuna.losttales.client.chat;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.IChatComponent;

/** Resolves a clicked wrapped line back to its full channel message. */
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
            text = plainText(lines.get(index).func_151461_a()).trim();
        }
        return text;
    }

    private static String resolveChannelMessage(List<ChatLine> lines,
                                                int clickedIndex) {
        int counter = lines.get(clickedIndex).getUpdatedCounter();
        int chatLineId = lines.get(clickedIndex).getChatLineID();
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

    private static String plainText(IChatComponent line) {
        StringBuilder text = new StringBuilder();
        for (Object value : line) {
            text.append(((IChatComponent)value)
                    .getUnformattedTextForChat());
        }
        return text.toString();
    }
}
