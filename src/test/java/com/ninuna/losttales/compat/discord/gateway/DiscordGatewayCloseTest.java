package com.ninuna.losttales.compat.discord.gateway;

import com.google.gson.JsonObject;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which closes from Discord keep the gateway down for the run: a wrong
 * token, a refused session, intents the application lacks. While it is
 * down the link command hands out no code, since no {@code /link} could
 * arrive to redeem it. Any other close is followed by a reconnect.
 */
public final class DiscordGatewayCloseTest {

    @Test
    public void aWrongTokenOrARefusedSessionClosesItForGood() {
        int[] fatal = {4004, 4010, 4011, 4012, 4013};
        for (int code : fatal) {
            assertTrue("close " + code,
                    DiscordGatewayClient.closesForGood(code, false));
            assertTrue("close " + code + " with the member intents",
                    DiscordGatewayClient.closesForGood(code, true));
        }
    }

    @Test
    public void refusedMemberIntentsAreDroppedWhileAMissingMessageIntentIsNot() {
        assertFalse("the member intents are dropped and it connects again",
                DiscordGatewayClient.closesForGood(4014, true));
        assertTrue("without them the message intent is what is missing",
                DiscordGatewayClient.closesForGood(4014, false));
    }

    @Test
    public void anyOtherCloseIsFollowedByAReconnect() {
        int[] others = {1000, 1006, 4000, 4001, 4007, 4008, 4009};
        for (int code : others) {
            assertFalse("close " + code,
                    DiscordGatewayClient.closesForGood(code, false));
        }
    }

    @Test
    public void aNewClientIsNotClosedForGood() {
        DiscordGatewayClient client = new DiscordGatewayClient("token", false,
                new DiscordGatewayClient.Listener() {
                    @Override
                    public String fetchGatewayUrl() throws IOException {
                        return "";
                    }

                    @Override
                    public void onReady(JsonObject ready) {}

                    @Override
                    public void onEvent(String name, JsonObject data) {}

                    @Override
                    public void onConnected() {}

                    @Override
                    public void onDisconnected() {}

                    @Override
                    public void onMembersRefused() {}
                });
        assertFalse(client.isClosedForGood());
    }
}
