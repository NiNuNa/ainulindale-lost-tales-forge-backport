package com.ninuna.losttales.client.window;

/**
 * How a world page — a waystone's, a missive board's, a letter's — lets
 * go of the thing it shows once that thing is out of the player's reach:
 * its tab closes by itself, fading as a tab closed by hand does, and a
 * notice over its window's bar says why. A page in front watches from
 * its own tick; the screen part installed here watches every world page
 * whose tab stands behind another in its window, where the page's own
 * tick does not reach. The watching runs while the window screen is
 * open: a tab left behind closes, with its notice, at the next opening.
 */
public final class WorldPageWatch {
    /** A world page that watches the thing it shows. */
    public interface Watched {
        /** Closes the page's tab where the thing is out of reach. */
        void watch();
    }

    /** Makes the part of the window screen that watches world pages behind other tabs. */
    private static final ScreenPart.Maker MAKER = new ScreenPart.Maker() {
        @Override
        public ScreenPart make(WindowScreen screen) {
            return screen.isWorldless() ? null : new Part(screen);
        }
    };

    private WorldPageWatch() {}

    /** Registers the part that watches world pages behind other tabs; once, as the client starts. */
    public static void install() {
        WindowScreen.addPart(MAKER);
    }

    /**
     * Closes a page's tab by itself, as a tab closed by hand, with
     * {@code notice} over its window's bar. A locked window keeps the
     * tabs the player put in it, but not one whose thing they left.
     */
    public static void close(PageTab tab, String notice) {
        if (tab == null) {
            return;
        }
        WindowScreen screen = WindowScreen.current();
        Window window = WindowLayout.windowOf(tab);
        if (screen != null) {
            if (window != null && notice != null && notice.length() > 0) {
                screen.showNotice(window.getId(), notice);
            }
            screen.closeTab(tab);
        }
        if (WindowLayout.isOpen(tab)) {
            final PageTab closing = tab;
            WindowLayout.removeTabs(new WindowLayout.TabFilter() {
                @Override
                public boolean matches(WindowTab open) {
                    return closing.equals(open);
                }
            });
        }
    }

    /**
     * The part of the window screen that watches every world page whose
     * tab stands behind another in its window; in front, a page watches
     * itself.
     */
    private static final class Part extends ScreenPart {
        Part(WindowScreen screen) {
            super(screen);
        }

        @Override
        public void tick() {
            for (WindowPages.Page page : WindowPages.all()) {
                if (!page.opensFromWorld()) {
                    continue;
                }
                PageTab tab = page.tab();
                Window window = WindowLayout.windowOf(tab);
                if (window == null || tab.equals(window.getActiveTab())) {
                    continue;
                }
                PageContent content = tab.content();
                if (content instanceof Watched) {
                    ((Watched)content).watch();
                }
            }
        }
    }
}
