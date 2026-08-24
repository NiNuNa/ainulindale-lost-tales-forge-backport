package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Lays one chat message out to a given width, exactly as it would be
 * laid out on its way into the shared history: a Lost Tales line through
 * {@link ChatLineWrapper}, so its continuation lines indent under the
 * sender, and anything else the way {@code GuiNewChat} itself does it —
 * component by component, breaking at the last space that fits and
 * carrying the rest onto the next line.
 *
 * <p>Vanilla can afford to lay a message out once because it has one
 * width; a window with a width of its own asks here instead. Keeping the
 * two in step matters: the same message in two windows differs only in
 * where it breaks.</p>
 */
final class ChatMessageWrapper {

    private ChatMessageWrapper() {}

    /**
     * The message as lines, top line first, never empty. {@code colours}
     * is the game's chat-colours setting: with it off every formatting
     * code is measured and drawn as nothing, so the layout has to agree.
     * {@code chatOpen} lays the message out for the open chat screen,
     * which draws no channel prefix and so has that width to spare for
     * the body.
     */
    static List<IChatComponent> wrap(final FontRenderer font,
                                     IChatComponent root, int width,
                                     final boolean colours,
                                     boolean chatOpen) {
        List<IChatComponent> lines = null;
        if (font != null && root != null && width > 0) {
            lines = ChatLineWrapper.wrap(new ChatLineWrapper.TextMetrics() {
                @Override
                public int width(String text) {
                    return font.getStringWidth(colours ? text
                            : LostTalesChatVisualStyle.stripCodes(text));
                }
            }, root, width, chatOpen);
        }
        if (lines != null && !lines.isEmpty()) {
            return lines;
        }
        return vanillaWrap(font, root, width, colours);
    }

    /**
     * {@code GuiNewChat.func_146237_a}'s own layout, over the flattened
     * components of the message. Kept to vanilla's shape — including
     * that a piece which does not fit alone is cut hard — so a line that
     * is not a Lost Tales line breaks where the game would break it.
     */
    private static List<IChatComponent> vanillaWrap(FontRenderer font,
                                                    IChatComponent root,
                                                    int width,
                                                    boolean colours) {
        List<IChatComponent> lines = new ArrayList<IChatComponent>();
        if (font == null || root == null) {
            lines.add(root == null ? new ChatComponentText("") : root);
            return lines;
        }
        int room = Math.max(1, width);
        // A component is iterable over itself and its siblings, which is
        // the list vanilla walks; the pieces it splits off join it as it
        // goes, exactly as they do there.
        List<IChatComponent> parts = new ArrayList<IChatComponent>();
        for (Object part : root) {
            if (part instanceof IChatComponent) {
                parts.add((IChatComponent)part);
            }
        }
        if (parts.isEmpty()) {
            parts.add(root);
        }
        ChatComponentText line = new ChatComponentText("");
        int used = 0;
        for (int index = 0; index < parts.size(); index++) {
            IChatComponent part = parts.get(index);
            String text = colours ? part.getUnformattedTextForChat()
                    : LostTalesChatVisualStyle.stripCodes(
                            part.getUnformattedTextForChat());
            String measured = part.getChatStyle().getFormattingCode() + text;
            int partWidth = font.getStringWidth(
                    colours ? measured
                            : LostTalesChatVisualStyle.stripCodes(measured));
            ChatComponentText piece = new ChatComponentText(text);
            piece.setChatStyle(part.getChatStyle().createShallowCopy());
            boolean breaks = false;
            if (used + partWidth > room) {
                String head = font.trimStringToWidth(text, room - used, false);
                String tail = head.length() < text.length()
                        ? text.substring(head.length()) : null;
                if (tail != null && tail.length() > 0) {
                    int space = head.lastIndexOf(' ');
                    if (space >= 0 && font.getStringWidth(
                            text.substring(0, space)) > 0) {
                        head = text.substring(0, space);
                        tail = text.substring(space);
                    }
                    ChatComponentText rest = new ChatComponentText(tail);
                    rest.setChatStyle(part.getChatStyle().createShallowCopy());
                    parts.add(index + 1, rest);
                }
                partWidth = font.getStringWidth(head);
                piece = new ChatComponentText(head);
                piece.setChatStyle(part.getChatStyle().createShallowCopy());
                breaks = true;
            }
            if (used + partWidth <= room) {
                used += partWidth;
                line.appendSibling(piece);
            } else {
                breaks = true;
            }
            if (breaks) {
                lines.add(line);
                used = 0;
                line = new ChatComponentText("");
            }
        }
        lines.add(line);
        return lines;
    }
}
