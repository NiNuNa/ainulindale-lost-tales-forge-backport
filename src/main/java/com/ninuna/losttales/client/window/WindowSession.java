package com.ninuna.losttales.client.window;

/**
 * What the window system lets go of as the player leaves a world: the
 * pages that stood for things in it, every page's content, the screen's
 * entrance, the marked tabs, the search, a tab standing alone in full
 * screen, the sub-windows open as the screen last closed, the view, and
 * what the pinned windows were showing. The layout itself is the account's
 * and stays.
 */
public final class WindowSession {
    private WindowSession() {}

    /** Called once the player has left the world, on the client thread. */
    public static void clear() {
        // A page that stands for a thing in this world (a waystone) goes
        // before every page lets its content go.
        WindowPages.closeWorldPages();
        WindowPages.forgetContents();
        WindowOpening.clear();
        TabSelection.clear();
        WindowSearch.close();
        ContentView.leave();
        SubWindowPlaces.forgetOpen();
        WindowView.clear();
        PinnedWindows.clear();
    }
}
