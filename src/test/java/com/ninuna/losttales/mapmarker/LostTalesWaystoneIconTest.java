package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.gui.screen.waystone.WaystoneDraft;
import com.ninuna.losttales.network.packet.LostTalesMapMarkerSnapshotPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A waystone's icon is an icon's name, and every marker the store keeps
 * fits the snapshot that carries it to everyone who sees it: one owner's
 * icon of multi-byte letters must never stop the markers reaching anyone.
 */
public final class LostTalesWaystoneIconTest {

    @Test
    public void everyIconThePageOffersIsAnIconName() {
        for (String icon : WaystoneDraft.ICONS) {
            assertTrue(icon, LostTalesWaystoneSettingsService.isIconName(icon));
        }
        assertTrue(LostTalesWaystoneSettingsService.isIconName("fort"));
        assertTrue(LostTalesWaystoneSettingsService.isIconName("big-port"));
    }

    @Test
    public void anIconOfOtherLettersOrPastItsLengthIsRefused() {
        assertFalse(LostTalesWaystoneSettingsService.isIconName(""));
        assertFalse(LostTalesWaystoneSettingsService.isIconName(null));
        assertFalse(LostTalesWaystoneSettingsService.isIconName("fört"));
        assertFalse(LostTalesWaystoneSettingsService.isIconName("§cfort"));
        assertFalse(LostTalesWaystoneSettingsService.isIconName("big port"));
        assertFalse(LostTalesWaystoneSettingsService.isIconName("fort\n"));
        assertFalse(LostTalesWaystoneSettingsService.isIconName(
                repeat('a', LostTalesWaystoneSettingsService.MAX_ICON_LENGTH + 1)));
        assertTrue(LostTalesWaystoneSettingsService.isIconName(
                repeat('a', LostTalesWaystoneSettingsService.MAX_ICON_LENGTH)));
    }

    @Test
    public void aMarkerAtEveryBoundOfTheStoreInMultiByteLettersStillTravels() {
        String id = repeat('é', LostTalesMapMarkerRecord.MAX_ID_LENGTH);
        String name = repeat('ლ', LostTalesMapMarkerRecord.MAX_NAME_LENGTH);
        String text = repeat('ლ', LostTalesMapMarkerRecord.MAX_TEXT_LENGTH);
        LostTalesMapMarkerDefinition marker = new LostTalesMapMarkerDefinition(
                id, name, name, name, name, text, true, 0, 1.0D, 64.0D, 1.0D,
                128.0D, 8.0D, false, true, false,
                LostTalesMapMarkerSource.PLAYER_CREATED, false, "", 0, "");
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMapMarkerSnapshotPacket(Collections.singletonList(marker))
                .toBytes(buffer);
        LostTalesMapMarkerSnapshotPacket decoded =
                new LostTalesMapMarkerSnapshotPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        LostTalesMapMarkerDefinition read = decoded.getMarkers().get(0);
        assertEquals(id, read.getId());
        assertEquals(name, read.getIconName());
        assertEquals(name, read.getColorName());
        assertEquals(text, read.getDescription());
    }

    private static String repeat(char letter, int count) {
        StringBuilder text = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            text.append(letter);
        }
        return text.toString();
    }
}
