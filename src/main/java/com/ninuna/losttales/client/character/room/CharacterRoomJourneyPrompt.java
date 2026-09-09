package com.ninuna.losttales.client.character.room;

import com.ninuna.losttales.client.input.LostTalesInputIconRenderer;
import com.ninuna.losttales.client.keybinding.LostTalesKeyBindings;
import com.ninuna.losttales.gui.hud.LostTalesNotificationHud;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.StatCollector;

/**
 * The one line the room shows over the world while nothing else is up:
 * <i>Press [R] to start your Journey!</i>, with the use key drawn as its
 * animated key icon in the middle of the sentence. It breathes rather
 * than sits there: in, held, out, a rest, and again, so it draws the eye
 * without being a fixture. Drawn in the notification slot, where every
 * other passing notice goes.
 */
public final class CharacterRoomJourneyPrompt {

    static final long FADE_IN_MS = 700L;
    static final long HOLD_MS = 1600L;
    static final long FADE_OUT_MS = 700L;
    static final long REST_MS = 1300L;
    static final long CYCLE_MS = FADE_IN_MS + HOLD_MS + FADE_OUT_MS + REST_MS;
    /** The font draws an alpha below four as opaque; below this nothing is drawn instead. */
    private static final int MIN_ALPHA = 4;
    /** Between the words and the key. */
    private static final int KEY_GAP = 4;
    /** Where the key goes in the translated line. */
    private static final String KEY_TOKEN = "%s";

    private CharacterRoomJourneyPrompt() {}

    /** Draws the line when the player stands in the room with no screen open. */
    public static void render(Minecraft minecraft) {
        if (!CharacterRoomSession.isInRoom(minecraft)
                || minecraft.currentScreen != null
                || minecraft.fontRenderer == null) {
            return;
        }
        float alpha = alphaAt(System.currentTimeMillis() % CYCLE_MS);
        int textAlpha = Math.round(alpha * 255.0F);
        if (textAlpha < MIN_ALPHA) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        ScaledResolution resolution = new ScaledResolution(minecraft,
                minecraft.displayWidth, minecraft.displayHeight);
        KeyBinding key = LostTalesKeyBindings.getUseKeyBinding();
        String line = StatCollector.translateToLocal("gui.losttales.character.room.journey");
        int token = line.indexOf(KEY_TOKEN);
        String before = token < 0 ? line : line.substring(0, token).trim();
        String after = token < 0 ? "" : line.substring(token + KEY_TOKEN.length()).trim();

        int keyWidth = LostTalesInputIconRenderer.measureKeyBinding(minecraft, key, 1.0F);
        int keyHeight = LostTalesInputIconRenderer.BASE_ICON_HEIGHT;
        int beforeWidth = font.getStringWidth(before);
        int afterWidth = font.getStringWidth(after);
        int width = beforeWidth + (before.length() > 0 ? KEY_GAP : 0) + keyWidth
                + (after.length() > 0 ? KEY_GAP : 0) + afterWidth;
        int height = Math.max(keyHeight, font.FONT_HEIGHT);
        int left = LostTalesNotificationHud.centerX(resolution.getScaledWidth(),
                resolution.getScaledHeight()) - width / 2;
        int top = LostTalesNotificationHud.claim(resolution.getScaledWidth(),
                resolution.getScaledHeight(), height);
        int textY = top + (height - font.FONT_HEIGHT) / 2 + 1;
        int color = (textAlpha << 24) | (LostTalesColors.IVORY & 0xFFFFFF);

        int x = left;
        if (before.length() > 0) {
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(before, x, textY, color);
            x += beforeWidth + KEY_GAP;
        }
        x += LostTalesInputIconRenderer.drawKeyBinding(minecraft, key, x,
                top + (height - keyHeight) / 2, 1.0F, alpha);
        if (after.length() > 0) {
            x += KEY_GAP;
            LostTalesSkyrimUiStyle.beginContent();
            font.drawStringWithShadow(after, x, textY, color);
        }
    }

    /**
     * How visible the line is that far into its cycle: rising through the
     * fade-in, full through the hold, falling through the fade-out, and
     * gone through the rest. Eased at both ends so it breathes rather
     * than blinks.
     */
    static float alphaAt(long cycleMillis) {
        long t = ((cycleMillis % CYCLE_MS) + CYCLE_MS) % CYCLE_MS;
        if (t < FADE_IN_MS) {
            return ease(t / (float) FADE_IN_MS);
        }
        t -= FADE_IN_MS;
        if (t < HOLD_MS) {
            return 1.0F;
        }
        t -= HOLD_MS;
        if (t < FADE_OUT_MS) {
            return ease(1.0F - t / (float) FADE_OUT_MS);
        }
        return 0.0F;
    }

    /** Smooth in and out: slow at the ends, quick through the middle. */
    private static float ease(float x) {
        return x * x * (3.0F - 2.0F * x);
    }
}
