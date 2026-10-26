package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.workbuddy.WorkBuddyEmbeddedCore

/**
 * ETA Agent -> 127.0.0.1:ephemeral -> real workbuddy2api Go core -> CodeBuddy.
 *
 * All routing, account pool leases, token renewals, cooldowns and SSE come from
 * the pinned upstream Go core instead of the former one-account Kotlin proxy.
 */
internal object WorkBuddyEmbeddedProvider : AgentProviderClient {
    override val id: String = "workbuddy_embedded_go"
    override val capabilities = OpenAiChatCompletionsProvider.capabilities

    override fun complete(
        request: ProviderRequest,
        runController: AgentRunController,
        onEvent: (ProviderEvent) -> Unit,
    ): ProviderResponse {
        runController.throwIfCancelled()
        return WorkBuddyEmbeddedCore.withActiveRequest { endpoint ->
            // Core is kept alive until streaming/tools terminate or the run is
            // cancelled. All downstream requests use the Go pool's real key.
            val configuration = request.effectiveConfig.copy(
                providerId = "eta-wb-native-loopback",
                providerSourceType = ProviderSourceTypes.CUSTOM,
                baseUrl = endpoint.baseUrl,
                apiKey = endpoint.apiKey,
                openAiEndpointMode = OpenAiEndpointMode.CHAT_COMPLETIONS,
                authMode = "",
                extraBodyJson = "",
                customHeaders = emptyList(),
                customBody = emptyList(),
                hostedWebSearchEnabled = false,
            )
            OpenAiChatCompletionsProvider.complete(
                request.copy(config = configuration),
                runController,
                onEvent,
            )
        }
    }
}
