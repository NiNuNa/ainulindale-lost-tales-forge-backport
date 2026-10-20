package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipMark;

/**
 * Whether a fellowship's or a mark's name may be given: asked by the
 * server before it keeps one, and by the naming window before Keep, so
 * the window greys what the server would refuse.
 */
public final class FellowshipNames {
    private FellowshipNames() {}

    /**
     * Why a trimmed name may not be a fellowship's; {@link FellowshipErrorId#NONE}
     * for one that may: not empty, at most {@link Fellowship#MAX_NAME_LENGTH}
     * characters, no formatting codes or control characters, and no word of
     * the profanity list.
     */
    public static FellowshipErrorId nameError(String name, ChatProfanityWords words) {
        if (name == null || name.length() == 0) {
            return FellowshipErrorId.NAME_MISSING;
        }
        if (name.codePointCount(0, name.length()) > Fellowship.MAX_NAME_LENGTH) {
            return FellowshipErrorId.NAME_TOO_LONG;
        }
        if (!Fellowship.isWellFormedName(name)
                || ChatProfanityFilter.hasListedWord(name, words)) {
            return FellowshipErrorId.NAME_NOT_ALLOWED;
        }
        return FellowshipErrorId.NONE;
    }

    /**
     * Why a trimmed name may not be a mark's; {@link FellowshipErrorId#NONE}
     * for one that may: as a fellowship's name, with no {@code @} or square
     * brackets besides.
     */
    public static FellowshipErrorId markNameError(String name, ChatProfanityWords words) {
        if (name == null || name.length() == 0) {
            return FellowshipErrorId.NAME_MISSING;
        }
        if (name.codePointCount(0, name.length()) > FellowshipMark.MAX_NAME_LENGTH) {
            return FellowshipErrorId.NAME_TOO_LONG;
        }
        if (!FellowshipMark.isValidName(name)
                || ChatProfanityFilter.hasListedWord(name, words)) {
            return FellowshipErrorId.NAME_NOT_ALLOWED;
        }
        return FellowshipErrorId.NONE;
    }
}
