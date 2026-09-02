package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import net.minecraft.util.ResourceLocation;

import java.util.Locale;

/**
 * Everything the player renderer needs to draw one player, derived once
 * from the synchronized character appearance (or, without a character, from
 * the account) by {@link PlayerAppearanceResolver}.
 *
 * Nothing here is gameplay state. Race is kept only for render decisions
 * that are still keyed by race, such as the cape attachment transform.
 */
public final class ResolvedPlayerAppearance {

    private static final String KEY_SEPARATOR = "|";

    private final String raceId;
    private final String modelId;
    private final CharacterSkinLayout layout;
    private final String bodyTypeId;
    private final String chestTypeId;
    private final float rendererScale;
    private final boolean accountSkin;
    private final ResourceLocation texture;
    private final String rendererKey;

    ResolvedPlayerAppearance(String raceId, String modelId, CharacterSkinLayout layout,
                             String bodyTypeId, String chestTypeId, float rendererScale,
                             boolean accountSkin, ResourceLocation texture) {
        if (raceId == null || raceId.length() == 0) {
            throw new IllegalArgumentException("raceId must not be blank");
        }
        if (modelId == null || modelId.length() == 0) {
            throw new IllegalArgumentException("modelId must not be blank");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        this.raceId = raceId;
        this.modelId = modelId;
        this.layout = layout;
        this.bodyTypeId = bodyTypeId == null ? "" : bodyTypeId;
        this.chestTypeId = CharacterChestTypeRegistry.normalizeOrNone(chestTypeId);
        this.rendererScale = rendererScale > 0.0F ? rendererScale : 1.0F;
        this.accountSkin = accountSkin;
        this.texture = texture;
        this.rendererKey = rendererKey(modelId, layout, this.bodyTypeId, this.chestTypeId);
    }

    public String getRaceId() {
        return this.raceId;
    }

    public String getModelId() {
        return this.modelId;
    }

    /** The layout of the texture the body reads. */
    public CharacterSkinLayout getLayout() {
        return this.layout;
    }

    /** Arm width of the body; empty for a model with only one. */
    public String getBodyTypeId() {
        return this.bodyTypeId;
    }

    /** The chest type drawn; {@link CharacterChestTypeRegistry#NONE} when none. */
    public String getChestTypeId() {
        return this.chestTypeId;
    }

    /** True when a feminine chest is drawn. */
    public boolean hasChest() {
        return !CharacterChestTypeRegistry.NONE.equals(this.chestTypeId);
    }

    /** Uniform scale applied to the whole body before it is drawn. */
    public float getRendererScale() {
        return this.rendererScale;
    }

    /**
     * True when the skin is the player's own account skin, which only the
     * client that draws the player can look up.
     */
    public boolean usesAccountSkin() {
        return this.accountSkin;
    }

    /** The skin texture, or null to keep the player's normal Minecraft skin. */
    public ResourceLocation getTexture() {
        return this.texture;
    }

    /** Identifies the configured renderer that draws this appearance. */
    public String getRendererKey() {
        return this.rendererKey;
    }

    /** The same appearance drawn with another texture. */
    ResolvedPlayerAppearance withTexture(ResourceLocation replacement) {
        return new ResolvedPlayerAppearance(this.raceId, this.modelId, this.layout,
                this.bodyTypeId, this.chestTypeId, this.rendererScale, this.accountSkin,
                replacement);
    }

    static String rendererKey(String modelId, CharacterSkinLayout layout,
                              String bodyTypeId, String chestTypeId) {
        String key = modelId + KEY_SEPARATOR + layout.name().toLowerCase(Locale.ROOT)
                + KEY_SEPARATOR + CharacterChestTypeRegistry.normalizeOrNone(chestTypeId);
        return bodyTypeId == null || bodyTypeId.length() == 0
                ? key : key + KEY_SEPARATOR + bodyTypeId;
    }
}
