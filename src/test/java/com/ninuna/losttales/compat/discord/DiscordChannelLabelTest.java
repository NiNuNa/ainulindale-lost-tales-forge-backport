package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiscordChannelLabelTest {

    @Test
    public void bridgeableChannelsAreMarkedAndNamed() {
        assertEquals("\uD83C\uDF0D Global", DiscordChannelLabel.of(ChatChannel.ALL, ""));
        assertEquals("\uD83D\uDCAC OOC", DiscordChannelLabel.of(ChatChannel.OOC, "Gondor"));
        assertEquals("\uD83D\uDD17 Discord", DiscordChannelLabel.of(ChatChannel.DISCORD, null));
        for (ChatChannel channel : ChatChannel.values()) {
            assertEquals("a mark on exactly the channels that may be bridged: "
                    + channel, channel.isBridgeable(),
                    DiscordChannelLabel.markOf(channel).length() > 0);
        }
    }

    @Test
    public void factionChatIsNamedAfterTheFaction() {
        assertEquals("\uD83D\uDEE1\uFE0F Gondor",
                DiscordChannelLabel.of(ChatChannel.FACTION, " Gondor "));
        assertEquals("\uD83D\uDEE1\uFE0F Faction",
                DiscordChannelLabel.of(ChatChannel.FACTION, ""));
        assertEquals("\uD83D\uDEE1\uFE0F Faction",
                DiscordChannelLabel.of(ChatChannel.FACTION, null));
    }

    @Test
    public void privateChannelsKeepTheirPlainName() {
        assertEquals("Party", DiscordChannelLabel.of(ChatChannel.PARTY, ""));
        assertEquals("", DiscordChannelLabel.of(null, ""));
        assertTrue(DiscordChannelLabel.markOf(ChatChannel.WHISPER).isEmpty());
    }
}
