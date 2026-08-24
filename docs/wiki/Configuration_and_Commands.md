# Configuration and commands

## Configuration files

Forge writes the main options to `config/losttales.cfg`. Its categories are:

- `client`: HUD visibility and placement, marker display, close-map terrain transition zooms, chat presentation, GUI animation and blur, quick-loot rows, and quest feedback;
- `quests`: prerequisites, rewards, permitted start sources, automatic marker discovery, and pinning;
- `missives`: board generation, notice counts, expiry, and deadlines;
- `characters`: starting-faction allow and deny lists, switching cooldowns, safety checks, snapshot size, checkpoint rate, and deletion retention;
- `combat_markers`: server tracking radius, update interval, grace time, logging, and party sharing;
- `party`: HUD linkage, status and tracking intervals, shared kill objectives, and sharing radius;
- `ranged_combat`: server charge-tier timing, damage, velocity, and knockback.

The optional camera uses `config/losttales-third-person.cfg`. Editable camera-preset JSON files are installed without overwriting user changes under `config/losttales/camera_presets/`.

Use the Forge Mods configuration screen for local display and camera settings. The HUD placement editor provides drag-and-drop positioning for all fixed Lost Tales panels; its values are the same bounded offsets stored in `losttales.cfg`. Chat windows appear in it as well, but their positions live with the rest of the chat layout in `config/losttales/chat_layout.txt`. Server owners should edit authoritative gameplay categories while the server is stopped, then review the generated comments and bounds.

Compatible in-world GUI screens use a restrained foreground fly-in; title and main-menu screens deliberately keep their normal presentation. In-world screens additionally fade a stationary black veil and centered Gaussian blur behind the foreground. Foreground duration, scale, easing, and direction are independently configurable, as are the background toggle, opacity, fade time, always-blur behavior, blur toggle, and blur strength. Lost Tales bottom control bars move as complete strips with a separate, slightly delayed upward entrance. Map legends, finders, editors, and confirmation panels use their own restrained popup entrance. Moving directly from one compatible GUI to another restarts foreground and control motion but preserves a settled in-world backdrop, preventing a second dark/blur fade. Reduced motion removes spatial movement and shortens the foreground transition. Shader or transformer failure falls back to the normal GUI background.

Inventory containers also provide optional smooth stack movement for Shift-click transfers and 1–9 hotbar swaps. Ordinary pickup and drag placement, creative search/repopulation, and bulk sorting stay immediate. `enableSmoothInventoryMovement` is the master toggle and `smoothInventoryAnimationDurationMillis` controls travel time; this is a client-side visual effect and does not modify container contents or server inventory authority.

### `discord` (server only)

`enabled`, `botToken`, `channelId`, `webhookUrl`, `pollIntervalSeconds` (2–60), `relayGameChat`, `relayDiscordChat`, `avatarUrlTemplate`, `serverEvents`, `channelStatus`, `channelStatusIntervalSeconds` (60–3600) — the server's own bridge between the Discord chat channel and one Discord text channel, its start/stop and join/leave notices, and the channel-topic status line; the setup guide is [Discord_Bridge](Discord_Bridge.md). The token and webhook URL are secrets and must never be shared with clients.

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
