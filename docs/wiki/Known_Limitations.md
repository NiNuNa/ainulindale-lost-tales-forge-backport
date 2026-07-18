# Known limitations

These issues were verified during the project-wide source and resource audit and were not changed because they need assets, gameplay direction, or a save-migration decision.

## Content completeness

- The lore catalogue contains 82 definitions, but only Frodo, Gandalf, and Éomer have complete appearance data and are claimable.
- Several registered items do not have a complete normal inventory icon or worn armor texture. This affects parts of the Orocarni weapon set, all Galadhrim helmets, Moon Elf inventory icons, Dáin's Hammer, and the test spear. Removing the items would break registry compatibility; inventing replacement art is outside a code cleanup.
- `HORN_TEST`, the item registered as `test`, `LAMP_TEST`, the Blue Goblin entity registered as `TestPerson`, test quests, and a test map marker are still present. Treat them as experimental. Stable saved identifiers should not be removed without migration.
- The bundled quest set demonstrates the framework but is not a complete RPG campaign.

## World and LOTR integration

- Biome IDs 200–203 and structure ID 7000 are fixed in source. A pack using the same numeric IDs can conflict. Changing them requires a world migration or a deliberate configuration design.
- Legacy tile-entity IDs such as `pot`, `statue`, `lamp`, `plushie`, and `missive_board` are not namespaced. They are retained for saved-world compatibility.
- Orocarni and Lossoth control zones currently point to the Sun Elves waypoint. The intended replacement waypoint is unclear.
- Some Orocarni rank translations appear inherited from goblin-themed content and need a writing/lore decision.
- Factions, ranks, regions, roads, waypoints, structures, and map behavior depend on reflection or enum injection into LOTR Legacy. This build is pinned to Update v36.15.
- The world-generation overlay is a fixed bundled image and is not configurable or hot-reloadable.

## Runtime and testing

- The coremod targets exact Minecraft and LOTR bytecode shapes. A transformer mismatch falls back where possible, but the related feature may be disabled or visually incorrect.
- Minecraft 1.7.10 has no datapacks. Quest, lore, and map-marker definitions are loaded from the jar at startup.
- The project uses Java 8, Gradle 4.6, ForgeGradle 1.2, and local deobfuscated dependency jars. The toolchain is intentionally old and should be isolated from modern Gradle projects.
- Automated tests do not yet include a real two-client dedicated-server session. A staging-server smoke test remains recommended before release.

## Planned systems that do not exist yet

Classes, skill trees, governments, subfactions, player-created factions, and a general powers-and-abilities framework are not implemented. Documentation pages with those names are status pages, not promises of current gameplay.

