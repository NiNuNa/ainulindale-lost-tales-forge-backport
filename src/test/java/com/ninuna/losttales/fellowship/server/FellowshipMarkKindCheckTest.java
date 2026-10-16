package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.fellowship.storage.FellowshipMarkWorldData;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Moving or removing a fellowship's mark finds only a mark of that
 * fellowship: never another fellowship's, and never a go-here mark, which
 * only its owner moves or takes away.
 */
public final class FellowshipMarkKindCheckTest {

    private static final UUID FELLOWSHIP = new UUID(2L, 2L);
    private static final UUID OTHER_FELLOWSHIP = new UUID(3L, 3L);
    private static final UUID GUIDE = new UUID(4L, 4L);
    private static final UUID OWNER = new UUID(5L, 5L);

    @Test
    public void aMarkOfTheFellowshipIsFound() {
        FellowshipMarkWorldData data = store();
        FellowshipMark mark = FellowshipMark.ofFellowship(new UUID(9L, 9L), FELLOWSHIP,
                "Bree", GUIDE, 0, 1.0D, 2.0D, 3L);
        data.saveMark(mark);

        assertSame(mark, FellowshipService.fellowshipMarkOf(data, mark.getMarkId(), FELLOWSHIP));
    }

    @Test
    public void anotherFellowshipsMarkIsNotFound() {
        FellowshipMarkWorldData data = store();
        FellowshipMark mark = FellowshipMark.ofFellowship(new UUID(9L, 9L), OTHER_FELLOWSHIP,
                "Bree", GUIDE, 0, 1.0D, 2.0D, 3L);
        data.saveMark(mark);

        assertNull(FellowshipService.fellowshipMarkOf(data, mark.getMarkId(), FELLOWSHIP));
    }

    @Test
    public void aGoHereMarkIsNeverAFellowshipsMark() {
        FellowshipMarkWorldData data = store();
        FellowshipMark goHere = FellowshipMark.goHere(new UUID(7L, 7L), OWNER, 0,
                10.0D, 20.0D, 1L);
        data.saveMark(goHere);

        assertNull(FellowshipService.fellowshipMarkOf(data, goHere.getMarkId(), FELLOWSHIP));
        assertNull(FellowshipService.fellowshipMarkOf(data, goHere.getMarkId(), null));
    }

    @Test
    public void noMarkAndNoStoreFindNothing() {
        assertNull(FellowshipService.fellowshipMarkOf(store(), new UUID(1L, 1L), FELLOWSHIP));
        assertNull(FellowshipService.fellowshipMarkOf(null, new UUID(1L, 1L), FELLOWSHIP));
        assertNull(FellowshipService.fellowshipMarkOf(store(), null, FELLOWSHIP));
    }

    private static FellowshipMarkWorldData store() {
        return new FellowshipMarkWorldData(FellowshipMarkWorldData.DATA_NAME);
    }
}
