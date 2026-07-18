# Configuration and commands

## Configuration files

Forge writes the main options to `config/losttales.cfg`. Its categories are:

- `client`: HUD visibility, placement, marker display, quick-loot rows, and quest feedback;
- `quests`: prerequisites, rewards, permitted start sources, automatic marker discovery, and pinning;
- `missives`: board generation, notice counts, expiry, and deadlines;
- `characters`: starting-faction allow and deny lists, switching cooldowns, safety checks, snapshot size, checkpoint rate, and deletion retention;
- `combat_markers`: server tracking radius, update interval, grace time, logging, and party sharing;
- `party`: HUD linkage, status and tracking intervals, shared kill objectives, and sharing radius;
- `ranged_combat`: server charge-tier timing, damage, velocity, and knockback.

The optional camera uses `config/losttales-third-person.cfg`. Editable camera-preset JSON files are installed without overwriting user changes under `config/losttales/camera_presets/`.

Use the Forge Mods configuration screen for local display and camera settings. Server owners should edit authoritative gameplay categories while the server is stopped, then review the generated comments and bounds.

## Server commands

The root `/losttales` command requires permission level 2. Its subcommands are:

| Command | Purpose |
| --- | --- |
| `/losttales quest ...` | Inspect definitions and player progress; start, complete, reset, abandon, pin, issue starter content, or scan quest data. |
| `/losttales mapmarker ...` | Inspect known markers; discover, forget, track, or untrack them. |
| `/losttales summon ...` | Summon a registered entity with optional coordinates and NBT. |
| `/losttales party ...` | Inspect, validate, repair, or clear party combat state. |
| `/losttales character ...` | Inspect and recover switching data; reset cooldowns; freeze accounts; restore, roll back, or purge deleted characters; inspect lore ownership. |
| `/losttales hud ...` | Inspect or change local HUD presets, offsets, and toggles in an integrated server only. |

The HUD subcommand refuses to mutate configuration on a dedicated server because a server process cannot control remote client displays.

Lost Tales also registers LOTR's `/strscan` command for structure development. It is an operator tool, not normal gameplay.

Run `/losttales help` for the current subcommand list and use tab completion for supported actions. Old underscore-prefixed Lost Tales commands are no longer registered.

## Compatibility-sensitive settings

Do not change numeric biome or entity IDs by editing source in an existing pack. Do not rename registry entries or JSON identifiers without a migration. Character and quest size limits are safety bounds, not tuning values; lowering them below existing saved data can make that data read-only or invalid.

