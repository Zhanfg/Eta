package io.github.mangi.eta.agent.runtime

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.view.Display

/**
 * Non-invasive, no-account secondary-display capability check.
 *
 * Only creates a temporary own-content display. It never launches the target
 * app, reads its data, grabs input focus or touches Display 0. Success in this
 * probe is NOT sufficient for working cross-app GUI automation.
 */
internal object BackgroundDisplayCapabilityProbe {
    data class Result(
        val featureSupported: Boolean,
        val displayCreated: Boolean,
        val targetLaunchPermitted: Boolean,
        val code: String,
    ) {
        val canProceedToDeviceTest: Boolean
            get() = featureSupported && displayCreated && targetLaunchPermitted
    }

    fun inspect(context: Context, packageName: String): Result {
        val supported = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS
        )
        if (!supported) return Result(false, false, false, "NO_SECONDARY_DISPLAY_FEATURE")

        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return Result(true, false, false, "APP_NOT_INSTALLED_OR_NO_LAUNCHER")
        val displayManager = context.getSystemService(DisplayManager::class.java)
            ?: return Result(true, false, false, "DISPLAY_MANAGER_UNAVAILABLE")
        val activityManager = context.getSystemService(ActivityManager::class.java)
            ?: return Result(true, false, false, "ACTIVITY_MANAGER_UNAVAILABLE")

        var reader: ImageReader? = null
        var workspace: VirtualDisplay? = null
        return try {
            val imageReader = ImageReader.newInstance(
                720, 1280, PixelFormat.RGBA_8888, 2
            )
            reader = imageReader
            val density = context.resources.displayMetrics.densityDpi.coerceIn(160, 560)
            // A normal app must not pretend it owns a system display. The
            // secure/mirroring/public flags need privileges we do not request.
            workspace = displayManager.createVirtualDisplay(
                "ETA-Background-Check",
                720,
                1280,
                density,
                imageReader.surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            )
            val id = workspace?.display?.displayId
            if (id == null || id == Display.DEFAULT_DISPLAY || id < 0) {
                Result(true, false, false, "DISPLAY_CREATION_FAILED")
            } else {
                val allowed = activityManager.isActivityStartAllowedOnDisplay(
                    context, id, launchIntent
                )
                Result(
                    true,
                    true,
                    allowed,
                    if (allowed) "LAUNCH_POLICY_ALLOWED_NOT_TESTED" else "THIRD_PARTY_LAUNCH_RESTRICTED",
                )
            }
        } catch (_: SecurityException) {
            Result(true, workspace != null, false, "DISPLAY_PERMISSION_DENIED")
        } catch (_: RuntimeException) {
            Result(true, workspace != null, false, "DISPLAY_PROBE_FAILED")
        } finally {
            workspace?.release()
            reader?.close()
        }
    }
}
