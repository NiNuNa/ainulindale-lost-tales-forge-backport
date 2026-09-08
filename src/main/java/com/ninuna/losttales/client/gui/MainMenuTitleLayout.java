package com.ninuna.losttales.client.gui;

/** Fits the complete title above the menu without changing its aspect ratio. */
final class MainMenuTitleLayout {
    static final int TEXTURE_WIDTH = 4014;
    static final int TEXTURE_HEIGHT = 796;
    // Exclude the transparent export border when matching vanilla's visible width.
    static final int TEXTURE_PADDING = 1;
    static final int ARTWORK_WIDTH = TEXTURE_WIDTH - TEXTURE_PADDING * 2;
    static final int ARTWORK_HEIGHT = TEXTURE_HEIGHT - TEXTURE_PADDING * 2;
    static final int TOP = 30;
    static final int SIDE_MARGIN = 12;
    static final int GAP = 8;
    static final int MAX_WIDTH = 256;

    final float left;
    final float width;
    final float height;

    private MainMenuTitleLayout(int screenWidth, float width) {
        this.width = width;
        this.height = width * ARTWORK_HEIGHT / ARTWORK_WIDTH;
        this.left = (float) Math.floor((screenWidth - width) / 2.0F);
    }

    static MainMenuTitleLayout fit(int screenWidth, int buttonsTop, int subtitleHeight) {
        int room = buttonsTop - TOP - GAP;
        if (subtitleHeight > 0) {
            room -= subtitleHeight + GAP;
        }
        float width = Math.min(MAX_WIDTH, screenWidth - SIDE_MARGIN * 2);
        width = Math.min(width, room * (float) ARTWORK_WIDTH / ARTWORK_HEIGHT);
        return new MainMenuTitleLayout(screenWidth, Math.max(0.0F, width));
    }

    int subtitleY() {
        // Round toward the logo to keep the reserved gap below the subtitle.
        return (int) (TOP + this.height) + GAP;
    }
}
