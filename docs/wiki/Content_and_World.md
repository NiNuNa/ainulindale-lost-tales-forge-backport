# Items, blocks, entities, and world content

Lost Tales registers 68 items, 11 blocks, four entities, four LOTR biomes, additional waypoints and roads, and one structure-spawner entry. Some of this content is experimental; see [Known Limitations](Known_Limitations.md) before using every registry entry in a released pack.

## Items

The item catalogue includes:

- light, heavy, and captain Arnor armor plus Arnor daggers, sword, longsword, halberd, pike, and spear;
- Orocarni armor and daggers, sword, battleaxe, mace, warhammer, halberd, pike, and spear;
- four Galadhrim helmet variants;
- one Geckolib-rendered Moon Elf light-armor set;
- Dáin's Hammer and the One Ring;
- a creation loot-respawner tool, test horn, and missive letter;
- plum, pear, baked pear, plum juice, and pear juice;
- community Lossoth and Moria Goblin weapons;
- one test spear retained under the stable registry name `test`.

Weapon subclasses register LOTR-compatible reach, swing speed, and knockback behavior. The pear has a furnace recipe for baked pear. Four Moria Goblin equipment conversions are added to the LOTR Gundabad crafting table.

## Blocks

The block catalogue contains a ceramic connected-texture tile; amphora, urn, and loutrophoros containers; a watch stone; a test lamp; a cheese wheel; bear, fox, and Gandalf plushies; and a missive board.

Urn registry names remain compatible with older saves. Urns have inventory and seal behavior and are supported by quick loot. The missive board provides the dynamic quest interface.

## Entities and spawning

The four registered entities are a Blue Goblin soldier stored under the legacy name `TestPerson`, Nia, an Odane man, and an Odane guard. Blue Goblins spawn in the LOTR Blue Mountains. Odane civilians and guards spawn in the two Odane biomes.

Nia is used by tutorial quest interaction. The Blue Goblin registry name is experimental but must not be renamed without a world migration because entity IDs and saved NBT are compatibility-sensitive.

## Biomes and structures

The added biomes are Moon Elf Vale, Moon Elf Mountains, Odane Island, and Odane Mountains. Their fixed numeric IDs are 200 through 203. The Odane biomes are painted into the LOTR world-generation map through a bundled overlay.

An Odane glowstone house can generate in the Odane biomes and is registered with LOTR's structure-spawner catalogue as ID 7000. The structure reads its own bundled scan resource and does not copy LOTR source or assets.

Moon Elf Vale includes custom cold terrain decoration and a very rare LOTR snow-troll spawn entry. Waypoints, roads, map regions, faction control zones, and spawn lists are registered during LOTR-compatible startup phases.

