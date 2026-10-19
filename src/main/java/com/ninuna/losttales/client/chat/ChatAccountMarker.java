package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * Style marker on the {@code  (Steve)} run that follows a character's
 * name and title on a line's name row: the account behind the
 * character, faint, in the aside tone the time behind the name wears.
 * It takes no room and draws nothing while this player's Show Accounts
 * is off ({@link #isHidden}), which every walk over a line asks through
 * {@link ChatPrefixMarker#isHidden}, so a change of the setting lays the
 * lines out again without building them anew.
 */
final class ChatAccountMarker {
    private static final String PREFIX = "losttales-chat-account:";

    private ChatAccountMarker() {}

    /** Marks a component as the account run. */
    static ChatComponentText apply(ChatComponentText component) {
        if (component != null) {
            component.setChatStyle(component.getChatStyle()
                    .setChatClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND, PREFIX)));
        }
        return component;
    }

    /** Whether the component is the account run. */
    static boolean isMarker(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return false;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        return event != null && event.getAction()
                == ClickEvent.Action.SUGGEST_COMMAND
                && PREFIX.equals(event.getValue());
    }

    /** Whether the component is the account run while Show Accounts is off. */
    static boolean isHidden(IChatComponent component) {
        return !LostTalesConfig.chatShowAccounts && isMarker(component);
    }

    /** The colour the run is drawn in, or null when this is not one. */
    static Integer colorOf(IChatComponent component) {
        return isMarker(component)
                ? Integer.valueOf(LostTalesChatVisualStyle.asideRgb()) : null;
    }
}
