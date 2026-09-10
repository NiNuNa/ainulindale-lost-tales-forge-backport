package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import lotr.client.gui.LOTRGuiAchievements;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.achievement.GuiAchievements;
import net.minecraft.event.HoverEvent;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.stats.StatList;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.AchievementPage;

/**
 * Where a click on an achievement in chat leads: the achievements
 * screen, opened on that achievement. Vanilla's screen is a map, so it
 * is scrolled to stand the achievement in the middle, on the page that
 * holds it; LOTR's is a list by category, so its category is chosen.
 * Nothing here is reached for a line that names no achievement, and
 * whatever the screens refuse, the click does nothing rather than
 * failing the game.
 */
final class ChatAchievementScreens {

    private ChatAchievementScreens() {}

    /**
     * Opens the achievements screen on the achievement the run names.
     * False when the run names none, or the screen could not be opened.
     */
    static boolean open(Minecraft minecraft, IChatComponent part) {
        if (minecraft == null || !ChatInteractions.isAchievement(part)) {
            return false;
        }
        HoverEvent hover = part.getChatStyle().getChatHoverEvent();
        String value = hover.getValue() == null ? ""
                : hover.getValue().getUnformattedText();
        try {
            if (hover.getAction() == HoverEvent.Action.SHOW_ACHIEVEMENT) {
                return openVanilla(minecraft, value);
            }
            return openLotr(minecraft, value);
        } catch (RuntimeException refused) {
            logOnce(refused);
            return false;
        } catch (LinkageError refused) {
            logOnce(refused);
            return false;
        }
    }

    private static boolean openVanilla(Minecraft minecraft, String statId) {
        StatBase stat = StatList.func_151177_a(statId);
        if (!(stat instanceof Achievement) || minecraft.thePlayer == null) {
            return false;
        }
        minecraft.displayGuiScreen(new FocusedScreen(null,
                minecraft.thePlayer.getStatFileWriter(), (Achievement)stat));
        return true;
    }

    /**
     * LOTR names one of its achievements as {@code CATEGORY$ID}. The
     * category is what its screen is opened on; the id names the
     * achievement within it, which the screen lists on its own.
     */
    private static boolean openLotr(Minecraft minecraft, String value) {
        int split = value.indexOf('$');
        String categoryName = split < 0 ? value : value.substring(0, split);
        LOTRAchievement.Category category;
        try {
            category = LOTRAchievement.Category.valueOf(categoryName);
        } catch (IllegalArgumentException unknown) {
            return false;
        }
        selectLotrCategory(category);
        minecraft.displayGuiScreen(new LOTRGuiAchievements());
        return true;
    }

    /**
     * Vanilla's screen scrolled to one achievement: the map's scroll
     * position is set exactly as the screen's own constructor sets it
     * for the first achievement, and the page is the one that holds
     * the achievement — vanilla's own, or a mod's.
     */
    private static final class FocusedScreen extends GuiAchievements {
        FocusedScreen(GuiScreen parent, StatFileWriter statistics,
                      Achievement achievement) {
            super(parent, statistics);
            // Half the map's 141 pixels, and the twelve the screen's
            // own constructor takes off the column.
            double x = achievement.displayColumn * 24 - 70 - 12;
            double y = achievement.displayRow * 24 - 70;
            this.field_146569_s = x;
            this.field_146567_u = x;
            this.field_146565_w = x;
            this.field_146568_t = y;
            this.field_146566_v = y;
            this.field_146573_x = y;
            selectPage(pageOf(achievement));
        }

        /** The index of the mod page holding the achievement, or -1 for vanilla's own. */
        private static int pageOf(Achievement achievement) {
            // The screen counts pages in the order Forge registered
            // them, which is the order it hands them out by index.
            int count = AchievementPage.getAchievementPages().size();
            for (int index = 0; index < count; index++) {
                AchievementPage page = AchievementPage.getAchievementPage(index);
                if (page != null && page.getAchievements() != null
                        && page.getAchievements().contains(achievement)) {
                    return index;
                }
            }
            return -1;
        }

        /**
         * Forge keeps the shown page in a private field of its own
         * naming; it is set by name and shape, and left alone when the
         * screen is not shaped that way, which shows vanilla's page.
         */
        private void selectPage(int page) {
            if (page < 0 || CURRENT_PAGE == null) {
                return;
            }
            try {
                CURRENT_PAGE.setInt(this, page);
            } catch (IllegalAccessException refused) {
                logOnce(refused);
            }
        }
    }

    private static final Field CURRENT_PAGE = resolveField(
            GuiAchievements.class, "currentPage", int.class, false);
    private static final Field LOTR_CATEGORY = resolveField(
            LOTRGuiAchievements.class, "currentCategory",
            LOTRAchievement.Category.class, true);
    private static final Field LOTR_DIMENSION = resolveField(
            LOTRGuiAchievements.class, "currentDimension",
            LOTRDimension.class, true);
    private static final Field LOTR_PREVIOUS_DIMENSION = resolveField(
            LOTRGuiAchievements.class, "prevDimension",
            LOTRDimension.class, true);

    /**
     * LOTR's screen remembers the category it last showed in static
     * fields and, on opening, falls back to the dimension's first
     * category when the dimension changed since. All three are set so
     * the chosen category stands; when the screen is not shaped this
     * way it opens as it would from the menu.
     */
    private static void selectLotrCategory(LOTRAchievement.Category category) {
        if (category == null || LOTR_CATEGORY == null
                || LOTR_DIMENSION == null || LOTR_PREVIOUS_DIMENSION == null) {
            return;
        }
        try {
            LOTR_CATEGORY.set(null, category);
            LOTR_DIMENSION.set(null, category.dimension);
            LOTR_PREVIOUS_DIMENSION.set(null, category.dimension);
        } catch (IllegalAccessException refused) {
            logOnce(refused);
        }
    }

    /** A field by name, only when it has the shape it is used with. */
    private static Field resolveField(Class<?> owner, String name,
                                      Class<?> type, boolean isStatic) {
        try {
            Field field = owner.getDeclaredField(name);
            if (field.getType() != type
                    || Modifier.isStatic(field.getModifiers()) != isStatic
                    || Modifier.isFinal(field.getModifiers())) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException missing) {
            return null;
        } catch (RuntimeException refused) {
            return null;
        } catch (LinkageError refused) {
            return null;
        }
    }

    private static boolean failureLogged;

    private static void logOnce(Throwable throwable) {
        if (!failureLogged) {
            failureLogged = true;
            FMLLog.warning("[%s] Could not open the achievements screen from chat: %s",
                    LostTalesMetaData.MOD_ID, throwable.toString());
        }
    }
}
