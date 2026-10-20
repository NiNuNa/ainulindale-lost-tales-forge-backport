package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.progression.ProgressionSnapshot;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.SkillStanding;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedSummary;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillCount;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The progression's packets read back what they wrote, and nothing else (PROG-13). */
public final class ProgressionPacketsTest {

    @Test
    public void theCatalogReadsBackItsSkillsAndTrees() {
        ProgressionCatalogPacket sent = new ProgressionCatalogPacket(
                Collections.singletonList(skill()), Collections.singletonList(new DeedSummary(
                        "losttales:greenway", "journeys", "minecraft:map",
                        DeedDefinition.Kind.PLACES, 5L, true, "", "")));
        ProgressionCatalogPacket read = new ProgressionCatalogPacket();
        read.fromBytes(bytes(sent));
        assertFalse(read.isMalformed());
        SkillDefinition skill = read.getSkills().get(0);
        assertEquals("losttales:archery", skill.getId());
        PerkDefinition perk = skill.perk("quick_nock");
        assertEquals(60, perk.levelFor(1));
        assertEquals("steady_draw", perk.getNeedsPerk());
        assertEquals(2, perk.getNeedsRank());
        assertEquals(3.0D, perk.param("seconds", 0.0D), 0.0D);
        assertTrue("what counts never travels", skill.getCounts().isEmpty());
        DeedSummary deed = read.getDeeds().get(0);
        assertEquals("losttales:greenway", deed.getId());
        assertEquals(DeedDefinition.Kind.PLACES, deed.getKind());
        assertEquals(5L, deed.getTarget());
        assertTrue(deed.hasTitle());
    }

    @Test
    public void theStateReadsBackWhole() {
        ProgressionState state = new ProgressionState();
        state.setSkill("losttales:archery", new SkillStanding(31, 12.5D));
        state.setCharacter(12, 40L);
        state.setPerkRank("losttales:archery", "steady_draw", 2);
        state.addDeed("losttales:greenway");
        state.setDeedCounter("losttales:troll_slayer", 7L);
        state.setResets(1);
        Map<String, Double> needed = new LinkedHashMap<String, Double>();
        needed.put("losttales:archery", Double.valueOf(420.0D));
        ProgressionStatePacket read = new ProgressionStatePacket();
        read.fromBytes(bytes(new ProgressionStatePacket(
                new ProgressionSnapshot(state, needed, 375L, 3))));
        assertFalse(read.isMalformed());
        ProgressionSnapshot snapshot = read.getSnapshot();
        assertEquals(31, snapshot.view().skill("losttales:archery").getLevel());
        assertEquals(420.0D, snapshot.skillNeeded("losttales:archery"), 0.0D);
        assertEquals(375L, snapshot.getCharacterNeeded());
        assertEquals(3, snapshot.getAvailablePoints());
        assertEquals(2, snapshot.view().perkRank("losttales:archery", "steady_draw"));
        assertTrue(snapshot.view().hasDeed("losttales:greenway"));
        assertEquals(7L, snapshot.view().deedCounter("losttales:troll_slayer"));
    }

    @Test
    public void aTruncatedOrLongerStateIsMalformed() {
        ByteBuf whole = bytes(new ProgressionStatePacket(new ProgressionSnapshot(
                new ProgressionState(), null, 100L, 0)));
        ByteBuf cut = whole.copy(0, whole.readableBytes() - 1);
        ProgressionStatePacket read = new ProgressionStatePacket();
        read.fromBytes(cut);
        assertTrue(read.isMalformed());
        ByteBuf longer = Unpooled.buffer();
        longer.writeBytes(whole.copy());
        longer.writeByte(1);
        read.fromBytes(longer);
        assertTrue(read.isMalformed());
        assertTrue(read.getSnapshot() == null);
    }

    @Test
    public void requestsReadBackAndAnUnknownActionIsMalformed() {
        ProgressionRequestPacket read = new ProgressionRequestPacket();
        read.fromBytes(bytes(ProgressionRequestPacket.takePerk("losttales:archery", "steady_draw")));
        assertFalse(read.isMalformed());
        read.fromBytes(bytes(ProgressionRequestPacket.resetAtStatue(10, 64, -20, "losttales:archery")));
        assertFalse(read.isMalformed());
        ByteBuf unknown = Unpooled.buffer();
        unknown.writeByte(7);
        read.fromBytes(unknown);
        assertTrue(read.isMalformed());
    }

    private static ByteBuf bytes(cpw.mods.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buf = Unpooled.buffer();
        message.toBytes(buf);
        return buf;
    }

    private static SkillDefinition skill() {
        Map<String, Double> params = new LinkedHashMap<String, Double>();
        params.put("seconds", Double.valueOf(3.0D));
        List<PerkDefinition> perks = new java.util.ArrayList<PerkDefinition>();
        perks.add(new PerkDefinition("steady_draw", new int[] { 10, 30, 50 }, "", 0,
                "draw_speed", 0.1D, null, null, true, "", ""));
        perks.add(new PerkDefinition("quick_nock", new int[] { 60 }, "steady_draw", 2,
                "quick_nock", 0.5D, params, null, true, "", ""));
        return new SkillDefinition("losttales:archery", "minecraft:bow", 1, "", "",
                Collections.<SkillCount>emptyList(), perks);
    }
}
