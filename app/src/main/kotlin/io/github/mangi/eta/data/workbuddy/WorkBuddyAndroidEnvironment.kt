package io.github.mangi.eta.data.workbuddy

import java.io.File

/** Android's Conscrypt roots moved to an APEX on Android 14+. Keep TLS verification enabled. */
internal object WorkBuddyAndroidEnvironment {
    fun configure(builder: ProcessBuilder, home: File, cache: File): ProcessBuilder {
        check(cache.isDirectory || cache.mkdirs()) { "无法准备本地核心缓存目录" }
        builder.environment().apply {
            put("HOME", home.absolutePath)
            put("TMPDIR", cache.absolutePath)
            put("SSL_CERT_DIR", "/apex/com.android.conscrypt/cacerts:/system/etc/security/cacerts")
        }
        return builder
    }
}
