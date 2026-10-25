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
        val endpoint = WorkBuddyEmbeddedCore.ensureStarted()
        // The core speaks the standard OpenAI Chat dialect. Deliberately clear
        // the local special-case source type; that code emits raw Tencent headers
        // and must never be applied to a loopback Go gateway.
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
        return OpenAiChatCompletionsProvider.complete(
            request.copy(config = configuration),
            runController,
            onEvent,
        )
    }
}
