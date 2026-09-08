package com.ninuna.losttales.gui.screen.character.creator;

/**
 * A choice among a list the screen owns: which is chosen, what each is
 * called, and what choosing another does. A stepper, a list and a grid
 * all read one of these, so the screen keeps its options in one place and
 * the controls only present them.
 */
public interface CreatorChoice {

    /** How many there are to choose from; zero shows as no choice. */
    int count();

    /** Which is chosen, or -1 for none. */
    int index();

    /** The stable id of that option. */
    String id(int index);

    /** What that option is called, for the player. */
    String label(int index);

    /** The player chose that one. */
    void choose(int index);

    /**
     * Whether the choice is currently made by something else — a body
     * type the skin decides, say. A fixed choice is shown and cannot be
     * stepped.
     */
    boolean isFixed();

    /** What to show in place of the value while the choice is fixed. */
    String fixedLabel();

    /** What to show when there is nothing to choose from. */
    String emptyLabel();
}
