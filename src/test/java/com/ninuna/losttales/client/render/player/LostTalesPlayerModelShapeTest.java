package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterChestTypeDefinition;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The shapes must carry the geometry the catalogue skins were painted for:
 * mirrored left limbs on LOTR layouts, the (0,32) hair cuboid, the chest
 * types, elf ears, orc nose and ears, hobbit feet and lowered pivots, the
 * half-troll's own boxes. Armor variants keep proportions only.
 */
public final class LostTalesPlayerModelShapeTest {

    private static final CharacterSkinLayout LOTR = CharacterSkinLayout.LOTR_64X64;
    private static final CharacterSkinLayout CLASSIC = CharacterSkinLayout.LOTR_64X32;
    private static final CharacterSkinLayout MINECRAFT = CharacterSkinLayout.MINECRAFT_64X64;
    private static final CharacterChestTypeDefinition ROUNDED =
            CharacterChestTypeRegistry.get(CharacterChestTypeRegistry.ROUNDED_MEDIUM);
    private static final CharacterChestTypeDefinition FULL =
            CharacterChestTypeRegistry.get(CharacterChestTypeRegistry.FULL_LARGE);
    private static final CharacterChestTypeDefinition CLASSIC_CHEST =
            CharacterChestTypeRegistry.get(CharacterChestTypeRegistry.CLASSIC);
    private static final CharacterChestTypeDefinition NONE =
            CharacterChestTypeRegistry.get(CharacterChestTypeRegistry.NONE);

    @Test
    public void lotrLayoutMirrorsLeftLimbsAndHasNoOverlays() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HUMAN, LOTR, null, false);
        assertEquals(64.0F, model.textureHeight, 0.0F);
        assertTrue(model.bipedLeftArm.mirror);
        assertTrue(model.bipedLeftLeg.mirror);
        assertNull(model.bipedRightArm.childModels);
        assertNull(model.bipedLeftLeg.childModels);
        // Hair and beard cuboid: 8 wide, 16 tall, hanging below the chin.
        ModelBox hat = box(model.bipedHeadwear);
        assertEquals(16.0F, hat.posY2 - hat.posY1, 0.0F);
        assertEquals(-8.0F, hat.posY1, 0.0F);
    }

    @Test
    public void raceShapeOnMinecraftLayoutKeepsOverlaysAndVanillaHat() {
        LostTalesPlayerModel elf = new LostTalesPlayerModel(
                0.0F, true, PlayerBodyShape.ELF, MINECRAFT, null, false);
        assertFalse(elf.bipedLeftArm.mirror);
        assertEquals(1, elf.bipedRightLeg.childModels.size());
        assertEquals(8.0F, box(elf.bipedHeadwear).posY2 - box(elf.bipedHeadwear).posY1, 0.0F);
        assertEquals(2, elf.bipedHead.childModels.size());
        assertEquals(3.0F, width(elf.bipedLeftArm), 0.0F);
    }

    @Test
    public void slimLotrBodyNarrowsBothMirroredArms() {
        LostTalesPlayerModel model = new LostTalesPlayerModel(
                0.0F, true, PlayerBodyShape.HUMAN, LOTR, null, false);
        assertEquals(3.0F, width(model.bipedRightArm), 0.0F);
        assertEquals(3.0F, width(model.bipedLeftArm), 0.0F);
        assertTrue(model.bipedLeftArm.mirror);
        assertEquals(2.0F, model.bipedLeftArm.rotationPointY, 0.0F);
    }

    @Test
    public void chestTypesBuildTheirOwnGeometry() {
        LostTalesPlayerModel rounded = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HUMAN, LOTR, ROUNDED, false);
        assertTrue(rounded.hasChest());
        assertEquals(CharacterChestTypeRegistry.Shape.ROUNDED, rounded.getChestShape());
        assertFalse(rounded.hasChestOverlay());
        // Drawn apart from the body, which keeps no children on a LOTR layout.
        assertNull(rounded.bipedBody.childModels);

        LostTalesPlayerModel roundedMinecraft = new LostTalesPlayerModel(
                0.0F, true, PlayerBodyShape.PLAYER, MINECRAFT, ROUNDED, false);
        assertTrue(roundedMinecraft.hasChestOverlay());
        assertEquals(1, roundedMinecraft.bipedBody.childModels.size());

        LostTalesPlayerModel full = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.ELF, MINECRAFT, FULL, false);
        assertEquals(CharacterChestTypeRegistry.Shape.FULL, full.getChestShape());
        assertTrue(full.hasChestOverlay());

        LostTalesPlayerModel classic = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HUMAN, LOTR, CLASSIC_CHEST, false);
        assertEquals(CharacterChestTypeRegistry.Shape.CLASSIC, classic.getChestShape());
        ModelRenderer slab = (ModelRenderer)classic.bipedBody.childModels.get(0);
        assertEquals(6.0F, width(slab), 0.0F);
        assertEquals(-4.0F, box(slab).posZ1, 0.0F);

        LostTalesPlayerModel none = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HUMAN, LOTR, NONE, false);
        assertFalse(none.hasChest());
        assertNull(none.bipedBody.childModels);

        LostTalesPlayerModel armor = new LostTalesPlayerModel(
                1.0F, false, PlayerBodyShape.HUMAN, LOTR, ROUNDED, true);
        assertFalse(armor.hasChest());
        LostTalesPlayerModel troll = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HALF_TROLL, LOTR, ROUNDED, false);
        assertFalse(troll.hasChest());
    }

    @Test
    public void elfEarsHangOffTheHead() {
        LostTalesPlayerModel elf = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.ELF, LOTR, null, false);
        assertNotNull(elf.bipedHead.childModels);
        assertEquals(2, elf.bipedHead.childModels.size());
        ModelRenderer right = (ModelRenderer)elf.bipedHead.childModels.get(0);
        ModelRenderer left = (ModelRenderer)elf.bipedHead.childModels.get(1);
        assertEquals(1.0F, width(right), 0.0F);
        assertEquals(-right.rotateAngleZ, left.rotateAngleZ, 0.0001F);
        assertTrue(left.mirror);
        assertNull(new LostTalesPlayerModel(0.0F, false, PlayerBodyShape.HUMAN, LOTR, null, false)
                .bipedHead.childModels);
    }

    @Test
    public void orcReadsTheClassicLayoutWithNoseAndEars() {
        LostTalesPlayerModel orc = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.ORC, CLASSIC, null, false);
        assertEquals(32.0F, orc.textureHeight, 0.0F);
        assertTrue(orc.bipedLeftArm.mirror);
        assertEquals(3, orc.bipedHead.childModels.size());
        ModelRenderer nose = (ModelRenderer)orc.bipedHead.childModels.get(0);
        assertEquals(1.0F, width(nose), 0.0F);
        assertEquals(-4.8F, box(nose).posZ1, 0.0001F);
        ModelRenderer earRight = (ModelRenderer)orc.bipedHead.childModels.get(1);
        ModelRenderer earLeft = (ModelRenderer)orc.bipedHead.childModels.get(2);
        assertEquals(-earRight.rotateAngleY, earLeft.rotateAngleY, 0.0001F);
        assertEquals(earRight.rotateAngleX, earLeft.rotateAngleX, 0.0001F);
        // The classic hat stays: 8 rows.
        assertEquals(8.0F, box(orc.bipedHeadwear).posY2 - box(orc.bipedHeadwear).posY1, 0.0F);
        // Armor: no features, classic layout.
        LostTalesPlayerModel armor = new LostTalesPlayerModel(
                1.0F, false, PlayerBodyShape.ORC, CLASSIC, null, true);
        assertNull(armor.bipedHead.childModels);
        assertEquals(32.0F, armor.textureHeight, 0.0F);
    }

    @Test
    public void halfTrollKeepsLotrsBoxesAndPivots() {
        LostTalesPlayerModel troll = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HALF_TROLL, LOTR, null, false);
        assertEquals(64.0F, troll.textureHeight, 0.0F);
        assertEquals(10.0F, width(troll.bipedHead), 0.0F);
        assertEquals(2, troll.bipedHead.cubeList.size());
        assertEquals(-8.0F, troll.bipedHead.rotationPointY, 0.0F);
        assertEquals(-8.0F, troll.bipedBody.rotationPointY, 0.0F);
        assertEquals(12.0F, width(troll.bipedBody), 0.0F);
        assertEquals(-8.5F, troll.bipedRightArm.rotationPointX, 0.0001F);
        assertEquals(8.5F, troll.bipedLeftArm.rotationPointX, 0.0001F);
        assertEquals(-6.0F, troll.bipedRightArm.rotationPointY, 0.0F);
        assertEquals(2, troll.bipedRightArm.cubeList.size());
        assertEquals(6.0F, width(troll.bipedRightArm), 0.0F);
        assertEquals(8.0F, troll.bipedRightLeg.rotationPointY, 0.0F);
        assertEquals(-3.2F, troll.bipedRightLeg.rotationPointX, 0.0001F);
        assertEquals(6.0F, width(troll.bipedRightLeg), 0.0F);
        assertTrue(troll.bipedHeadwear.isHidden);
        // Nose, tusks, two ears, mohawk, four horn pieces.
        assertEquals(9, troll.bipedHead.childModels.size());

        // Vanilla's pose rewrites every pivot; the troll's come back.
        troll.isSneak = true;
        troll.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, null);
        assertEquals(-7.0F, troll.bipedHead.rotationPointY, 0.0F);
        assertEquals(5.0F, troll.bipedRightLeg.rotationPointY, 0.0F);
        assertEquals(4.0F, troll.bipedRightLeg.rotationPointZ, 0.0F);
        assertEquals(-8.5F, troll.bipedRightArm.rotationPointX, 0.0001F);
        assertEquals(-6.0F, troll.bipedLeftArm.rotationPointY, 0.0F);

        LostTalesPlayerModel slim = new LostTalesPlayerModel(
                0.0F, true, PlayerBodyShape.HALF_TROLL, LOTR, null, false);
        assertEquals(5.0F, width(slim.bipedRightArm), 0.0F);
        assertEquals(-6.0F, slim.bipedRightArm.rotationPointY, 0.0F);

        // Armor is painted for the same 64x64 boxes, without the head features.
        LostTalesPlayerModel armor = new LostTalesPlayerModel(
                1.0F, false, PlayerBodyShape.HALF_TROLL, LOTR, null, true);
        assertEquals(64.0F, armor.textureHeight, 0.0F);
        assertEquals(10.0F, width(armor.bipedHead), 0.0F);
        assertNull(armor.bipedHead.childModels);
        assertTrue(armor.bipedHeadwear.isHidden);
    }

    @Test
    public void hobbitLowersTheHeadAndLimbsAndGrowsFeet() {
        LostTalesPlayerModel hobbit = new LostTalesPlayerModel(
                0.0F, false, PlayerBodyShape.HOBBIT, LOTR, null, false);
        assertEquals(4.0F, hobbit.bipedHead.rotationPointY, 0.0F);
        assertEquals(4.0F, hobbit.bipedHeadwear.rotationPointY, 0.0F);
        assertEquals(4.8F, hobbit.bipedBody.rotationPointY, 0.0001F);
        assertEquals(6.8F, hobbit.bipedRightArm.rotationPointY, 0.0001F);
        assertEquals(16.8F, hobbit.bipedLeftLeg.rotationPointY, 0.0001F);
        assertEquals(12.0F, box(hobbit.bipedHeadwear).posY2 - box(hobbit.bipedHeadwear).posY1,
                0.0F);
        ModelRenderer foot = (ModelRenderer)hobbit.bipedRightLeg.childModels.get(0);
        assertEquals(4.0F, width(foot), 0.0F);
        assertEquals(10.0F, box(foot).posY1, 0.0F);
        assertEquals(-((ModelRenderer)hobbit.bipedLeftLeg.childModels.get(0)).rotateAngleY,
                foot.rotateAngleY, 0.0001F);

        // The pivots survive a pose, which vanilla rewrites every frame.
        hobbit.isSneak = true;
        hobbit.setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0625F, null);
        assertEquals(5.0F, hobbit.bipedHead.rotationPointY, 0.0F);
        assertEquals(13.8F, hobbit.bipedRightLeg.rotationPointY, 0.0001F);
        assertEquals(6.8F, hobbit.bipedLeftArm.rotationPointY, 0.0001F);
        assertEquals(-5.0F, hobbit.bipedRightArm.rotationPointX, 0.0001F);
    }

    @Test
    public void armorVariantsKeepProportionsOnly() {
        LostTalesPlayerModel armor = new LostTalesPlayerModel(
                1.0F, false, PlayerBodyShape.HOBBIT, LOTR, ROUNDED, true);
        assertTrue(armor.isArmorTexture());
        assertEquals(32.0F, armor.textureHeight, 0.0F);
        assertFalse(armor.hasChest());
        assertNull(armor.bipedBody.childModels);
        assertNull(armor.bipedRightLeg.childModels);
        assertEquals(4.8F, armor.bipedBody.rotationPointY, 0.0001F);
        // Classic hat: eight rows, not the hair cuboid.
        assertEquals(8.0F, box(armor.bipedHeadwear).posY2 - box(armor.bipedHeadwear).posY1,
                0.0F);
        LostTalesPlayerModel elfArmor = new LostTalesPlayerModel(
                0.5F, false, PlayerBodyShape.ELF, LOTR, null, true);
        assertNull(elfArmor.bipedHead.childModels);
    }

    @Test
    public void everyModelIdMapsToAShape() {
        assertEquals(PlayerBodyShape.PLAYER, PlayerBodyShape.forModelId("losttales:player"));
        assertEquals(PlayerBodyShape.HUMAN, PlayerBodyShape.forModelId("lotr:human"));
        assertEquals(PlayerBodyShape.ELF, PlayerBodyShape.forModelId("lotr:elf"));
        assertEquals(PlayerBodyShape.DWARF, PlayerBodyShape.forModelId("lotr:dwarf"));
        assertEquals(PlayerBodyShape.HOBBIT, PlayerBodyShape.forModelId("lotr:hobbit"));
        assertEquals(PlayerBodyShape.ORC, PlayerBodyShape.forModelId("lotr:orc"));
        assertEquals(PlayerBodyShape.ORC, PlayerBodyShape.forModelId("lotr:uruk"));
        assertEquals(PlayerBodyShape.HALF_TROLL, PlayerBodyShape.forModelId("lotr:half_troll"));
        assertNull(PlayerBodyShape.forModelId("lotr:balrog"));
    }

    private static float width(ModelRenderer part) {
        ModelBox box = box(part);
        return box.posX2 - box.posX1;
    }

    private static ModelBox box(ModelRenderer part) {
        return (ModelBox)part.cubeList.get(0);
    }
}
