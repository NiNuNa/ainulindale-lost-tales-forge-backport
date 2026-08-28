# Discord Bridge

The server can link one Discord text channel to the in-game **Discord** chat channel, post short notices when it starts, stops, or a player joins or leaves, and keep the channel's topic saying whether it is online and how many players are on. Players need nothing installed beyond the mod; Discord users need nothing at all. Everything is set up by the server owner in about five minutes, in two halves: a **bot** that lets the server read the Discord channel (and write its topic), and a **webhook** that lets the server post into it. You can set up only one half if you want a one-way bridge.

The mod talks to Discord directly — no bot library, no plugin, no hybrid server.

## 1. Create the bot (Discord → game)

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**. Name it after your server (this name is what Discord shows on the bot).
2. Open the **Bot** tab and click **Reset Token**. Copy the token and keep it private — it is a password for the bot.
3. On the same tab, under **Privileged Gateway Intents**, switch on **Message Content Intent**. Without it Discord hands the server empty messages and nothing is relayed.
4. Open **OAuth2 → URL Generator**. Tick the scope `bot`, then the permissions **View Channels** and **Read Message History** — and **Manage Channels** if you want the channel topic to show the server's status. Open the generated link and invite the bot to your Discord server.
5. In Discord, enable **User Settings → Advanced → Developer Mode**, then right-click the text channel you want bridged and choose **Copy Channel ID**.

## 2. Create the webhook (game → Discord)

6. Open that channel's settings → **Integrations → Webhooks → New Webhook**. Chat lines carry the sender's own name and head instead of the webhook's; the server's own notices (started, shutting down, joined, left) show the webhook's name and picture, so name it after the server. Click **Copy Webhook URL** — also private.

## 3. Configure the server

Edit `config/losttales.cfg` **on the server** (it is created with defaults on the first start) and fill the `discord` block:

```
discord {
    B:enabled=true
    S:botToken=PASTE_THE_BOT_TOKEN
    S:channelId=PASTE_THE_CHANNEL_ID
    S:webhookUrl=PASTE_THE_WEBHOOK_URL
    I:pollIntervalSeconds=3
    B:relayGameChat=true
    B:relayDiscordChat=true
    S:avatarUrlTemplate=https://mc-heads.net/head/{name}/64
    B:serverEvents=true
    B:channelStatus=true
    I:channelStatusIntervalSeconds=300
}
```

| Setting | Meaning |
|---|---|
| `enabled` | Master switch. Off, and the Discord tab disappears for players. |
| `botToken`, `channelId` | Reading Discord. Leave empty for a game-to-Discord-only bridge. |
| `webhookUrl` | Posting to Discord. Leave empty for a Discord-to-game-only bridge. |
| `pollIntervalSeconds` | How often the channel is read (2–60). Three seconds is plenty. |
| `relayGameChat` / `relayDiscordChat` | Switch either direction off without removing the secrets. |
| `avatarUrlTemplate` | The picture a post carries: a URL with `{name}` (the sender's Minecraft account name) and/or `{uuid}` (their account id). The default is an isometric 3D head from a public head service; `https://mc-heads.net/avatar/{name}/64` gives the flat face instead, and empty shows the webhook's own picture. |
| `serverEvents` | Post `🟢 Server started`, `🔴 Server shutting down`, `**Name** joined the game` and `**Name** left the game` to the webhook. Needs `webhookUrl`. |
| `channelStatus` | Keep the channel topic at `Server online • 3/20 players` while up and `Server offline` after a clean shutdown. Needs `botToken` and `channelId`, and the bot needs **Manage Channels** in the channel. |
| `channelStatusIntervalSeconds` | The least time between two topic writes (60–3600). Discord lets a channel's topic change only twice per ten minutes, so the default of five minutes never waits on Discord; joins and leaves inside the interval are folded into the next write. |

Restart the server. The log should say:

```
[losttales] Discord bridge started (both ways, channel topic)
```

Players' clients need no configuration: the Discord tab appears because the server says the bridge is on.

## 4. Try it

- In the game, select the **Discord** tab and send a line. It appears in the Discord channel within a few seconds, under your account name and head.
- In Discord, write in the channel. It appears in the Discord tab for everyone online within one poll interval, as `<YourDiscordName> …`.
- Messages written in Discord *before* the server started are never replayed, and the webhook's own posts never come back into the game.
- Once the server has finished starting, `🟢 Server started` appears and the channel topic reads `Server online • 0/20 players`; join with a second account and the topic follows within the interval. Stop the server: `🔴 Server shutting down` and `Server offline` are sent before it exits.

## What is relayed, exactly

- **Only the Discord channel.** Global, Proximity, Faction, Party, Operator, Console and whispers never leave the game, so role-play stays in the game. Discord messages only ever enter the Discord channel, as account-identity lines — never as a character.
- **Game → Discord:** the text as typed — except that a canonical emoji shortcode with a Unicode form (`:flushed:`) is posted as the emoji Discord renders, while the mod’s own sprites (`:cutesy:`, `:discord:`, `:console:`, `:creeper:`) stay literal text — under the sender's name, with the head as avatar. Mentions are disabled on every post, so nobody in Discord can be pinged from the game.
- **Server notices:** one line each for the server having started (`FMLServerStartedEvent`), shutting down (`FMLServerStoppingEvent`), and a player joining or leaving, under the webhook's own name; player names are escaped for Discord's markdown. Nothing else — deaths, achievements, quests — is posted.
- **Channel topic:** recomputed from the live player list on the tick after a start, join or leave (so a burst of joins is one write) and written by the bridge's worker when it differs from what Discord last took and the interval has passed; the offline topic at shutdown skips the interval. A topic write that Discord rate-limits waits exactly as long as Discord asks without holding the chat relay; a refused one (no Manage Channels, a bad token) switches the topic off for the session with one log line. The offline topic and the farewell are best-effort: the server waits at most five seconds for them.
- **Discord → game:** `@mentions` and `#channels` are spelled out as text; a Unicode emoji or an alias shortcode the emoji registry knows becomes its canonical `:name:` and renders as the game’s own sprite, a custom emoji is matched by its name the same way (an unknown one stays its `:name:` text), and emoji the registry does not carry are dropped rather than shown as broken glyphs; line breaks become spaces, formatting codes are removed, and the message is cut to the chat's length. Messages from bots are ignored.

## If it does not work

| Log line | What to do |
|---|---|
| `Discord refused the bot (HTTP 401)` | The token is wrong or was reset. Paste it again. |
| `Discord refused the bot (HTTP 403)` | The bot is not in the server, cannot see the channel, or the Message Content intent is off. Reading stays off until the server restarts. |
| `Discord bridge failing, retrying with backoff: …` | A network or Discord problem; the bridge retries by itself and logs `Discord bridge recovered` when it is back. An HTTP 400 on a post usually means Discord rejected the sender's name (names containing "discord" or "clyde"). |
| `Discord bridge is enabled but has neither …` | `enabled` is on but both the bot fields and the webhook are empty. |
| `Discord channel topic updates are off until the server restarts: Discord refused it (HTTP 403)` | The bot lacks **Manage Channels** in that channel; grant it and restart. Chat relay is unaffected. |
| `Discord limited the channel topic write; retrying in … s` | Two topic edits within ten minutes — usually a restart right after a shutdown. The write happens when Discord allows it; raising `channelStatusIntervalSeconds` avoids it. |
| `Discord bridge did not finish its last posts within 5000 ms` | The farewell or offline topic could not be sent before the server gave up waiting; the topic will still say online until the next start corrects it. |
| A post shows the wrong skin | The head service looks the sender's account *name* up at Mojang. On an offline-mode server (a development client's `Player123` included) that name belongs to whoever registered it, so the picture is theirs; online-mode accounts resolve to their own skin. |

Keep the token and the webhook URL to the server's config file: never paste them into chat, a client's config, or a log you share.
