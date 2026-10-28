package io.github.mangi.eta.agent.runtime

import android.net.Credentials
import android.net.LocalSocket
import android.net.LocalSocketAddress
import org.json.JSONObject
import java.io.IOException

/**
 * ETA -> local KSU/Magisk companion capability handshake.
 *
 * This is diagnostic only. It never returns a display lease and offers no
 * shell, app-launch, credential or GUI command.
 */
internal object AgentWorkspaceBridgeClient {
    private const val ADDRESS = "eta.workspace.bridge.v1"
    private const val MAX_RESPONSE_BYTES = 512
    private const val PROTOCOL = 1

    data class Status(
        val connected: Boolean,
        val displayBackendReady: Boolean = false,
        val maxDisplays: Int = 0,
        val code: String,
    )

    fun inspect(): Status = runCatching {
        val hello = request("HELLO 1")
        require(hello.optBoolean("ok") &&
            hello.optInt("protocol") == PROTOCOL &&
            hello.optString("service") == "eta-workspace-bridge") {
            "HANDSHAKE_INVALID"
        }
        val capabilities = request("CAPS")
        require(capabilities.optBoolean("ok") &&
            capabilities.optInt("protocol") == PROTOCOL) {
            "CAPABILITIES_INVALID"
        }
        val displayBackend = capabilities.optBoolean("display_backend")
        val max = capabilities.optInt("max_displays", 0)
        if (!displayBackend || max <= 0) {
            Status(connected = true, code = "BRIDGE_PROBE_ONLY")
        } else {
            // A capabilities claim is not a signed display lease. Do not
            // register a display until the future lease protocol verifies it.
            Status(connected = true, code = "BRIDGE_CAPABILITY_REPORTED_NOT_VERIFIED")
        }
    }.getOrElse { throwable ->
        Status(
            connected = false,
            code = if (throwable is SecurityException) "BRIDGE_PEER_UNTRUSTED"
                else if (throwable is IOException) "BRIDGE_UNAVAILABLE"
                else "BRIDGE_HANDSHAKE_FAILED",
        )
    }

    private fun request(command: String): JSONObject {
        require(command == "HELLO 1" || command == "CAPS")
        LocalSocket().use { socket ->
            socket.soTimeout = 2_000
            socket.connect(LocalSocketAddress(ADDRESS, LocalSocketAddress.Namespace.ABSTRACT))
            val identity: Credentials = socket.peerCredentials
            if (identity.uid != 0) throw SecurityException("Untrusted workspace companion UID")
            socket.outputStream.write((command + "\n").toByteArray(Charsets.US_ASCII))
            socket.outputStream.flush()
            val buffer = ByteArray(MAX_RESPONSE_BYTES)
            var count = 0
            while (count < buffer.size) {
                val read = socket.inputStream.read()
                if (read < 0) throw IOException("Bridge closed without response")
                if (read == 10) break
                buffer[count++] = read.toByte()
            }
            if (count == buffer.size) throw IOException("Bridge response too large")
            return JSONObject(String(buffer, 0, count, Charsets.UTF_8))
        }
    }
}
