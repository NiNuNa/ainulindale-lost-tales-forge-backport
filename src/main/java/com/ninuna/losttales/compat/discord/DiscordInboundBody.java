package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.nio.charset.Charset;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * A Discord member's line as words each game reads in its own language:
 * its pieces as one chat component, plain text and the marks'
 * translations in order, written as the game's chat JSON. A client reads
 * it back as plain words and nothing else (the chat's
 * {@code ChatTranslatedWords.isWordsComponent}).
 */
public final class DiscordInboundBody {
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private DiscordInboundBody() {}

    /**
     * The line's component as chat JSON; empty for a line without a mark,
     * which travels as its words alone, and for one whose component would
     * not fit a chat line's body.
     */
    public static String jsonOf(DiscordInboundLine line) {
        if (line == null || !line.hasMarks()) {
            return "";
        }
        ChatComponentText root = new ChatComponentText("");
        for (DiscordInboundLine.Piece piece : line.getPieces()) {
            root.appendSibling(piece.isMark()
                    ? new ChatComponentTranslation(piece.key,
                            piece.arguments.toArray())
                    : new ChatComponentText(piece.words));
        }
        String json;
        try {
            json = IChatComponent.Serializer.func_150696_a(root);
        } catch (RuntimeException unwritable) {
            return "";
        }
        return json != null && json.getBytes(UTF_8).length
                <= LostTalesChatMessagePacket.MAX_BODY_BYTES ? json : "";
    }
}
