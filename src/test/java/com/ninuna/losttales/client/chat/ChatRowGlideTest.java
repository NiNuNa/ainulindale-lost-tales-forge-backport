package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.motion.MotionIds;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * A window laid out again for the same messages moves each row from
 * where it was drawn to its new place instead of jumping there, and a
 * row the new layout adds fades in where it lands; anything else — a
 * message arriving, animations off, a scrolled view — sets the rows
 * down at once. A layout arriving while rows travel sets them off from
 * where they are drawn, so a window dragged through many widths never
 * makes a row jump.
 */
public final class ChatRowGlideTest {
    private static final int LINE = ChatStackRows.LINE_HEIGHT;
    private static final long START = 1000000000L;
    private static final long DURATION =
            Motions.travelNanos(MotionIds.CHAT_ROW_MOVE);
    private static final int OLDER = 11;
    private static final int NEWER = 12;

    @After
    public void restoreAnimations() {
        LostTalesConfig.animations = true;
    }

    @Test
    public void aMessageWrappingOntoAnotherRowSlidesTheRowsAboveItUp() {
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(NEWER, "a b c d"), row(OLDER, "hello"));
        // Narrower: the newer message takes two rows, its first row one
        // above where it was and the older message one above that.
        lay(glide, START, row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello"));

        glide.advance(START, 0.0F);
        assertEquals("the new row lands in place", 0.0F, glide.lift(0), 0.0F);
        assertEquals("and fades in from nothing", 0.0F, glide.shown(0), 0.0F);
        assertEquals("the first row sets off from where it stood",
                -LINE, glide.lift(1), 0.001F);
        assertEquals(1.0F, glide.shown(1), 0.0F);
        assertEquals("the message above goes with it",
                -LINE, glide.lift(2), 0.001F);
        assertEquals(LINE, glide.deepestDrop(), 0.001F);

        glide.advance(START + DURATION / 2, 0.0F);
        assertTrue("half way it is on its way",
                glide.lift(1) > -LINE && glide.lift(1) != 0.0F);
        assertTrue(glide.shown(0) > 0.0F && glide.shown(0) < 1.0F);

        glide.advance(START + DURATION, 0.0F);
        assertEquals(0.0F, glide.lift(1), 0.0F);
        assertEquals(0.0F, glide.lift(2), 0.0F);
        assertEquals(1.0F, glide.shown(0), 0.0F);
        glide.advance(START + DURATION + 1L, 0.0F);
        assertEquals("a finished trip is let go", 0.0F, glide.deepestDrop(),
                0.0F);
    }

    @Test
    public void aRowGlidesPastItsPlaceByAHairAndBack() {
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(NEWER, "a b c d"), row(OLDER, "hello"));
        lay(glide, START, row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello"));
        float farthest = 0.0F;
        for (int step = 1; step < 100; step++) {
            glide.advance(START + DURATION * step / 100, 0.0F);
            farthest = Math.max(farthest, glide.lift(2));
        }
        assertTrue("it overshoots upward", farthest > 0.0F);
        assertTrue("by a hair", farthest < LINE * 0.1F);
    }

    @Test
    public void aLayoutArrivingOnTheWaySetsTheRowOffFromWhereItIsDrawn() {
        ChatRowGlide glide = new ChatRowGlide();
        ChatLine[] wide = { row(NEWER, "a b c d"), row(OLDER, "hello") };
        ChatLine[] narrow = { row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello") };
        lay(glide, START, wide);
        ChatStackRows narrowRows = lay(glide, START, narrow);
        long midway = START + DURATION / 3;
        glide.advance(midway, 0.0F);
        float drawn = narrowRows.top(2) + glide.lift(2);

        // Wider again before it arrived: the older message sets off back
        // down from exactly where it is drawn.
        ChatStackRows wideRows = lay(glide, midway,
                row(NEWER, "a b c d"), row(OLDER, "hello"));
        glide.advance(midway, 0.0F);
        assertEquals(drawn, wideRows.top(1) + glide.lift(1), 0.001F);
        assertNotEquals(0.0F, glide.lift(1), 0.0F);
    }

    @Test
    public void aMessageArrivingIsACut() {
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(OLDER, "hello"));
        lay(glide, START, row(NEWER, "hi"), row(OLDER, "hello"));
        glide.advance(START, 0.0F);
        assertEquals(0.0F, glide.lift(1), 0.0F);
        assertEquals(1.0F, glide.shown(0), 0.0F);
    }

    @Test
    public void nothingGlidesWithAnimationsOff() {
        LostTalesConfig.animations = false;
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(NEWER, "a b c d"), row(OLDER, "hello"));
        lay(glide, START, row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello"));
        glide.advance(START, 0.0F);
        assertEquals(0.0F, glide.lift(2), 0.0F);
        assertEquals(1.0F, glide.shown(0), 0.0F);
    }

    @Test
    public void haltingSetsEveryRowDown() {
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(NEWER, "a b c d"), row(OLDER, "hello"));
        lay(glide, START, row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello"));
        glide.halt();
        glide.advance(START, 0.0F);
        assertEquals(0.0F, glide.lift(2), 0.0F);
        assertEquals(1.0F, glide.shown(0), 0.0F);
    }

    @Test
    public void aLiftLandsOnTheDisplaysGrid() {
        ChatRowGlide glide = new ChatRowGlide();
        lay(glide, START, row(NEWER, "a b c d"), row(OLDER, "hello"));
        lay(glide, START, row(NEWER, "c d"), row(NEWER, "a b"),
                row(OLDER, "hello"));
        for (int step = 0; step <= 20; step++) {
            glide.advance(START + DURATION * step / 20, 3.0F);
            float displayPixels = glide.lift(2) * 3.0F;
            assertEquals(Math.round(displayPixels), displayPixels, 0.0001F);
        }
    }

    @Test
    public void eachMessageIsKnownByItsIdAndTheSameIdsAboveIt() {
        // Two lines another mod printed share id zero; a blank row
        // between runs parts them.
        List<ChatLine> lines = Arrays.asList(row(0, "second"),
                new ChatLine(0, ChatWindowLines.SPACER, 0), row(0, "first"),
                row(OLDER, "b"), row(OLDER, "a"));
        long[] messages = ChatRowGlide.keysOf(lines,
                new ChatRowGlide.Key[lines.size()]);
        assertEquals(3, messages.length);
        assertEquals((long)OLDER << 32, messages[0]);
        assertEquals(0L, messages[1]);
        assertEquals(1L, messages[2]);
    }

    private static ChatStackRows lay(ChatRowGlide glide, long now,
                                     ChatLine... newestFirst) {
        List<ChatLine> lines = Arrays.asList(newestFirst);
        ChatStackRows rows = new ChatStackRows();
        rows.reset(lines, -1, true);
        glide.relaid(lines, rows, -1, now);
        return rows;
    }

    private static ChatLine row(int chatLineId, String words) {
        return new ChatLine(0, new ChatComponentText(words), chatLineId);
    }
}
