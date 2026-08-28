package com.ninuna.losttales.client.chat;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Zero-text marker opening a grouped line: it reserves the slot the
 * grouping junction is drawn in — the little elbow or tee that ties a
 * continuation message to the run it belongs to. Every walk over the
 * line advances by the declared slot (see
 * {@link ChatInlineIcons#declaredWidth}), and the renderer draws the
 * junction from its line loop, where it can see whether the run
 * continues below and pick the connecting shape. Same carrier mechanism
 * as every other marker: the click event survives vanilla's wrapped-chat
 * component copies.
 */
final class ChatGroupMarker {
    private static final String PREFIX = "losttales-chat-group";
    /**
     * The junction slot: exactly the header's opening bracket plus its
     * head slot (6 + 11), so a grouped line's body starts where the
     * header's name starts, and the junction's stem stands centred
     * under the head above.
     */
    static final int SLOT_WIDTH = 17;

    private ChatGroupMarker() {}

    static ChatComponentText create() {
        ChatComponentText marker = new ChatComponentText("");
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, PREFIX)));
        return marker;
    }

    static boolean isMarker(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return false;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        return event != null
                && event.getAction() == ClickEvent.Action.SUGGEST_COMMAND
                && PREFIX.equals(event.getValue());
    }

    /**
     * Whether the line opens a grouped message. Only the message's
     * first visual line carries the marker — the wrapper keeps header
     * parts on the line they open — so this is also the line the
     * junction is drawn on.
     */
    static boolean isGroupedLine(IChatComponent line) {
        if (line == null) {
            return false;
        }
        for (Object value : line) {
            if (value instanceof IChatComponent
                    && isMarker((IChatComponent)value)) {
                return true;
            }
        }
        return false;
    }
}
