package com.ninuna.losttales.gui.screen.missive;

import com.ninuna.losttales.quest.missive.LostTalesMissiveData;
import com.ninuna.losttales.quest.missive.LostTalesMissiveObjectiveData;
import com.ninuna.losttales.quest.missive.MissiveNotice;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The missive board page's list, from the board's notices: what a search
 * keeps, which notice stays picked as the board changes under the
 * player, the arrows' walk, and the time each notice has left.
 */
public final class MissiveNoticeListTest {

    /**
     * The words a game in English reads the letters in: the mod's own
     * missive and objective lines, and no other, so the rest of the suite
     * still reads keys where it expects them.
     */
    @BeforeClass
    public static void readInEnglish() throws Exception {
        StringBuilder lines = new StringBuilder();
        for (Map.Entry<String, String> line
                : LostTalesLangFile.english().entrySet()) {
            if (line.getKey().startsWith("missive.losttales.")
                    || line.getKey().startsWith("gui.losttales.quest.objective.")) {
                lines.append(line.getKey()).append('=').append(line.getValue())
                        .append('\n');
            }
        }
        StringTranslate.inject(new ByteArrayInputStream(
                lines.toString().getBytes("UTF-8")));
    }

    private static MissiveNotice notice(int slot, String id, String title,
                                        String issuer, String type,
                                        String target, int count) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("kill".equals(type) ? "entity" : "item", target);
        params.put("count", String.valueOf(count));
        LostTalesMissiveData missive = LostTalesMissiveData.builder(id, type)
                .titleId(title).issuerId(issuer).descriptionId(type)
                .target(target)
                .objective(new LostTalesMissiveObjectiveData("o", type, false,
                        params))
                .build();
        return new MissiveNotice(slot, 1000L, missive);
    }

    private static final MissiveNotice ROAD = notice(0, "q/road",
            "trouble_on_the_road", "road_warden", "kill", "Zombie", 5);
    private static final MissiveNotice COAL = notice(3, "q/coal",
            "materials_wanted", "quartermaster", "gather", "minecraft:coal", 12);
    private static final MissiveNotice BLANK = new MissiveNotice(5, 1000L, null);
    private static final MissiveNotice BONES = notice(8, "q/bones",
            "gatherers_pay", "village_reeve", "gather", "minecraft:bone", 6);
    private static final List<MissiveNotice> BOARD =
            Arrays.asList(ROAD, COAL, BLANK, BONES);

    @Test
    public void anEmptySearchKeepsEveryNoticeInSlotOrder() {
        assertEquals(BOARD, MissiveNoticeList.of(BOARD, ""));
        assertEquals(BOARD, MissiveNoticeList.of(BOARD, "   "));
        assertTrue(MissiveNoticeList.of(null, "").isEmpty());
    }

    /** A search reads the letters in the game's words, made from their template ids. */
    @Test
    public void aSearchKeepsTheNoticesHoldingEveryWordAnywhere() {
        assertEquals(Collections.singletonList(COAL),
                MissiveNoticeList.of(BOARD, "materials"));
        assertEquals("the issuer counts", Collections.singletonList(ROAD),
                MissiveNoticeList.of(BOARD, "warden"));
        assertEquals("the objectives count, in any order and case",
                Arrays.asList(COAL, BONES), MissiveNoticeList.of(BOARD, "GATHER"));
        assertEquals("the target's noun and the issuer",
                Collections.singletonList(BONES),
                MissiveNoticeList.of(BOARD, "bones reeve"));
        assertTrue("a notice that cannot be read says nothing",
                MissiveNoticeList.of(BOARD, "dragon").isEmpty());
    }

    @Test
    public void theNoticeInASlotIsFoundByItsSlot() {
        assertSame(COAL, MissiveNoticeList.inSlot(BOARD, 3));
        assertNull(MissiveNoticeList.inSlot(BOARD, 4));
        assertNull(MissiveNoticeList.inSlot(null, 0));
    }

    @Test
    public void thePickFollowsItsLetterAsTheBoardChanges() {
        assertEquals("nothing picked picks the first", 0,
                MissiveNoticeList.keepPick(BOARD, -1, ""));
        assertEquals("the letter picked stays picked", 3,
                MissiveNoticeList.keepPick(BOARD, 3, "q/coal"));
        MissiveNotice movedCoal = notice(6, "q/coal", "materials_wanted",
                "quartermaster", "gather", "minecraft:coal", 12);
        assertEquals("even where it moved", 6, MissiveNoticeList.keepPick(
                Arrays.asList(ROAD, movedCoal), 3, "q/coal"));
        List<MissiveNotice> taken = Arrays.asList(ROAD, BLANK, BONES);
        assertEquals("gone, the nearest notice before its slot", 0,
                MissiveNoticeList.keepPick(taken, 3, "q/coal"));
        assertEquals("the notice now in its slot", 5,
                MissiveNoticeList.keepPick(taken, 5, "q/gone"));
        assertEquals("nothing before it, the first", 5,
                MissiveNoticeList.keepPick(Arrays.asList(BLANK, BONES), 1,
                        "q/road"));
        assertEquals("a bare board picks nothing", -1, MissiveNoticeList
                .keepPick(Collections.<MissiveNotice>emptyList(), 3, "q/coal"));
    }

    @Test
    public void theArrowsWalkTheNoticesShownAndStopAtTheEnds() {
        assertEquals(3, MissiveNoticeList.step(BOARD, 0, 1));
        assertEquals(5, MissiveNoticeList.step(BOARD, 3, 1));
        assertEquals(8, MissiveNoticeList.step(BOARD, 8, 1));
        assertEquals(0, MissiveNoticeList.step(BOARD, 0, -1));
        assertEquals(3, MissiveNoticeList.step(BOARD, 5, -1));
        assertEquals("a pick the search left out walks from the first", 0,
                MissiveNoticeList.step(BOARD, 7, 1));
        assertEquals(-1, MissiveNoticeList.step(
                Collections.<MissiveNotice>emptyList(), 0, 1));
    }

    @Test
    public void aNoticesTimeLeftCountsDownFromWhenTheBoardSaidIt() {
        MissiveNotice notice = new MissiveNotice(0, 3000L, null);
        assertEquals(3000L, MissiveNoticeList.ticksLeft(notice, 100L, 100L));
        assertEquals(2000L, MissiveNoticeList.ticksLeft(notice, 100L, 1100L));
        assertEquals("never below none", 0L,
                MissiveNoticeList.ticksLeft(notice, 100L, 9100L));
        assertEquals("a clock gone back counts from the receipt", 3000L,
                MissiveNoticeList.ticksLeft(notice, 100L, 50L));
        assertEquals(MissiveNotice.STAYS_UP, MissiveNoticeList.ticksLeft(
                new MissiveNotice(0, MissiveNotice.STAYS_UP, null), 0L, 999L));
        assertEquals("a negative time is one that stays up",
                MissiveNotice.STAYS_UP,
                new MissiveNotice(0, -40L, null).getTicksLeft());
    }
}
