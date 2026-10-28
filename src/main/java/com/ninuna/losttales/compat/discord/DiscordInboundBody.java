package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * A Discord member's line as words each game reads in its own language:
 * its pieces as one chat component, plain text and the marks'
 * translations in order, written as the game's chat JSON. A client reads
 * it back as plain words and nothing else (the chat's
 * {@code ChatTranslatedWords.isWordsComponent}), and the bridge reads it
 * back into pieces ({@link #lineOf}) to word an edit carried on to
 * another linked Discord channel.
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

    /**
     * The line {@link #jsonOf} wrote, back in pieces: plain words and
     * marks, each mark's text in English. Null for anything of another
     * shape, and for JSON longer than a chat line's body.
     */
    public static DiscordInboundLine lineOf(String json) {
        if (json == null || json.length() == 0
                || json.length() > LostTalesChatMessagePacket.MAX_BODY_BYTES) {
            return null;
        }
        IChatComponent root;
        try {
            root = IChatComponent.Serializer.func_150699_a(json);
        } catch (RuntimeException unreadable) {
            return null;
        }
        if (!(root instanceof ChatComponentText)
                || root.getUnformattedTextForChat().length() > 0) {
            return null;
        }
        List<DiscordInboundLine.Piece> pieces = new ArrayList<DiscordInboundLine.Piece>();
        for (Object sibling : root.getSiblings()) {
            if (sibling instanceof ChatComponentTranslation) {
                ChatComponentTranslation mark = (ChatComponentTranslation)sibling;
                Object[] arguments = mark.getFormatArgs();
                String[] said = new String[arguments == null ? 0 : arguments.length];
                for (int index = 0; index < said.length; index++) {
                    said[index] = arguments[index] instanceof IChatComponent
                            ? ((IChatComponent)arguments[index]).getUnformattedText()
                            : String.valueOf(arguments[index]);
                }
                pieces.add(DiscordInboundLine.Piece.mark(LostTalesLangFile.ENGLISH_WORDS,
                        mark.getKey(), said));
            } else if (sibling instanceof ChatComponentText) {
                pieces.add(DiscordInboundLine.Piece.plain(
                        ((ChatComponentText)sibling).getUnformattedTextForChat()));
            } else {
                return null;
            }
        }
        return new DiscordInboundLine(pieces);
    }
}
