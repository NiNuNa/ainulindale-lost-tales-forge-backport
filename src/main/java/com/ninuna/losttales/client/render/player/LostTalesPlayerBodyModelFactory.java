package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeDefinition;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import net.minecraft.client.model.ModelBiped;

/**
 * Builds the configured renderer for one body model, skin layout, arm width
 * and chest type. This is the only place that knows which geometry stands
 * behind a body model identifier.
 */
final class LostTalesPlayerBodyModelFactory {

    private static final float OUTER_ARMOR_INFLATION = 1.0F;
    private static final float LEGGINGS_INFLATION = 0.5F;

    private LostTalesPlayerBodyModelFactory() {}

    /**
     * Returns null for an identifier this client cannot build. A body model
     * is built with its main model at inflation 0, the outer armor layer at
     * 1.0, and the leggings layer at 0.5, matching vanilla's RenderPlayer.
     * Armor keeps the body's proportions but always wide arms, as it is in
     * every Minecraft version with slim arms.
     */
    static LostTalesConfiguredPlayerRenderer create(
            CharacterBodyModelDefinition definition, CharacterSkinLayout layout,
            String bodyTypeId, String chestTypeId) {
        if (definition == null || layout == null) {
            return null;
        }
        PlayerBodyShape shape = PlayerBodyShape.forModelId(definition.getId());
        if (shape == null) {
            return null;
        }
        boolean slim = CharacterBodyTypeRegistry.SLIM.equals(bodyTypeId);
        CharacterChestTypeDefinition chest = CharacterChestTypeRegistry.get(chestTypeId);
        ModelBiped mainModel = new LostTalesPlayerModel(
                0.0F, slim, shape, layout, chest, false);
        ModelBiped chestArmorModel = new LostTalesPlayerModel(
                OUTER_ARMOR_INFLATION, false, shape, layout, null, true);
        ModelBiped armorModel = new LostTalesPlayerModel(
                LEGGINGS_INFLATION, false, shape, layout, null, true);
        return new LostTalesConfiguredPlayerRenderer(
                definition, bodyTypeId, chestTypeId, mainModel, chestArmorModel, armorModel);
    }
}
