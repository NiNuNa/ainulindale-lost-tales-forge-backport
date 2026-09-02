package com.ninuna.losttales.client.render.player;

import com.ninuna.losttales.character.registry.CharacterBodyModelDefinition;
import com.ninuna.losttales.character.registry.CharacterBodyModelRegistry;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinDefinition;
import com.ninuna.losttales.character.registry.CharacterSkinLayout;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.client.character.ClientCharacterAppearanceCache;
import com.ninuna.losttales.client.skin.LostTalesAccountSkins;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a player's synchronized character appearance into the model, texture,
 * and scale the renderer draws with. A player without a character is a plain
 * human on the Lost Tales player body, wearing the account skin.
 *
 * The answer is cached per player and recomputed only when the appearance
 * cache hands out a different {@link CharacterAppearance} instance, which is
 * what every sync packet, preview push, and preview pop does, or when the
 * account skin behind an entry is replaced, which happens once the local
 * player's profile textures arrive. Render code therefore pays a map lookup
 * or two per frame and never rebuilds keys or resource locations. Client
 * render thread only.
 */
public final class PlayerAppearanceResolver {

    private static final Map<UUID, Entry> RESOLVED = new HashMap<UUID, Entry>();
    private static final Map<String, ResourceLocation> TEXTURES =
            new HashMap<String, ResourceLocation>();

    private PlayerAppearanceResolver() {}

    /**
     * The resolved appearance, or null for an entity that is not a client
     * player and therefore has no skin to draw.
     */
    public static ResolvedPlayerAppearance resolve(EntityPlayer player) {
        UUID playerId = player == null ? null : player.getUniqueID();
        if (playerId == null) {
            return null;
        }
        CharacterAppearance appearance = ClientCharacterAppearanceCache.get(playerId);
        Entry entry = RESOLVED.get(playerId);
        boolean rebuild = entry == null || entry.source != appearance;
        LostTalesAccountSkins.AccountSkin skin = null;
        if (rebuild || entry.skin != null) {
            // A rebuild may need the account skin; an entry drawn with one
            // asks again so a swapped-in skin replaces the entry that used
            // the old one. Entries drawn with a catalogue skin pay nothing.
            skin = accountSkin(player);
            rebuild = rebuild || entry.skin != skin;
        }
        if (rebuild) {
            ResolvedPlayerAppearance resolved = appearance == null
                    ? resolveDefault(skin)
                    : resolve(playerId, appearance);
            if (resolved == null && appearance != null) {
                // A character the client cannot draw falls back to the same
                // look a player without a character has.
                resolved = resolveDefault(skin);
            } else if (resolved != null && resolved.usesAccountSkin()
                    && skin != null) {
                resolved = resolved.withTexture(skin.getTexture());
            }
            entry = new Entry(appearance, resolved,
                    resolved != null && resolved.usesAccountSkin() ? skin : null);
            RESOLVED.put(playerId, entry);
        }
        return entry.resolved;
    }

    private static LostTalesAccountSkins.AccountSkin accountSkin(EntityPlayer player) {
        return player instanceof AbstractClientPlayer
                ? LostTalesAccountSkins.resolve((AbstractClientPlayer)player)
                : null;
    }

    /**
     * Pure resolution of a character appearance; null when it cannot be
     * drawn. An account skin leaves the texture null for the caller that
     * has the player at hand.
     */
    static ResolvedPlayerAppearance resolve(UUID playerId, CharacterAppearance appearance) {
        if (appearance == null || !appearance.isPresent()) {
            return null;
        }
        String raceId = appearance.getRaceId();
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return null;
        }

        CharacterSkinDefinition skin = resolveSkin(playerId, appearance);
        String modelId = skin == null
                ? CharacterBodyModelRegistry.getDefaultModelId(raceId)
                : skin.getModelId();
        CharacterBodyModelDefinition model = CharacterBodyModelRegistry.get(modelId);
        if (model == null) {
            return null;
        }

        String chestTypeId = model.hasChestVariant()
                ? CharacterChestTypeRegistry.normalizeOrNone(appearance.getChestTypeId())
                : CharacterChestTypeRegistry.NONE;
        String bodyTypeId = model.supportsBodyTypes()
                ? CharacterBodyTypeRegistry.normalizeOrWide(appearance.getBodyTypeId())
                : "";
        boolean accountSkin = skin != null && skin.isAccountSkin();
        ResourceLocation texture = skin == null || accountSkin ? null : textureFor(skin);
        CharacterSkinLayout layout = skin == null ? model.getLayout() : skin.getLayout();
        return new ResolvedPlayerAppearance(
                raceId, model.getId(), layout, bodyTypeId, chestTypeId,
                race.getRendererScale(), accountSkin, texture);
    }

    /** A plain human on the Lost Tales body with the account's skin and arm width. */
    private static ResolvedPlayerAppearance resolveDefault(
            LostTalesAccountSkins.AccountSkin skin) {
        if (skin == null) {
            return null;
        }
        return new ResolvedPlayerAppearance(
                CharacterRaceRegistry.HUMAN,
                CharacterBodyModelRegistry.LOSTTALES_PLAYER,
                CharacterSkinLayout.MINECRAFT_64X64,
                skin.getBodyTypeId(),
                CharacterChestTypeRegistry.NONE,
                1.0F,
                true,
                skin.getTexture());
    }

    public static void clear() {
        RESOLVED.clear();
    }

    /**
     * The selected skin when it is still valid for the character, otherwise
     * the race's deterministic fallback so an old record keeps one look.
     */
    private static CharacterSkinDefinition resolveSkin(
            UUID playerId, CharacterAppearance appearance) {
        CharacterSkinDefinition selected = CharacterSkinRegistry.get(appearance.getSkinId());
        if (selected != null && selected.isCompatibleWith(
                appearance.getRaceId(), appearance.getGenderId())) {
            return selected;
        }
        String fallbackId = CharacterSkinRegistry.getDefaultSkinId(
                appearance.getRaceId(), appearance.getGenderId(), playerId);
        return CharacterSkinRegistry.get(fallbackId);
    }

    private static ResourceLocation textureFor(CharacterSkinDefinition skin) {
        ResourceLocation texture = TEXTURES.get(skin.getId());
        if (texture == null) {
            texture = new ResourceLocation(skin.getTextureLocation());
            TEXTURES.put(skin.getId(), texture);
        }
        return texture;
    }

    private static final class Entry {
        private final CharacterAppearance source;
        private final ResolvedPlayerAppearance resolved;
        /** The account skin the entry was drawn with; null when it used none. */
        private final LostTalesAccountSkins.AccountSkin skin;

        private Entry(CharacterAppearance source, ResolvedPlayerAppearance resolved,
                      LostTalesAccountSkins.AccountSkin skin) {
            this.source = source;
            this.resolved = resolved;
            this.skin = skin;
        }
    }
}
