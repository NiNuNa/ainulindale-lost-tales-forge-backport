package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.List;
import lotr.client.gui.LOTRGuiMap;

/**
 * Isolates the LOTR v36.15 members needed by the Lost Tales map layout.
 * Native map code still performs all coordinate conversion and rendering; the
 * transformer calls this class after LOTR calculates its viewport.
 */
@SideOnly(Side.CLIENT)
public final class LostTalesLotrMapLayout {
    private static final int BOTTOM_STATUS_MARGIN = 4;
    private static final int INACTIVE_BOTTOM_TEXT = Integer.MIN_VALUE;

    private static final ThreadLocal<ArrayDeque<Integer>> bottomTextBounds =
            new ThreadLocal<ArrayDeque<Integer>>() {
                @Override
                protected ArrayDeque<Integer> initialValue() {
                    return new ArrayDeque<Integer>();
                }
            };

    private static Field fullscreenField;
    private static Field mapWidthField;
    private static Field mapHeightField;
    private static Field mapXMinField;
    private static Field mapXMaxField;
    private static Field mapYMinField;
    private static Field mapYMaxField;
    private static Field conquestGridField;
    private static Field hasOverlayField;
    private static Field mapWidgetsField;
    private static Field widgetFullScreenField;
    private static boolean reflectionReady;
    private static boolean reflectionFailed;

    private LostTalesLotrMapLayout() {
    }

    static boolean prepareBeforeInit(LOTRGuiMap gui) {
        if (!isFullscreenLayoutActive(gui)) {
            return false;
        }
        try {
            fullscreenField.setBoolean(null, true);
            return true;
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
            return false;
        }
    }

    static boolean finishInit(LOTRGuiMap gui) {
        if (!isFullscreenLayoutActive(gui)) {
            return false;
        }
        try {
            fullscreenField.setBoolean(null, true);
            setFullscreenBounds(gui);
            Object fullscreenWidget = widgetFullScreenField.get(gui);
            Object widgets = mapWidgetsField.get(null);
            if (fullscreenWidget != null && widgets instanceof List) {
                ((List<?>)widgets).remove(fullscreenWidget);
            }
            return true;
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
            return false;
        }
    }

    static void prepareForDraw(LOTRGuiMap gui) {
        if (!isFullscreenLayoutActive(gui)) {
            return;
        }
        try {
            fullscreenField.setBoolean(null, true);
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
        }
    }

    /** Called from LOTRGuiMap.setupMapDimensions after its native calculation. */
    public static void applyFullscreenBounds(LOTRGuiMap gui) {
        if (!isFullscreenLayoutActive(gui)) {
            return;
        }
        try {
            setFullscreenBounds(gui);
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
        }
    }

    /** Called from LOTR's border renderer before it draws the framed panel. */
    public static boolean shouldSuppressMapFrame(LOTRGuiMap gui) {
        return isFullscreenLayoutActive(gui);
    }

    /**
     * Gives LOTR's existing fullscreen subtitle renderer its former bottom
     * margin while the actual map remains fullscreen.
     */
    public static void beginFullscreenSubtitles(LOTRGuiMap gui) {
        if (gui instanceof LostTalesLotrMapGui) {
            ((LostTalesLotrMapGui)gui).renderControlBar(false);
        }
        beginBottomReservedBounds(gui, isFullscreenLayoutActive(gui));
    }

    /** Keeps native marker tooltips above the control bar. */
    public static void beginMapTooltip(LOTRGuiMap gui) {
        beginBottomReservedBounds(gui, isControlBarVisible(gui));
    }

    public static void endMapTooltip() {
        endBottomReservedBounds();
    }

    private static void beginBottomReservedBounds(
            LOTRGuiMap gui, boolean reserveBottom) {
        ArrayDeque<Integer> bounds = bottomTextBounds.get();
        int previous = INACTIVE_BOTTOM_TEXT;
        if (reserveBottom) {
            try {
                previous = mapYMaxField.getInt(null);
                mapYMaxField.setInt(null,
                        calculateBottomTextMapYMax(gui));
            } catch (IllegalAccessException exception) {
                markReflectionFailed(exception);
            } catch (RuntimeException exception) {
                markReflectionFailed(exception);
            }
        }
        bounds.push(Integer.valueOf(previous));
    }

    /** Restores the real viewport after LOTR finishes drawing subtitles. */
    public static void endFullscreenSubtitles() {
        endBottomReservedBounds();
    }

    private static void endBottomReservedBounds() {
        ArrayDeque<Integer> bounds = bottomTextBounds.get();
        if (bounds.isEmpty()) {
            return;
        }
        int previous = bounds.pop().intValue();
        try {
            if (previous != INACTIVE_BOTTOM_TEXT && mapYMaxField != null) {
                mapYMaxField.setInt(null, previous);
            }
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
        } finally {
            if (bounds.isEmpty()) {
                bottomTextBounds.remove();
            }
        }
    }

    static boolean isFullscreenLayoutActive(LOTRGuiMap gui) {
        if (!(gui instanceof LostTalesLotrMapGui)
                || !Boolean.getBoolean(
                LostTalesClassTransformer.LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY)
                || !ensureReflection()) {
            return false;
        }
        try {
            return !conquestGridField.getBoolean(gui);
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
            return false;
        }
    }

    static boolean isControlBarVisible(LOTRGuiMap gui) {
        if (!Boolean.getBoolean(
                LostTalesClassTransformer.LOTR_MAP_CONTROL_BAR_ACTIVE_PROPERTY)
                || !isFullscreenLayoutActive(gui)) {
            return false;
        }
        try {
            return !hasOverlayField.getBoolean(gui);
        } catch (IllegalAccessException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
            return false;
        }
    }

    static int resolveStatusY(
            LOTRGuiMap gui, int nativeMapYMax, int panelHeight) {
        if (!isFullscreenLayoutActive(gui)) {
            return nativeMapYMax + 10;
        }
        return Math.max(0, gui.height - panelHeight - BOTTOM_STATUS_MARGIN);
    }

    private static int calculateBottomTextMapYMax(LOTRGuiMap gui) {
        int legendHeight = gui instanceof LostTalesLotrMapGui
                ? LostTalesLotrMapLegend.getReservedHeight(
                        (LostTalesLotrMapGui)gui)
                : 0;
        return Math.max(0,
                gui.height - LostTalesLotrMapControlBar.HEIGHT
                        - legendHeight);
    }

    private static void setFullscreenBounds(LOTRGuiMap gui)
            throws IllegalAccessException {
        int width = Math.max(0, gui.width);
        int height = Math.max(0, gui.height);
        mapXMinField.setInt(null, 0);
        mapXMaxField.setInt(null, width);
        mapYMinField.setInt(null, 0);
        mapYMaxField.setInt(null, height);
        mapWidthField.setInt(null, width);
        mapHeightField.setInt(null, height);
    }

    private static synchronized boolean ensureReflection() {
        if (reflectionReady) {
            return true;
        }
        if (reflectionFailed) {
            return false;
        }
        try {
            Class<LOTRGuiMap> mapClass = LOTRGuiMap.class;
            fullscreenField = field(mapClass, "fullscreen");
            mapWidthField = field(mapClass, "mapWidth");
            mapHeightField = field(mapClass, "mapHeight");
            mapXMinField = field(mapClass, "mapXMin");
            mapXMaxField = field(mapClass, "mapXMax");
            mapYMinField = field(mapClass, "mapYMin");
            mapYMaxField = field(mapClass, "mapYMax");
            conquestGridField = field(mapClass, "isConquestGrid");
            hasOverlayField = field(mapClass, "hasOverlay");
            mapWidgetsField = field(mapClass, "mapWidgets");
            widgetFullScreenField = field(mapClass, "widgetFullScreen");
            reflectionReady = true;
            return true;
        } catch (ReflectiveOperationException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (RuntimeException exception) {
            markReflectionFailed(exception);
            return false;
        } catch (LinkageError error) {
            markReflectionFailed(error);
            return false;
        }
    }

    private static Field field(Class<?> owner, String name)
            throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static synchronized void markReflectionFailed(Throwable cause) {
        if (reflectionFailed) {
            return;
        }
        reflectionReady = false;
        reflectionFailed = true;
        FMLLog.warning(
                "[%s] Fullscreen LOTR map layout disabled: expected v36.15 "
                        + "GUI members were unavailable (%s)",
                LostTalesMetaData.MOD_ID,
                cause == null ? "unknown error" : cause.toString());
    }
}
