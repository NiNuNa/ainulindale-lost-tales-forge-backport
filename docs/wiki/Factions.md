# Factions

Lost Tales extends LOTR Legacy's faction enum and related catalogues during startup. Active additions receive map information, relations, control zones, ranks, achievements, titles, and starting-character eligibility through the LOTR systems.

## Active additions

| Faction | LOTR category | Region |
| --- | --- | --- |
| Lossoth | Men | North |
| Moria Goblins | Orcs | West |
| Orocarni | Dwarves | East |
| Tharbad | Men | West |
| Moon Elves | Elves and Free Peoples | North |
| Sun Elves | Elves and Free Peoples | East |
| Odane | Men | East |
| Blue Goblins | Orcs | West |

Arnor, the Lost Tales Lothlórien entry, and the internal Neutral entry are currently inactive. They remain registered for source and save compatibility but are removed from normal player-facing LOTR faction lists and do not provide active progression.

The active factions use the normal LOTR alignment and pledge model. Lost Tales does not add a separate faction currency, shop, role hierarchy, election, government, player-created faction, or territorial-conquest system.

## Relations and territory

Selected default relations are registered between new factions and LOTR factions. Control zones use Lost Tales or public LOTR waypoints. Lossoth is anchored on LOTR's own Cape of Forochel — it previously used a Lost Tales waypoint of its own, which sat out in the Sundering Seas — while Orocarni keeps its dedicated waypoint; both use a 175-block zone radius. The Lossoth fast-travel region stays registered even though nothing places a waypoint in it, because a character who has already unlocked it has that name saved and the state adapter rejects a region it cannot resolve. The Moon Elf map label is centered on its two northern waypoints.

Orocarni progression uses Red Mountain Dwarf titles from Stonebearer through Lord or Lady of the Red Mountains. The map regions, relations, ranks, and zones are startup integration data and should be tested whenever the LOTR dependency changes.

## Content integration

The mod also adds faction-themed equipment, NPC spawns, map waypoints, roads, and biomes. Four Moria Goblin equipment conversions are appended to the LOTR Gundabad crafting table. Lost Tales does not call LOTR's global recipe initializer a second time, which avoids duplicate legacy recipes.
