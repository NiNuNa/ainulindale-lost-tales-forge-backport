package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.lwjgl.input.Keyboard;

public final class LostTalesMapMoveMarkerPromptTest {
    private static final int WIDTH = 854;
    private static final int HEIGHT = 480;

    @Test
    public void eachActionAnswersItsOwnButton() {
        LostTalesMapMoveMarkerPrompt prompt =
                new LostTalesMapMoveMarkerPrompt(true);

        assertEquals(LostTalesMapMoveMarkerPrompt.Action.MOVE,
                click(prompt, first()));
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.LEAVE,
                click(prompt, second()));
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.REMOVE,
                click(prompt, third()));
    }

    /**
     * Opened on the marker itself there is nowhere to move it to, so only
     * leaving and removing it are on offer.
     */
    @Test
    public void withoutADestinationMovingIsNotOffered() {
        LostTalesMapMoveMarkerPrompt prompt =
                new LostTalesMapMoveMarkerPrompt(false);

        assertEquals(LostTalesMapMoveMarkerPrompt.Action.NONE,
                click(prompt, first()));
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.NONE,
                prompt.keyTyped(Keyboard.KEY_RETURN));
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.REMOVE,
                click(prompt, third()));
    }

    @Test
    public void clickingThePanelBesideTheButtonsChangesNothing() {
        LostTalesMapMoveMarkerPrompt prompt =
                new LostTalesMapMoveMarkerPrompt(true);
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(WIDTH, HEIGHT);

        assertEquals(LostTalesMapMoveMarkerPrompt.Action.NONE,
                prompt.mouseClicked(WIDTH, HEIGHT,
                        layout.x + 2, layout.y + 2, 0));
        // A right click is not an answer either.
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.NONE,
                prompt.mouseClicked(WIDTH, HEIGHT,
                        first().x + first().width / 2,
                        first().y + first().height / 2, 1));
    }

    @Test
    public void escapeLeavesTheMarkerWhereItIs() {
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.LEAVE,
                new LostTalesMapMoveMarkerPrompt(true)
                        .keyTyped(Keyboard.KEY_ESCAPE));
        assertEquals(LostTalesMapMoveMarkerPrompt.Action.LEAVE,
                new LostTalesMapMoveMarkerPrompt(false)
                        .keyTyped(Keyboard.KEY_ESCAPE));
    }

    private static LostTalesMapMoveMarkerPrompt.Action click(
            LostTalesMapMoveMarkerPrompt prompt,
            LostTalesMapChoicePrompt.Bounds bounds) {
        return prompt.mouseClicked(WIDTH, HEIGHT,
                bounds.x + bounds.width / 2,
                bounds.y + bounds.height / 2, 0);
    }

    private static LostTalesMapChoicePrompt.Bounds first() {
        return LostTalesMapChoicePrompt.calculateLayout(
                WIDTH, HEIGHT).first;
    }

    private static LostTalesMapChoicePrompt.Bounds second() {
        return LostTalesMapChoicePrompt.calculateLayout(
                WIDTH, HEIGHT).second;
    }

    private static LostTalesMapChoicePrompt.Bounds third() {
        return LostTalesMapChoicePrompt.calculateLayout(
                WIDTH, HEIGHT).third;
    }
}
