package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.util.StatCollector;

/**
 * What a page's help says, behind the question mark at the end of its
 * tool strip and under F1: a short guide to what the page is for and how
 * it works, then the page's own keys. Every page's help ends with the
 * keys every page shares ({@link PageKeys#windowAreas}), which the help
 * adds itself.
 */
public final class PageHelp {
    /** The guide's paragraphs, each read from the language file. */
    public final List<String> guide;
    /** The page's own keys, in areas. */
    public final List<PageKeys.Area> areas;

    public PageHelp(List<String> guide, List<PageKeys.Area> areas) {
        this.guide = guide == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(guide));
        this.areas = areas == null ? Collections.<PageKeys.Area>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<PageKeys.Area>(areas));
    }

    /**
     * The paragraphs the language file holds under {@code prefix}: its
     * {@code .1}, {@code .2} and on, until the first it does not hold.
     */
    public static List<String> paragraphs(String prefix) {
        List<String> read = new ArrayList<String>();
        for (int index = 1; index <= 12; index++) {
            String key = prefix + "." + index;
            if (!StatCollector.canTranslate(key)) {
                break;
            }
            read.add(StatCollector.translateToLocal(key));
        }
        return read;
    }
}
