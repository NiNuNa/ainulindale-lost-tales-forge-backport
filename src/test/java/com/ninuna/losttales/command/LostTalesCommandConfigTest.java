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
                Arrays.asList("chat.typingIndicators", "discord.enabled"),
                Collections.singletonList(new ServerConfigApplyResult.Refusal(
                        "chat.proximityRadius",
                        ServerConfigChangeValidator.REASON + "above_maximum", "512")),
                Arrays.asList(ServerConfigApplyResult.RESTARTED_DISCORD,
                        ServerConfigApplyResult.RESTARTED_CHAT_ACCESS), ""));
        assertEquals(Arrays.asList(
                "Applied chat.typingIndicators, discord.enabled; restarted Discord bridge, chat access.",
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
                Arrays.asList("chat.typingIndicators"), null, null, ""));
        LostTalesCommandConfig.report(operator,
                ServerConfigApplyResult.refusedOutright(ServerConfigApplyResult.NO_FILE));
        assertEquals(Arrays.asList("Applied chat.typingIndicators.",
                "The server has no config file loaded."), operator.told());
    }

    /**
     * An option only the next start puts in force is never called
     * applied: the answer says it was saved and waits (DATA-12).
     */
    @Test
    public void anOptionForTheNextStartIsSavedNotApplied() {
        FakeCommandSender operator = FakeCommandSender.operator("Ops");
        LostTalesCommandConfig.report(operator, new ServerConfigApplyResult(
                Arrays.asList("waystones.enableWaystoneRecipe"), null, null, ""));
        LostTalesCommandConfig.report(operator, new ServerConfigApplyResult(
                Arrays.asList("chat.typingIndicators", "chat.historyPersisted"), null,
                Arrays.asList(ServerConfigApplyResult.RESTARTED_CHAT_ACCESS), ""));
        LostTalesCommandConfig.report(operator, new ServerConfigApplyResult(
                Arrays.asList("chat.auditLog"), null,
                Arrays.asList(ServerConfigApplyResult.RESTARTED_CHAT_ACCESS), ""));
        assertEquals(Arrays.asList(
                "Saved waystones.enableWaystoneRecipe; this waits for the next start.",
                "Applied chat.typingIndicators; restarted chat access.",
                "Saved chat.historyPersisted; this waits for the next start.",
                "Saved chat.auditLog; this waits for the next start. Restarted chat access."),
                operator.told());
        assertEquals(EnumChatFormatting.YELLOW,
                operator.heard().get(0).getChatStyle().getColor());
    }
}
