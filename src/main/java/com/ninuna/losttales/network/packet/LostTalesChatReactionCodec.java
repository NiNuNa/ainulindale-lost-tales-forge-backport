package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatReactionSummary;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;

/**
 * The wire layout of a {@link ChatReactionSummary}, shared by the line
 * it rides on and the update that replaces it: a count of emoji, then
 * for each its reaction key (a registry name or a foreign key, at most
 * {@link ChatReactionSummary#MAX_EMOJI_BYTES}), how many reacted,
 * whether the reader is one of them, and the names shown. Every part is
 * bounded; a summary that breaks a bound does not decode.
 */
final class LostTalesChatReactionCodec {
    /** The most a summary takes on the wire. */
    static final int MAX_BYTES = 4 + ChatReactionSummary.MAX_KINDS
            * (2 + ChatReactionSummary.MAX_EMOJI_BYTES + 4 + 1 + 4
                    + ChatReactionSummary.MAX_NAMES
                            * (2 + ChatReactionSummary.MAX_NAME_BYTES));

    private LostTalesChatReactionCodec() {}

    static void write(ByteBuf buffer, ChatReactionSummary summary) {
        List<ChatReactionSummary.Reaction> reactions = summary == null
                ? new ArrayList<ChatReactionSummary.Reaction>()
                : summary.getReactions();
        LostTalesPacketCodec.writeCount(buffer, reactions.size(),
                ChatReactionSummary.MAX_KINDS, "reactions");
        for (ChatReactionSummary.Reaction reaction : reactions) {
            LostTalesPacketCodec.writeUtf8String(buffer, reaction.emoji,
                    ChatReactionSummary.MAX_EMOJI_BYTES);
            buffer.writeInt(reaction.count);
            buffer.writeBoolean(reaction.mine);
            LostTalesPacketCodec.writeCount(buffer, reaction.names.size(),
                    ChatReactionSummary.MAX_NAMES, "reaction names");
            for (String name : reaction.names) {
                LostTalesPacketCodec.writeUtf8String(buffer, name,
                        ChatReactionSummary.MAX_NAME_BYTES);
            }
        }
    }

    /** Throws on anything out of bounds; the caller's decoder marks the payload malformed. */
    static ChatReactionSummary read(ByteBuf buffer) {
        int kinds = LostTalesPacketCodec.readCount(buffer,
                ChatReactionSummary.MAX_KINDS, "reactions");
        if (kinds == 0) {
            return ChatReactionSummary.EMPTY;
        }
        List<ChatReactionSummary.Reaction> reactions =
                new ArrayList<ChatReactionSummary.Reaction>(kinds);
        for (int index = 0; index < kinds; index++) {
            String emoji = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatReactionSummary.MAX_EMOJI_BYTES);
            int count = buffer.readInt();
            boolean mine = buffer.readBoolean();
            int named = LostTalesPacketCodec.readCount(buffer,
                    ChatReactionSummary.MAX_NAMES, "reaction names");
            List<String> names = new ArrayList<String>(named);
            for (int name = 0; name < named; name++) {
                names.add(LostTalesPacketCodec.readUtf8String(buffer,
                        ChatReactionSummary.MAX_NAME_BYTES));
            }
            reactions.add(new ChatReactionSummary.Reaction(emoji, count,
                    mine, names));
        }
        for (int first = 0; first < reactions.size(); first++) {
            for (int second = first + 1; second < reactions.size(); second++) {
                if (reactions.get(first).emoji.equals(reactions.get(second).emoji)) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "an emoji reacted with twice");
                }
            }
        }
        return new ChatReactionSummary(reactions);
    }
}
