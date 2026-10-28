package io.github.mangi.eta.data.workbuddy

import io.github.mangi.ai.runtime.AiBackend
import io.github.mangi.ai.runtime.AiEndpoint
import io.github.mangi.ai.runtime.AiSessionAction

/**
 * Reusable AI backend adapter. The same ai-runtime-api interface can be used
 * by KELIVO or another Android app with its own account vault/UI.
 *
 * The Go backend and its auth/refresh policy remain owned by the host process.
 * No raw credentials are exposed to other apps via Binder, Intent or files.
 */
internal object WorkBuddyAiBackend : AiBackend {
    override fun id(): String = "workbuddy.local"

    override fun isInstalled(): Boolean = WorkBuddyEmbeddedCore.isBundled()

    override fun <T> withSession(operation: AiSessionAction<T>): T =
        WorkBuddyEmbeddedCore.withActiveRequest { endpoint ->
            operation.run(AiEndpoint(endpoint.baseUrl, endpoint.apiKey))
        }
}
