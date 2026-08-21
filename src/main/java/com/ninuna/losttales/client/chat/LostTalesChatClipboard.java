package com.ninuna.losttales.client.chat;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;

/** Resolves a clicked wrapped line back to its full channel message. */
final class LostTalesChatClipboard {
    private LostTalesChatClipboard() {}

    static boolean copy(GuiNewChat chat, Minecraft minecraft,
                        int rawMouseX, int rawMouseY,
                        float entryDisplacement) {
        if (chat == null || minecraft == null || !chat.getChatOpen()) {
            return false;
        }
        try {
            List<ChatLine> lines =
                    LostTalesChatOverlayRenderer.getDrawnLines(chat);
            int clickedIndex = findClickedLine(
                    chat, minecraft, lines, rawMouseX, rawMouseY,
                    entryDisplacement);
            if (clickedIndex < 0) {
                return false;
            }
            String text = resolveChannelMessage(lines, clickedIndex);
            if (text.length() == 0) {
                text = plainText(lines.get(clickedIndex).func_151461_a())
                        .trim();
            }
            if (text.length() == 0) {
                return false;
            }
            GuiScreen.setClipboardString(text);
            return true;
        } catch (IllegalAccessException ignored) {
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static int findClickedLine(
            GuiNewChat chat, Minecraft minecraft, List<ChatLine> lines,
            int rawMouseX, int rawMouseY, float entryDisplacement)
            throws IllegalAccessException {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        ScaledResolution resolution = new ScaledResolution(
                minecraft, minecraft.displayWidth,
                minecraft.displayHeight);
        int scaleFactor = resolution.getScaleFactor();
        float chatScale = chat.func_146244_h();
        int visibleLines = Math.min(
                LostTalesChatOverlayRenderer.visibleLineCount(chat),
                lines.size());
        int width = MathHelper.floor_float(
                chat.func_146228_f() / chatScale);
        int scrollPosition =
                LostTalesChatOverlayRenderer.getScrollPosition(chat);
        return lineIndexAt(rawMouseX, rawMouseY, scaleFactor, chatScale,
                width, visibleLines, scrollPosition, scrollPosition == 0
                        ? entryDisplacement : 0.0F, lines.size());
    }

    /**
     * Vanilla 1.7.10 hit testing adjusted to the renderer's contiguous
     * line stride, with the visual entry offset removed.
     */
    static int lineIndexAt(int rawMouseX, int rawMouseY, int scaleFactor,
                           float chatScale, int width, int visibleLines,
                           int scrollPosition,
                           float entryDisplacement, int totalLines) {
        if (scaleFactor <= 0 || chatScale <= 0.0F || visibleLines <= 0
                || totalLines <= 0) {
            return -1;
        }
        int lineHeight = LostTalesChatOverlayRenderer.LINE_HEIGHT;
        int x = MathHelper.floor_float(
                (rawMouseX / scaleFactor - 3) / chatScale);
        int y = MathHelper.floor_float(
                (rawMouseY / scaleFactor - 27 + entryDisplacement)
                        / chatScale);
        if (x < 0 || y < 0 || x > width
                || y >= lineHeight * visibleLines) {
            return -1;
        }
        int lineIndex = y / lineHeight + scrollPosition;
        return lineIndex >= 0 && lineIndex < totalLines
                ? lineIndex : -1;
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

    private static ChatHeadMarker.Data findMarker(
            net.minecraft.util.IChatComponent line) {
        for (Object value : line) {
            ChatHeadMarker.Data marker = ChatHeadMarker.decode(
                    (IChatComponent)value);
            if (marker != null) {
                return marker;
            }
        }
        return null;
    }

    private static String plainText(
            net.minecraft.util.IChatComponent line) {
        StringBuilder text = new StringBuilder();
        for (Object value : line) {
            text.append(((IChatComponent)value)
                    .getUnformattedTextForChat());
        }
        return text.toString();
    }

}
