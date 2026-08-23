package com.ninuna.losttales.compat.discord;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class DiscordAvatarUrlTest {

    @Test
    public void templatesFillNameAndUuidAndRefuseAnythingButHttps() {
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        assertEquals("https://mc-heads.net/avatar/Grey%20Wanderer/64",
                DiscordAvatarUrl.of("https://mc-heads.net/avatar/{name}/64",
                        " Grey Wanderer ", id));
        assertEquals("https://crafatar.com/avatars/12345678123412341234123456789abc",
                DiscordAvatarUrl.of("https://crafatar.com/avatars/{uuid}",
                        "Steve", id));
        assertEquals("", DiscordAvatarUrl.of("http://insecure/{name}", "Steve", id));
        assertEquals("", DiscordAvatarUrl.of("", "Steve", id));
        assertEquals("", DiscordAvatarUrl.of(null, "Steve", id));
        // An unknown placeholder is not a usable picture.
        assertEquals("", DiscordAvatarUrl.of("https://x/{skin}", "Steve", id));
        // No account id leaves the uuid empty rather than failing.
        assertEquals("https://x/", DiscordAvatarUrl.of("https://x/{uuid}",
                "Steve", null));
    }
}
