# Chat channels

Lost Tales provides five player-conversation channels while leaving commands, server messages, death messages, and other mods' chat components on Minecraft's normal paths.

- **All** is global in-character conversation.
- **Proximity** is in-character conversation delivered only to players in the sender's dimension and within the server's configured radius.
- **Party** is in-character conversation delivered only to online members of the sender's current Lost Tales party. It is unavailable without a valid party.
- **Faction** is in-character conversation delivered only to online characters whose configured faction matches the sender's active character. It is unavailable without a valid active-character faction.
- **OOC** is global out-of-character conversation and uses the Minecraft account name and account skin.

In-character messages snapshot the active character name and skin when the server accepts the message, so existing history does not change after a character switch. The current LOTR title and the exact color exposed by LOTR's faction explorer are snapshotted presentation details; missing data falls back to a clean account identity and skin.

Messages use the compact form `All: [HH:mm] <HEAD [Title] CharacterName> Message`. The head reserves its own space before the title or name. Clicking the displayed name fills the chat input with `/msg AccountName `, including for character channels where the visible role-play name differs from the Minecraft account name.

The selected channel appears beside the chat input as `[All]`, `[Party]`, `[Faction]`, `[Proximity]`, or `[OOC]`. Party and Faction are omitted when the active character cannot use them. With an empty input, `TAB` cycles available channels. With text or a command present, Minecraft's normal autocomplete remains active. Clicking the indicator opens a compact selector upward. Right-clicking a visible message copies only its message body and briefly confirms the action. Chat and the other Minecraft GUI screens use the same Lost Tales cursor as the LOTR map.

`chat.proximityRadius` is a server-authoritative gameplay setting. Timestamp visibility and the message, input-bar, and selector animation settings are client presentation preferences under the `client` category. The message animation combines vertical displacement with a short fade; the input bar enters with restrained overshoot. Disabling animation does not alter layout, routing, history, or command behavior.
