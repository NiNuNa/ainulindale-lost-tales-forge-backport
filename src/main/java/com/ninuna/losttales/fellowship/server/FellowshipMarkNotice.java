package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.chat.ChatTranslatedWords;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipNames;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;

import java.util.Collections;
import java.util.List;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * The Server's quiet line in a fellowship's conversation when its leader or
 * a guide places, moves or removes a mark: <i>Aldric marked Weathertop.</i>,
 * in each member's own language. The mark's name opens the map there. The line names nobody, so it pings
 * nobody; it only waits unread.
 */
public final class FellowshipMarkNotice {

    private FellowshipMarkNotice() {}

    /**
     * Says what was done to a mark in its fellowship's conversation, as
     * words each member's game translates ({@link ChatTranslatedWords});
     * the server's own words stand for it where it is quoted or logged.
     */
    public static void tell(FellowshipOperationType operation, Fellowship fellowship,
                            FellowshipMember actor, FellowshipMark mark) {
        if (fellowship == null || actor == null || mark == null) {
            return;
        }
        String key = keyOf(operation);
        if (key == null) {
            return;
        }
        Object[] arguments = argumentsOf(operation, actor.getCharacterName(),
                mark.getName());
        Object[] spoken = arguments.clone();
        spoken[0] = FellowshipNames.shown(actor.getCharacterName());
        String words = StatCollector.translateToLocalFormatted(key, spoken);
        arguments[0] = FellowshipNames.component(actor.getCharacterName());
        LostTalesChatService.sayToFellowship(fellowship, words,
                componentJson(new ChatComponentTranslation(key, arguments)),
                operation == FellowshipOperationType.REMOVE_MARK
                        ? Collections.<ChatShowcase>emptyList()
                        : link(words, mark, actor.getColor().getTint()));
    }

    /** The line's lang key for what was done to a mark; null for any other operation. */
    static String keyOf(FellowshipOperationType operation) {
        if (operation == FellowshipOperationType.PLACE_MARK) {
            return ChatTranslatedWords.PREFIX + "mark_placed";
        }
        if (operation == FellowshipOperationType.MOVE_MARK) {
            return ChatTranslatedWords.PREFIX + "mark_moved";
        }
        if (operation == FellowshipOperationType.REMOVE_MARK) {
            return ChatTranslatedWords.PREFIX + "mark_removed";
        }
        return null;
    }

    /**
     * The line's arguments: who did it, and the mark's name, as a map
     * link while the mark stands and plain once it is removed.
     */
    static Object[] argumentsOf(FellowshipOperationType operation,
                                String actor, String markName) {
        return new Object[] {actor,
                operation == FellowshipOperationType.REMOVE_MARK ? markName
                        : ChatShareTokenParser.buildToken(ChatShareKind.MARKER,
                                markName, 1)};
    }

    private static String componentJson(IChatComponent component) {
        try {
            String json = IChatComponent.Serializer.func_150696_a(component);
            return json == null ? "" : json;
        } catch (RuntimeException unwritable) {
            return "";
        }
    }

    /**
     * The link under the line's one map token: the mark's place, in the
     * colour its placer wears. Each reader's client shows it in the colour
     * they wear themselves, as their map does. None when the words hold no
     * single map token.
     */
    static List<ChatShowcase> link(String text, FellowshipMark mark, String tint) {
        List<ChatShareTokenParser.Token> tokens = ChatShareTokenParser.parse(text);
        if (tokens.size() != 1 || tokens.get(0).kind != ChatShareKind.MARKER) {
            return Collections.emptyList();
        }
        return Collections.singletonList(ChatShowcase.marker(0,
                mark.getMarkerId(), mark.getName(),
                LostTalesCompassMarkerIcon.CAMP.name(), tint,
                mark.getDimensionId(), mark.getX(), mark.getZ()));
    }
}
