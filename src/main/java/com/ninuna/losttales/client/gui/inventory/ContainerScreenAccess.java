package com.ninuna.losttales.client.gui.inventory;

import com.ninuna.losttales.util.LostTalesLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.InventoryEffectRenderer;

/**
 * Reads where a container screen stands and whether it shows the
 * player's potion effects, for what is drawn beside it from outside the
 * screen. Reflection is required because {@code guiLeft} and
 * {@code guiTop} are protected and the effect flag private, and neither
 * Forge nor LOTR caches them. Each field is resolved once, by its MCP or
 * its SRG name, and kept only if it has the shape it should: an instance
 * field of that type, declared there.
 */
public final class ContainerScreenAccess {
    private static final Field GUI_LEFT = field(GuiContainer.class, int.class,
            "guiLeft", "field_147003_i");
    private static final Field GUI_TOP = field(GuiContainer.class, int.class,
            "guiTop", "field_147009_r");
    private static final Field SHOWS_EFFECTS = field(InventoryEffectRenderer.class,
            boolean.class, "field_147045_u");
    private static boolean failureLogged;

    private ContainerScreenAccess() {}

    /** Thrown when a screen's place cannot be read. */
    public static final class Unavailable extends Exception {
        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The screen's left edge, as its slots are drawn from. */
    public static int guiLeft(GuiContainer screen) throws Unavailable {
        return readInt(GUI_LEFT, screen, "guiLeft");
    }

    /** The screen's top edge, as its slots are drawn from. */
    public static int guiTop(GuiContainer screen) throws Unavailable {
        return readInt(GUI_TOP, screen, "guiTop");
    }

    /**
     * Whether vanilla draws the player's potion effects left of the
     * screen: set as the screen opened, when it made room for them.
     */
    public static boolean showsEffects(InventoryEffectRenderer screen) throws Unavailable {
        if (SHOWS_EFFECTS == null || screen == null) {
            throw unavailable("the effect flag", null);
        }
        try {
            return SHOWS_EFFECTS.getBoolean(screen);
        } catch (IllegalAccessException exception) {
            throw unavailable("the effect flag", exception);
        }
    }

    private static int readInt(Field field, GuiContainer screen, String what) throws Unavailable {
        if (field == null || screen == null) {
            throw unavailable(what, null);
        }
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException exception) {
            throw unavailable(what, exception);
        }
    }

    private static Unavailable unavailable(String what, Throwable cause) {
        if (!failureLogged) {
            failureLogged = true;
            LostTalesLog.warning("A container screen's %s cannot be read; nothing is drawn beside it.",
                    what);
        }
        return new Unavailable("Cannot read " + what, cause);
    }

    private static Field field(Class<?> owner, Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                if (field.getType() == type && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Try the other naming convention.
            } catch (RuntimeException ignored) {
                // A security manager or a broken class: unavailable.
            }
        }
        return null;
    }
}
