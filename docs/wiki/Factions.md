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

Selected default relations are registered between new factions and LOTR factions. Control zones use Lost Tales or public LOTR waypoints. The map regions, relations, ranks, and zones are startup integration data and should be tested whenever the LOTR dependency changes.

Two existing zone assignments require a gameplay decision before they can be changed safely: the Orocarni and Lossoth control zones currently point at the Sun Elves waypoint. Changing them could alter alignment and territory behavior in existing worlds, so the cleanup pass leaves them unchanged and records the issue in [Known Limitations](Known_Limitations.md).

## Content integration

The mod also adds faction-themed equipment, NPC spawns, map waypoints, roads, and biomes. Four Moria Goblin equipment conversions are appended to the LOTR Gundabad crafting table. Lost Tales does not call LOTR's global recipe initializer a second time, which avoids duplicate legacy recipes.

