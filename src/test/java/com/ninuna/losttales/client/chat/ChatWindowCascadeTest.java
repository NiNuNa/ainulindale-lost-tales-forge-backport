package com.ninuna.losttales.client.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ChatWindowCascadeTest {

    private static final int SCREEN_WIDTH = 427;
    private static final int SCREEN_HEIGHT = 240;
    private static final int WIDTH = 160;
    private static final double HEIGHT = 68.0D;

    @Test
    public void aWindowLandsOneStepRightAndDown() {
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                10.0D, 20.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                ChatWindowCascade.STEP);
        assertEquals(10.0D + ChatWindowCascade.STEP, corner.x, 0.0D);
        assertEquals(20.0D + ChatWindowCascade.STEP, corner.y, 0.0D);
    }

    @Test
    public void theStepKeepsTheTabStripBehindInView() {
        assertTrue(ChatWindowCascade.STEP >= ChatChannelTabBar.ROW_HEIGHT);
    }

    @Test
    public void aBottomOverflowRestartsTheColumnAtTheTop() {
        double referenceY = SCREEN_HEIGHT - HEIGHT - 5.0D;
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                10.0D, referenceY, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                ChatWindowCascade.STEP);
        assertEquals("x keeps stepping", 10.0D + ChatWindowCascade.STEP, corner.x, 0.0D);
        assertEquals("y wraps to the margin", 0.0D, corner.y, 0.0D);
    }

    @Test
    public void aRightOverflowRestartsTheRowAtTheLeft() {
        double referenceX = SCREEN_WIDTH - WIDTH - 5.0D;
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                referenceX, 20.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                ChatWindowCascade.STEP);
        assertEquals("x wraps to the margin", 0.0D, corner.x, 0.0D);
        assertEquals("y keeps stepping", 20.0D + ChatWindowCascade.STEP, corner.y, 0.0D);
    }

    @Test
    public void bothOverflowingGivesTheOrigin() {
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                SCREEN_WIDTH - WIDTH, SCREEN_HEIGHT - HEIGHT, WIDTH, HEIGHT,
                SCREEN_WIDTH, SCREEN_HEIGHT, 4, ChatWindowCascade.STEP);
        assertEquals(4.0D, corner.x, 0.0D);
        assertEquals(4.0D, corner.y, 0.0D);
    }

    @Test
    public void aBoxTooBigForTheScreenIsClampedNotLost() {
        ChatWindowCascade.Corner corner = ChatWindowCascade.place(
                0.0D, 0.0D, SCREEN_WIDTH + 50, SCREEN_HEIGHT + 50.0D,
                SCREEN_WIDTH, SCREEN_HEIGHT, 0, ChatWindowCascade.STEP);
        assertEquals(0.0D, corner.x, 0.0D);
        assertEquals(0.0D, corner.y, 0.0D);
    }

    @Test
    public void theSameInputsGiveTheSameCorner() {
        ChatWindowCascade.Corner first = ChatWindowCascade.place(
                33.0D, 44.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                ChatWindowCascade.STEP);
        ChatWindowCascade.Corner second = ChatWindowCascade.place(
                33.0D, 44.0D, WIDTH, HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, 0,
                ChatWindowCascade.STEP);
        assertEquals(first.x, second.x, 0.0D);
        assertEquals(first.y, second.y, 0.0D);
    }
}
