package com.ninuna.losttales.util;

import net.minecraft.util.StatCollector;

/**
 * Words from the lang file: a key and its arguments in, the line out.
 * {@link #LANG} reads them in the language of the side that asks: the
 * game's own in each game, a world it hosts included, and on a dedicated
 * server the language the server writes in
 * ({@link LostTalesServerLanguage}), for what it writes to Discord. A
 * test passes the English lines instead, so the words live in the lang
 * file alone.
 */
public interface LostTalesWords {
    /** The lang file's words, in the language of the side that asks. */
    LostTalesWords LANG = new LostTalesWords() {
        @Override
        public String format(String key, Object... arguments) {
            return StatCollector.translateToLocalFormatted(key, arguments);
        }
    };

    /** The line under {@code key}, its {@code %s} filled from {@code arguments}. */
    String format(String key, Object... arguments);
}
