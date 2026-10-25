package io.github.mangi.eta.agent.model

import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.model.ProviderTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkBuddyProtocolsTest {
    private fun configuration(mode: String, source: String = ProviderSourceTypes.WORKBUDDY) =
        AgentModelClient.ModelConfig(
            providerId = "builtin-workbuddy",
            providerName = "WorkBuddy Manager",
            providerType = ProviderTypes.OPENAI_COMPATIBLE,
            providerSourceType = source,
            baseUrl = "https://example.org/v1",
            apiKey = "fixture-not-live",
            model = "fixture",
            systemPrompt = "test",
            openAiEndpointMode = mode,
        )

    @Test fun responsesIsNativeDefaultModeForNewWorkBuddy() {
        val builtin = io.github.mangi.eta.data.provider.BuiltinProviders.providerById(
            io.github.mangi.eta.data.provider.BuiltinProviders.WORKBUDDY_ID
        ) as io.github.mangi.eta.data.model.OpenAiCompatibleProviderSetting
        assertEquals(OpenAiEndpointMode.RESPONSES, builtin.endpointMode)
    }

    @Test fun routesEachWorkBuddyProtocolIndependently() {
        assertSame(OpenAiResponsesProvider, ProviderClientFactory.getClient(configuration(OpenAiEndpointMode.RESPONSES)))
        assertSame(AnthropicMessagesProvider, ProviderClientFactory.getClient(configuration(OpenAiEndpointMode.MESSAGES)))
        assertSame(OpenAiChatCompletionsProvider, ProviderClientFactory.getClient(configuration(OpenAiEndpointMode.CHAT_COMPLETIONS)))
        assertSame(OpenAiChatCompletionsProvider, ProviderClientFactory.getClient(configuration(OpenAiEndpointMode.MESSAGES, ProviderSourceTypes.CUSTOM)))
    }

    @Test fun messagesRouteNormalizesSharedGatewayBase() {
        assertEquals("https://host.example/v1/messages", ProviderUrls.anthropicMessagesUrl("https://host.example/v1/"))
        assertEquals("https://host.example/proxy/v1/messages", ProviderUrls.anthropicMessagesUrl("https://host.example/proxy/v1"))
        assertEquals("https://api.anthropic.com/v1/messages", ProviderUrls.anthropicMessagesUrl("https://api.anthropic.com"))
    }
}
