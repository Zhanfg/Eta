package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.runtime.AgentRunController
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderSourceTypes
import io.github.mangi.eta.data.workbuddy.WorkBuddyAiBackend
import io.github.mangi.ai.runtime.AiSessionAction
import java.util.UUID
import org.json.JSONObject

/**
 * ETA Agent -> 127.0.0.1:ephemeral -> real workbuddy2api Go core -> CodeBuddy.
 *
 * All routing, account pool leases, token renewals, cooldowns and SSE come from
 * the pinned upstream Go core instead of the former one-account Kotlin proxy.
 */
internal object WorkBuddyEmbeddedProvider : AgentProviderClient {
    override val id: String = "workbuddy_embedded_go"
    override val capabilities = OpenAiChatCompletionsProvider.capabilities

    // WorkBuddy Go sticky pool recognizes prompt_cache_key. Preserve a single
    // account binding across SSE + subsequent tool-result continuation turns.
    // Hash the ETA-local session identifier instead of exposing it upstream.
    internal fun poolSessionKey(sessionId: String): String =
        UUID.nameUUIDFromBytes(("eta-workbuddy:" + sessionId).toByteArray(Charsets.UTF_8)).toString()

    override fun complete(
        request: ProviderRequest,
        runController: AgentRunController,
        onEvent: (ProviderEvent) -> Unit,
    ): ProviderResponse {
        runController.throwIfCancelled()
        return WorkBuddyAiBackend.withSession(AiSessionAction { endpoint ->
            // The portable backend lease covers the full response lifecycle,
            // including SSE and tool-call streaming. No cross-app auth export.
            val configuration = request.effectiveConfig.copy(
                providerId = "eta-wb-native-loopback",
                providerSourceType = ProviderSourceTypes.CUSTOM,
                baseUrl = endpoint.baseUrl().toString(),
                apiKey = endpoint.bearerToken(),
                openAiEndpointMode = OpenAiEndpointMode.CHAT_COMPLETIONS,
                authMode = "",
                extraBodyJson = JSONObject()
                    .put("prompt_cache_key", poolSessionKey(request.sessionId))
                    .toString(),
                customHeaders = emptyList(),
                customBody = emptyList(),
                hostedWebSearchEnabled = false,
            )
            OpenAiChatCompletionsProvider.complete(
                request.copy(config = configuration),
                runController,
                onEvent,
            )
        })
    }
}
