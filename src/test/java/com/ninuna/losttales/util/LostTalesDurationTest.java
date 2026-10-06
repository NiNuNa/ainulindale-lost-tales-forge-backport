package com.ninuna.losttales.util;

import com.ninuna.losttales.chat.moderation.ChatMuteDurations;
import com.ninuna.losttales.quest.LostTalesQuestTimeText;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A length of time is written by the reader's lang lines: each unit and
 * the join between parts. The quest timer and the mute notice share them,
 * and what the server sends a player is a translation each game words.
 */
public final class LostTalesDurationTest {

    @Test
    public void englishWritesTheShortForm() {
        assertEquals("2d 5h", LostTalesDuration.of(2L, LostTalesDuration.Unit.DAYS)
                .and(5L, LostTalesDuration.Unit.HOURS).write(EnglishWords.INSTANCE));
        assertEquals("1h 2m 3s", LostTalesDuration.of(1L, LostTalesDuration.Unit.HOURS)
                .and(2L, LostTalesDuration.Unit.MINUTES)
                .and(3L, LostTalesDuration.Unit.SECONDS).write(EnglishWords.INSTANCE));
        assertEquals("0m", LostTalesQuestTimeText.duration(0L)
                .write(EnglishWords.INSTANCE));
    }

    /** Another language's units and join come from its own lines. */
    @Test
    public void anotherLanguageWordsTheUnitsItsOwnWay() {
        LostTalesWords german = words(
                LostTalesDuration.Unit.DAYS.getKey(), "%s T.",
                LostTalesDuration.Unit.HOURS.getKey(), "%s Std.",
                LostTalesDuration.Unit.MINUTES.getKey(), "%s Min.",
                LostTalesDuration.Unit.SECONDS.getKey(), "%s Sek.",
                LostTalesDuration.JOIN_KEY, "%s, %s");
        assertEquals("1 T., 3 Std.", LostTalesQuestTimeText.duration(27000L)
                .write(german));
        assertEquals("45 Min., 30 Sek.", ChatMuteDurations.remaining(
                (45L * 60L + 30L) * 1000L).write(german));
    }

    /** The server sends the duration as translations, which a game in English reads as English. */
    @Test
    public void theServerSendsTranslations() {
        IChatComponent sent = ChatMuteDurations.remaining(
                (3L * 3600L + 12L * 60L) * 1000L).component();
        assertTrue(sent instanceof ChatComponentTranslation);
        assertEquals(LostTalesDuration.JOIN_KEY,
                ((ChatComponentTranslation)sent).getKey());
        assertEquals("3h 12m", EnglishWords.INSTANCE.read(sent));
        assertEquals("20s", EnglishWords.INSTANCE.read(
                ChatMuteDurations.remaining(20000L).component()));
    }

    @Test
    public void everyUnitHasItsEnglishLine() {
        for (LostTalesDuration.Unit unit : LostTalesDuration.Unit.values()) {
            assertTrue(unit.getKey(), EnglishWords.INSTANCE.has(unit.getKey()));
        }
        assertTrue(EnglishWords.INSTANCE.has(LostTalesDuration.JOIN_KEY));
    }

    private static LostTalesWords words(String... keysAndLines) {
        final Map<String, String> lines = new HashMap<String, String>();
        for (int index = 0; index < keysAndLines.length; index += 2) {
            lines.put(keysAndLines[index], keysAndLines[index + 1]);
        }
        return new LostTalesWords() {
            @Override
            public String format(String key, Object... arguments) {
                String line = lines.get(key);
                if (line == null) {
                    throw new AssertionError("no line for " + key);
                }
                return String.format(line, arguments);
            }
        };
    }
}
