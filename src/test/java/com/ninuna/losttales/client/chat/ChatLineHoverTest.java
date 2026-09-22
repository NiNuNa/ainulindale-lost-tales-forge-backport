package com.ninuna.losttales.client.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.gui.style.LostTalesColors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A message under the pointer: its chevron steps and lights, its words
 * follow further one after another, the row stretching by whole pixels
 * while it travels, and all of it goes back once the pointer leaves.
 */
public final class ChatLineHoverTest {
    private static final long MILLIS = 1000000L;
    private static final int LINE = 42;

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        ChatLineHover.clear();
        MotionTestSettings.preview("{\"chat.line.hover\": {\"parts\": {"
                + "\"chevron\": {\"poses\": {\"rest\": {\"x\": 0, \"brighten\": 0},"
                + "  \"on\": {\"x\": 1, \"brighten\": 1}},"
                + "  \"beats\": {\"on\": {\"to\": \"on\", \"duration\": 100,"
                + "    \"curve\": \"linear\"},"
                + "    \"off\": {\"to\": \"rest\", \"duration\": 100,"
                + "    \"curve\": \"linear\"}}},"
                + "\"words\": {\"poses\": {\"rest\": {\"x\": 0}, \"on\": {\"x\": 3}},"
                + "  \"beats\": {\"on\": {\"to\": \"on\", \"duration\": 100,"
                + "    \"stagger\": 10, \"curve\": \"linear\","
                + "    \"tracks\": {\"gap\": [{\"bump\": 2}]}},"
                + "    \"off\": {\"to\": \"rest\", \"duration\": 100,"
                + "    \"curve\": \"linear\"}}}},"
                + "\"params\": {\"stagger_span\": 20}}}");
    }

    @After
    public void tearDown() {
        ChatLineHover.clear();
        this.settings.restore();
    }

    @Test
    public void aMessageAtRestIsDrawnAsItAlwaysIs() {
        assertNull(ChatLineHover.rowMotion(LINE, 0L));
    }

    @Test
    public void theChevronStepsALittleAndTheWordsFurther() {
        ChatLineHover.advance(LINE, 0L);
        ChatRowMotion early = ChatLineHover.rowMotion(LINE, 50L * MILLIS);
        assertNotNull(early);
        assertEquals(0.5F, early.chevronX(), 1.0E-5F);
        assertEquals(0.5F, early.brighten(), 1.0E-5F);
        ChatRowMotion landed = ChatLineHover.rowMotion(LINE, 200L * MILLIS);
        assertEquals(1.0F, landed.chevronX(), 1.0E-5F);
        assertEquals(3.0F, landed.wordX(0, 5), 1.0E-5F);
        assertEquals(3.0F, landed.wordX(4, 5), 1.0E-5F);
        assertEquals(3.0F, landed.endX(), 1.0E-5F);
    }

    @Test
    public void theRowStretchesByWholePixelsWhileItTravels() {
        ChatLineHover.advance(LINE, 0L);
        ChatRowMotion midway = ChatLineHover.rowMotion(LINE, 50L * MILLIS);
        // The first word half way along its travel; the last two
        // staggers behind, with its share of the stretch — the gap's
        // bump rounded to whole pixels — added.
        assertEquals(1.5F, midway.wordX(0, 3), 1.0E-4F);
        assertEquals(0.9F + 2.0F, midway.wordX(2, 3), 1.0E-4F);
        // Landed, the stretch has closed again.
        ChatRowMotion landed = ChatLineHover.rowMotion(LINE, 200L * MILLIS);
        assertEquals(3.0F, landed.wordX(2, 3), 1.0E-5F);
    }

    @Test
    public void aLongRowSpreadsItsStaggerOverTheSpanAtMost() {
        ChatLineHover.advance(LINE, 0L);
        ChatRowMotion motion = ChatLineHover.rowMotion(LINE, 30L * MILLIS);
        // Twenty words at a ten millisecond stagger would spread over
        // two hundred milliseconds; the span holds them to twenty, so the
        // last word is twenty milliseconds behind the first.
        assertEquals(0.9F, motion.wordX(0, 20), 1.0E-4F);
        assertEquals(0.3F + 1.0F, motion.wordX(19, 20), 1.0E-4F);
    }

    @Test
    public void leavingTakesItAllBackAndLetsItGo() {
        ChatLineHover.advance(LINE, 0L);
        ChatLineHover.advance(0, 200L * MILLIS);
        ChatRowMotion leaving = ChatLineHover.rowMotion(LINE, 250L * MILLIS);
        assertNotNull(leaving);
        assertEquals(1.5F, leaving.wordX(0, 1), 1.0E-5F);
        ChatLineHover.advance(0, 400L * MILLIS);
        assertNull(ChatLineHover.rowMotion(LINE, 400L * MILLIS));
    }

    @Test
    public void formattingCarriesAcrossAWordBreak() {
        assertEquals("", LostTalesChatVisualStyle.activeCodes("plain words"));
        assertEquals("§c§o",
                LostTalesChatVisualStyle.activeCodes("§cred §oslanted "));
        assertEquals("§a", LostTalesChatVisualStyle.activeCodes(
                "§lbold §agreen "));
        assertEquals("", LostTalesChatVisualStyle.activeCodes(
                "§c§lloud §rquiet "));
    }

    @Test
    public void aLighterShadeStaysOnThePaletteWhereItCan() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                LostTalesChatVisualStyle.lighterShadeOf(
                        LostTalesColors.rgb(LostTalesColors.ORCHID)));
        int offPalette = 0x102030;
        int lighter = LostTalesChatVisualStyle.lighterShadeOf(offPalette);
        assertTrue((lighter & 0xFF) > (offPalette & 0xFF));
    }
}
