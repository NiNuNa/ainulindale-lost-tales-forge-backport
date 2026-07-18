# Quests and missives

Press **J** by default to open the quest journal. Quest state is owned and validated by the server and stored separately for each active roleplay character.

## Quest model

Bundled JSON definitions can contain:

- a stable ID, title, description, and repeatable flag;
- journal, item, interaction, any-source, or locked start mode;
- prerequisites and rewards;
- one or more ordered stages;
- required or optional objectives;
- journal log text and giver, objective, or turn-in marker hints.

Implemented objective types are **gather**, **craft**, **kill**, and **go to**. Gather and craft selectors support explicit item registry names and Forge OreDictionary names. Kill selectors support explicit entity IDs and legacy groups such as hostile, passive, player, and living.

The server validates quest starts, objective events, stage advancement, completion, failure, abandonment, deadlines, and rewards. The client receives a bounded snapshot for display but cannot award itself progress or items.

## Bundled content

The current index contains 14 quests:

- seven one-time Survivalist path quests started by items;
- three tutorial quests involving the starter note, Nia, and a cheese cache;
- two repeatable missive templates for gathering gold ore and killing orcs;
- one repeatable blacksmith crafting test;
- one general test quest.

The test and tutorial content is development content and may not form a complete progression path. Definitions are bundled inside the jar because Minecraft 1.7.10 has no datapack system or runtime data reload.

## Tracking and feedback

Players can keep multiple quests active and pin selected quests for the HUD. The journal shows active, completed, and failed entries. Quest updates can use chat messages, sounds, HUD notifications, world markers, and the LOTR map. Marker discovery and automatic pinning are configurable.

Party members can share eligible kill progress when both the server setting and proximity check allow it. This applies to Lost Tales objectives only; unsupported external or LOTR quest types keep their original behavior.

## Missive boards

Missive boards generate a bounded set of notices over time. A notice can be read and accepted as a missive letter. Generated definitions are stored with the world and synchronized to the relevant client.

Configuration controls notice counts, generation frequency, expiry, and optional deadlines. Expired notices and timed quests are resolved by the server clock. Malformed or oversized dynamic quest data is rejected instead of partially loaded.

## Administration

`/losttales quest` provides operator tools for listing definitions and progress, starting, completing, resetting, abandoning, or pinning quests, issuing starter content, and scanning definitions. These commands require the root command's permission level 2.

