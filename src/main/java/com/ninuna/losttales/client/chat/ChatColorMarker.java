package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatColorMarkers;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Exact RGB metadata that survives vanilla's wrapped-chat component
 * copies. The value is {@link ChatColorMarkers}' so a mark the server
 * put on a component — a trader notice's name — reads the same as one
 * this client made.
 */
final class ChatColorMarker {

    private ChatColorMarker() {}

    static ChatComponentText apply(ChatComponentText component, int color) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND,
                            ChatColorMarkers.value(color))));
        }
        return component;
    }

    static Integer decode(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND) {
            return null;
        }
        return ChatColorMarkers.decode(event.getValue());
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }
}
