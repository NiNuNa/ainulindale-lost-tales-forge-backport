package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The wire layout of the roles a line calls, shared by the line and the
 * edit that changes its words: a count of at most
 * {@link ChatAccountRole#MAX_MENTIONS_PER_LINE}, then each role's id. An
 * id that is not one, or one named twice, does not decode.
 */
final class LostTalesChatRoleMentionCodec {
    /** The most a list takes on the wire. */
    static final int MAX_BYTES = 4 + ChatAccountRole.MAX_MENTIONS_PER_LINE
            * (2 + ChatAccountRole.MAX_ID_LENGTH);

    private LostTalesChatRoleMentionCodec() {}

    static void write(ByteBuf buffer, List<String> roleIds) {
        List<String> ids = roleIds == null ? Collections.<String>emptyList() : roleIds;
        LostTalesPacketCodec.writeCount(buffer, ids.size(),
                ChatAccountRole.MAX_MENTIONS_PER_LINE, "called roles");
        for (String id : ids) {
            LostTalesPacketCodec.writeUtf8String(buffer, id,
                    ChatAccountRole.MAX_ID_LENGTH);
        }
    }

    static List<String> read(ByteBuf buffer) {
        int count = LostTalesPacketCodec.readCount(buffer,
                ChatAccountRole.MAX_MENTIONS_PER_LINE, "called roles");
        List<String> ids = new ArrayList<String>(count);
        for (int index = 0; index < count; index++) {
            ids.add(LostTalesPacketCodec.readUtf8String(buffer,
                    ChatAccountRole.MAX_ID_LENGTH));
        }
        if (!isValid(ids)) {
            throw new LostTalesPacketCodec.DecodeException("invalid called roles");
        }
        return ids.isEmpty() ? Collections.<String>emptyList()
                : Collections.unmodifiableList(ids);
    }

    /** Whether the ids fit the layout: within the bound, each a role's id, none twice. */
    static boolean isValid(List<String> roleIds) {
        if (roleIds == null || roleIds.size() > ChatAccountRole.MAX_MENTIONS_PER_LINE) {
            return false;
        }
        for (int index = 0; index < roleIds.size(); index++) {
            String id = roleIds.get(index);
            if (!ChatAccountRole.isWellFormedId(id) || roleIds.indexOf(id) != index) {
                return false;
            }
        }
        return true;
    }
}
