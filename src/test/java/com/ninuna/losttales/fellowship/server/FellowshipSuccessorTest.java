package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;

/**
 * When a leader's character is deleted, the member who joined first among
 * those who may lead one fellowship more takes the lead; when none may, the
 * member who joined first still does, so the fellowship goes on.
 */
public final class FellowshipSuccessorTest {

    private static final FellowshipService.LeadLimits ONE_EACH =
            new FellowshipService.LeadLimits() {
                @Override
                public int of(FellowshipMember member) {
                    return 1;
                }
            };

    private final FellowshipMember aldric = member("Aldric", 1L, FellowshipColor.GREEN);
    private final FellowshipMember beren = member("Beren", 2L, FellowshipColor.BLUE);
    private final FellowshipMember celeb = member("Celeb", 3L, FellowshipColor.RED);

    @Test
    public void theFirstJoinedWhoMayLeadOneMoreTakesTheLead() {
        FellowshipWorldData data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        Fellowship grey = company(data);
        data.saveFellowship(Fellowship.createNew(UUID.randomUUID(), "Rangers", beren, 1L));

        assertEquals("Beren leads as many as he may, so Celeb follows",
                celeb.getIdentityId(), FellowshipService.successorOf(data, grey,
                        aldric.getIdentityId(), ONE_EACH));
    }

    @Test
    public void theFirstJoinedTakesTheLeadWhenNobodyMayLeadOneMore() {
        FellowshipWorldData data = new FellowshipWorldData(FellowshipWorldData.DATA_NAME);
        Fellowship grey = company(data);
        data.saveFellowship(Fellowship.createNew(UUID.randomUUID(), "Rangers", beren, 1L));
        data.saveFellowship(Fellowship.createNew(UUID.randomUUID(), "Riders", celeb, 1L));

        assertEquals(beren.getIdentityId(), FellowshipService.successorOf(data, grey,
                aldric.getIdentityId(), ONE_EACH));
    }

    private Fellowship company(FellowshipWorldData data) {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "The Grey Company",
                aldric, 1L);
        grey.addMember(beren);
        grey.addMember(celeb);
        data.saveFellowship(grey);
        return grey;
    }

    private static FellowshipMember member(String name, long joinedAt, FellowshipColor color) {
        return new FellowshipMember(UUID.randomUUID(), UUID.randomUUID(), name, joinedAt,
                color);
    }
}
