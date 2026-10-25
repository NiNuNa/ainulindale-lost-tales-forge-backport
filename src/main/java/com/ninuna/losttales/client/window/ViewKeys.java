package com.ninuna.losttales.client.window;

/**
 * What a view's key does on the window screen (WIN-03, WIN-05): T, the
 * command key, M, J, the Fellowships, Characters and Skills keys and every
 * custom view's. Where no field is typed in, on a page such as the map or
 * on Views, it swaps the screen to its view, and pressed on its own view it
 * closes the screen. Where a field is typed in, as in a conversation, a
 * key that types a character types it, and one that types nothing works
 * all the same. A key the page in front answers itself stays the page's:
 * the missive board's T takes a missive. With Ctrl or Alt held it is no
 * view's key.
 */
final class ViewKeys {
    /** What the press does. */
    enum Act {
        /** It goes on to what else takes it: the field, the page, the screen's own keys. */
        PASS,
        /** The screen swaps to the key's view. */
        SWAP,
        /** The screen closes. */
        CLOSE
    }

    private ViewKeys() {}

    /**
     * What a press does: {@code viewKey}, it is some view's key;
     * {@code modified}, Ctrl or Alt is held; {@code typedIn}, a field holds
     * the keys; {@code types}, the press types a character;
     * {@code pageOwns}, the page in front has a key of its own on it;
     * {@code onItsView}, the screen shows the key's view.
     */
    static Act act(boolean viewKey, boolean modified, boolean typedIn,
                   boolean types, boolean pageOwns, boolean onItsView) {
        if (!viewKey || modified || typedIn && types || pageOwns) {
            return Act.PASS;
        }
        return onItsView ? Act.CLOSE : Act.SWAP;
    }
}
