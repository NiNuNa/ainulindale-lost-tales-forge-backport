package com.ninuna.losttales.command;

import com.ninuna.losttales.config.server.ServerConfigApplyResult;
import com.ninuna.losttales.config.server.ServerConfigChangeValidator;
import java.util.Arrays;
import java.util.Collections;
import net.minecraft.util.EnumChatFormatting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * What the config service made of a change reads in the sender's
 * language: the command's own lines, and the service's words (what
 * restarted, why a change was refused) among them.
 */
public final class LostTalesCommandConfigTest {

    @Test
    public void aResultIsReportedInTheLangFilesWords() {
        FakeCommandSender operator = FakeCommandSender.operator("Ops");
        LostTalesCommandConfig.report(operator, new ServerConfigApplyResult(
                Arrays.asList("chat.auditLog", "discord.enabled"),
                Collections.singletonList(new ServerConfigApplyResult.Refusal(
                        "chat.proximityRadius",
                        ServerConfigChangeValidator.REASON + "above_maximum", "512")),
                Arrays.asList(ServerConfigApplyResult.RESTARTED_DISCORD,
                        ServerConfigApplyResult.RESTARTED_CHAT_ACCESS), ""));
        assertEquals(Arrays.asList(
                "Applied chat.auditLog, discord.enabled; restarted Discord bridge, chat access.",
                "Refused chat.proximityRadius: above the maximum 512"), operator.told());
        assertEquals(EnumChatFormatting.GREEN,
                operator.heard().get(0).getChatStyle().getColor());
        assertEquals(EnumChatFormatting.RED,
                operator.heard().get(1).getChatStyle().getColor());
    }

    @Test
    public void aResultWithNothingRestartedOrRefusedOutrightReadsSo() {
        FakeCommandSender operator = FakeCommandSender.operator("Ops");
        LostTalesCommandConfig.report(operator, new ServerConfigApplyResult(
                Arrays.asList("chat.auditLog"), null, null, ""));
        LostTalesCommandConfig.report(operator,
                ServerConfigApplyResult.refusedOutright(ServerConfigApplyResult.NO_FILE));
        assertEquals(Arrays.asList("Applied chat.auditLog.",
                "The server has no config file loaded."), operator.told());
    }
}
