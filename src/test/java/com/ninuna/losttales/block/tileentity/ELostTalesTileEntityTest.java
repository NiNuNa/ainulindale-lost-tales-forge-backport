package com.ninuna.losttales.block.tileentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.LostTalesMetaData;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** The ids a world saves the mod's tile entities under (MOD-16). */
public final class ELostTalesTileEntityTest {

    /**
     * MOD-16 gave the four short ids the mod's prefix; the missive board's
     * id was not among them and stands as it is.
     */
    @Test
    public void everyTileEntityIdCarriesTheModPrefix() {
        for (ELostTalesTileEntity tileEntity : ELostTalesTileEntity.values()) {
            if (tileEntity == ELostTalesTileEntity.MISSIVE_BOARD) {
                assertEquals("missive_board", tileEntity.getId());
                continue;
            }
            assertTrue(tileEntity.getId(),
                    tileEntity.getId().startsWith(LostTalesMetaData.MOD_ID + "_"));
        }
    }

    @Test
    public void theVesselsLampsStatuesAndPlushiesSaveUnderTheirPrefixedIds() {
        assertEquals("losttales_pot", ELostTalesTileEntity.URN.getId());
        assertEquals("losttales_lamp", ELostTalesTileEntity.LAMP.getId());
        assertEquals("losttales_statue", ELostTalesTileEntity.STATUE.getId());
        assertEquals("losttales_plushie", ELostTalesTileEntity.PLUSHIE.getId());
    }

    @Test
    public void noTwoTileEntitiesShareAnId() {
        Set<String> ids = new HashSet<String>();
        for (ELostTalesTileEntity tileEntity : ELostTalesTileEntity.values()) {
            assertTrue(tileEntity.getId(), ids.add(tileEntity.getId()));
        }
    }
}
