package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every skin the races of {@link CharacterRaceRegistry} offer, worked out
 * from their files (CHAR-38) and again whenever the list changes.
 *
 * <p>Most skins are LOTR Legacy's: Lost Tales keeps only ids and LOTR's
 * resource locations, and the textures stay inside the LOTR installation.
 * Skins drawn for Lost Tales ship under
 * {@code assets/losttales/textures/skins/}. Every race also offers the
 * player's own Minecraft account skin, first in its list, so a new
 * character lands on it.</p>
 */
public final class CharacterSkinRegistry {

    /** Prefix of every texture location that points into LOTR Legacy. */
    public static final String LOTR_TEXTURE_ROOT = CharacterRaceFiles.LOTR_TEXTURES;
    /** Prefix of every texture location bundled with Lost Tales. */
    public static final String BUNDLED_TEXTURE_ROOT = CharacterRaceFiles.OWN_TEXTURES;
    /**
     * The player's own Minecraft account skin on the Human race, drawn on
     * the Lost Tales player body. Every client fetches the texture for
     * itself; the placeholder location below is never loaded. Another
     * race's is this id followed by the race's.
     */
    public static final String ACCOUNT_SKIN_ID = "losttales:account_skin";
    public static final String ACCOUNT_SKIN_GROUP = "account";
    private static final String ACCOUNT_TEXTURE_PLACEHOLDER = "losttales:account_skin";
    private static final String OWN_NAMESPACE = "losttales";

    private static volatile Catalog catalog;

    private CharacterSkinRegistry() {}

    public static CharacterSkinDefinition get(String id) {
        return id == null ? null : current().byId.get(LostTalesIdentifiers.normalize(id));
    }

    /** The skins a race offers a body type, the account skin first; a stand-in offers its race's. */
    public static List<CharacterSkinDefinition> getCompatibleSkins(
            String raceId, String bodyTypeId) {
        String skinRace = skinRaceOf(raceId);
        List<CharacterSkinDefinition> raceSkins = current().byRace.get(skinRace);
        if (raceSkins == null || raceSkins.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<CharacterSkinDefinition> compatible =
                new ArrayList<CharacterSkinDefinition>();
        for (CharacterSkinDefinition definition : raceSkins) {
            if (definition.isCompatibleWith(skinRace, bodyTypeId)) {
                compatible.add(definition);
            }
        }
        return Collections.unmodifiableList(compatible);
    }

    public static boolean isCompatible(String skinId, String raceId, String bodyTypeId) {
        CharacterSkinDefinition definition = get(skinId);
        return definition != null && definition.isCompatibleWith(skinRaceOf(raceId), bodyTypeId);
    }

    /**
     * One of the race's catalogue skins, picked by {@code seed}, so the same
     * seed always gets the same skin. The account skin is never picked.
     */
    public static String getDefaultSkinId(String raceId, String bodyTypeId, UUID seed) {
        ArrayList<CharacterSkinDefinition> skins = new ArrayList<CharacterSkinDefinition>();
        for (CharacterSkinDefinition definition : getCompatibleSkins(raceId, bodyTypeId)) {
            if (!definition.isAccountSkin()) {
                skins.add(definition);
            }
        }
        if (skins.isEmpty()) {
            return "";
        }
        int hash = seed == null ? 0 : seed.hashCode();
        int index = (hash & Integer.MAX_VALUE) % skins.size();
        return skins.get(index).getId();
    }

    /**
     * The ids of the races in {@code races} with a skin whose id an earlier
     * race's skin has, so they can be left out of the list.
     */
    public static List<String> clashes(Collection<CharacterRaceDefinition> races) {
        List<String> clashing = new ArrayList<String>();
        Map<String, CharacterSkinDefinition> byId =
                new LinkedHashMap<String, CharacterSkinDefinition>();
        for (CharacterRaceDefinition race : races) {
            List<CharacterSkinDefinition> skins = skinsOf(race);
            boolean clash = false;
            for (CharacterSkinDefinition skin : skins) {
                if (byId.containsKey(skin.getId())) {
                    clash = true;
                }
            }
            if (clash) {
                clashing.add(race.getId());
                continue;
            }
            for (CharacterSkinDefinition skin : skins) {
                byId.put(skin.getId(), skin);
            }
        }
        return clashing;
    }

    /** The id of the account skin on a race. */
    public static String accountSkinIdOf(String raceId) {
        if (CharacterRaceRegistry.HUMAN.equals(raceId)) {
            return ACCOUNT_SKIN_ID;
        }
        int colon = raceId.indexOf(':');
        String namespace = colon < 0 ? OWN_NAMESPACE : raceId.substring(0, colon);
        String path = colon < 0 ? raceId : raceId.substring(colon + 1);
        return ACCOUNT_SKIN_ID + "_" + (OWN_NAMESPACE.equals(namespace)
                ? path : namespace + "_" + path);
    }

    /** The race whose skins a race wears: its own, or for a stand-in its body's race. */
    private static String skinRaceOf(String raceId) {
        String canonical = LostTalesIdentifiers.normalize(raceId);
        CharacterRaceDefinition race = CharacterRaceRegistry.get(canonical);
        return race != null && race.isStandIn() ? race.getStandInFor() : canonical;
    }

    private static Catalog current() {
        Catalog known = catalog;
        int revision = CharacterRaceRegistry.revision();
        if (known == null || known.revision != revision) {
            known = new Catalog(revision, CharacterRaceRegistry.getAll());
            catalog = known;
        }
        return known;
    }

    /** A race's skins: the account skin, then every picture of its entries, in order. */
    static List<CharacterSkinDefinition> skinsOf(CharacterRaceDefinition race) {
        List<CharacterSkinDefinition> skins = new ArrayList<CharacterSkinDefinition>();
        if (race.isStandIn()) {
            return skins;
        }
        CharacterBody body = race.getBody();
        skins.add(new CharacterSkinDefinition(accountSkinIdOf(race.getId()), race.getId(), "",
                ACCOUNT_SKIN_GROUP, "", 0, ACCOUNT_TEXTURE_PLACEHOLDER,
                body.getAccountSkinModelId(), CharacterSkinLayout.MINECRAFT_64X64, true));
        CharacterBodyModelDefinition model = CharacterBodyModelRegistry.get(body.getModelId());
        int colon = race.getId().indexOf(':');
        String namespace = colon < 0 ? OWN_NAMESPACE : race.getId().substring(0, colon);
        for (CharacterRaceSkins entry : race.getSkins()) {
            String bodyType = entry.getBodyTypeId();
            String bodyTypeSuffix = bodyType.length() == 0 ? "" : "_" + pathOf(bodyType);
            for (int index = 0; index < entry.getCount(); index++) {
                skins.add(new CharacterSkinDefinition(
                        namespace + ":" + entry.getGroup() + bodyTypeSuffix + "_" + index,
                        race.getId(), bodyType, entry.getGroup(), entry.getName(), index,
                        entry.getTexture() + "/" + index + ".png", body.getModelId(),
                        model.getLayout(), false));
            }
        }
        return skins;
    }

    private static String pathOf(String identifier) {
        int colon = identifier.indexOf(':');
        return colon < 0 ? identifier : identifier.substring(colon + 1);
    }

    /** The skins of one race list, by id and by race. */
    private static final class Catalog {
        private final int revision;
        private final Map<String, CharacterSkinDefinition> byId;
        private final Map<String, List<CharacterSkinDefinition>> byRace;

        private Catalog(int revision, Collection<CharacterRaceDefinition> races) {
            this.revision = revision;
            LinkedHashMap<String, CharacterSkinDefinition> ids =
                    new LinkedHashMap<String, CharacterSkinDefinition>();
            LinkedHashMap<String, List<CharacterSkinDefinition>> raceSkins =
                    new LinkedHashMap<String, List<CharacterSkinDefinition>>();
            for (CharacterRaceDefinition race : races) {
                List<CharacterSkinDefinition> own = new ArrayList<CharacterSkinDefinition>();
                for (CharacterSkinDefinition skin : skinsOf(race)) {
                    // A list is checked for clashes before it is put in place;
                    // the first race to take an id keeps it.
                    if (!ids.containsKey(skin.getId())) {
                        ids.put(skin.getId(), skin);
                        own.add(skin);
                    }
                }
                raceSkins.put(race.getId(), Collections.unmodifiableList(own));
            }
            this.byId = Collections.unmodifiableMap(ids);
            this.byRace = Collections.unmodifiableMap(raceSkins);
        }
    }
}
