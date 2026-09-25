package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.List;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A section-sign-coded string, as the hover cards draw it: runs in the
 * palette's tones, decorations kept, a reset back to ivory.
 */
public final class ChatLegacyRunsTest {

    @Test
    public void colourCodesBecomePaletteRunsAndDecorationsRide() {
        List<LostTalesChatVisualStyle.LegacyRun> runs =
                LostTalesChatVisualStyle.legacyRuns(
                        "§aTaking Inventory§r plain §o§7italic grey");
        assertEquals(3, runs.size());
        assertEquals("Taking Inventory", runs.get(0).text);
        assertEquals(LostTalesChatVisualStyle.paletteRgb(EnumChatFormatting.GREEN),
                runs.get(0).rgb);
        assertEquals(" plain ", runs.get(1).text);
        assertEquals(LostTalesUiInk.IVORY, runs.get(1).rgb);
        // A colour clears the decorations set before it, as vanilla does;
        // one set after it rides the run.
        assertEquals("italic grey", runs.get(2).text);
        assertEquals(LostTalesChatVisualStyle.paletteRgb(EnumChatFormatting.GRAY),
                runs.get(2).rgb);
        List<LostTalesChatVisualStyle.LegacyRun> styled =
                LostTalesChatVisualStyle.legacyRuns("§5§lSpecial§r done");
        assertEquals("§lSpecial", styled.get(0).text);
        assertEquals(LostTalesChatVisualStyle.paletteRgb(EnumChatFormatting.DARK_PURPLE),
                styled.get(0).rgb);
        assertEquals(" done", styled.get(1).text);
        assertEquals(LostTalesUiInk.IVORY, styled.get(1).rgb);
    }

    @Test
    public void oddCodesAndEmptyTextAreHarmless() {
        assertTrue(LostTalesChatVisualStyle.legacyRuns("").isEmpty());
        assertTrue(LostTalesChatVisualStyle.legacyRuns(null).isEmpty());
        // A trailing section sign is text; an unknown code is consumed.
        List<LostTalesChatVisualStyle.LegacyRun> runs =
                LostTalesChatVisualStyle.legacyRuns("end§");
        assertEquals(1, runs.size());
        assertEquals("end§", runs.get(0).text);
        runs = LostTalesChatVisualStyle.legacyRuns("a§zb");
        assertEquals(1, runs.size());
        assertEquals("ab", runs.get(0).text);
        // Only a colour change starts a new run.
        runs = LostTalesChatVisualStyle.legacyRuns("§c§cred");
        assertEquals(1, runs.size());
        assertEquals(LostTalesChatVisualStyle.paletteRgb(EnumChatFormatting.RED),
                runs.get(0).rgb);
    }
}
