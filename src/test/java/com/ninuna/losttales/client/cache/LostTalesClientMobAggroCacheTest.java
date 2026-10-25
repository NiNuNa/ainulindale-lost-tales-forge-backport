package com.ninuna.losttales.client.cache;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Each client names a combat contact in its own language from the kind the
 * server sent (MAP-16): a name a player gave stands as written, a kind this
 * game knows reads its own line, and anything else is an enemy.
 */
public final class LostTalesClientMobAggroCacheTest {

    /** A game in German, knowing the lines it holds and no others. */
    private static final class German implements LostTalesClientMobAggroCache.Words {
        private final Map<String, String> lines = new HashMap<String, String>();

        German() {
            this.lines.put("entity.lotr.MordorOrc.name", "Mordor-Ork");
            this.lines.put("entity.Wolf.name", "Wolf");
            this.lines.put(LostTalesClientMobAggroCache.ENEMY_KEY, "Feind");
        }

        @Override
        public boolean has(String key) {
            return this.lines.containsKey(key);
        }

        @Override
        public String read(String key) {
            String line = this.lines.get(key);
            return line == null ? key : line;
        }
    }

    @Test
    public void aKnownKindReadsInTheClientsLanguage() {
        assertEquals("Mordor-Ork",
                LostTalesClientMobAggroCache.nameOf("lotr.MordorOrc", "", new German()));
        assertEquals("entity.lotr.MordorOrc.name",
                LostTalesClientMobAggroCache.kindKey("lotr.MordorOrc"));
    }

    @Test
    public void aNamePlayerGaveStandsAsWritten() {
        assertEquals("Grip", LostTalesClientMobAggroCache.nameOf("Wolf", " Grip ", new German()));
    }

    @Test
    public void anUnknownOrMissingKindIsAnEnemy() {
        assertEquals("Feind",
                LostTalesClientMobAggroCache.nameOf("othermod.Wyvern", "", new German()));
        assertEquals("Feind", LostTalesClientMobAggroCache.nameOf("", null, new German()));
        assertEquals("Feind", LostTalesClientMobAggroCache.nameOf(null, "  ", new German()));
    }
}
