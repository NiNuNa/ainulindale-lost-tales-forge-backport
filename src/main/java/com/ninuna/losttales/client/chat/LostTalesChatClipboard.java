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
        if (chat == null || minecraft == null || !chat.getChatOpen()) {
            return false;
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
                return false;
            }
            String text = resolveChannelMessage(lines, band.viewIndex);
            if (text.length() == 0) {
                text = plainText(lines.get(band.viewIndex).func_151461_a())
                        .trim();
            }
            if (text.length() == 0) {
                return false;
            }
            GuiScreen.setClipboardString(text);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
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
