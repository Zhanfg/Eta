package io.github.mangi.eta.workspace;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Build;
import android.view.Display;

import java.lang.reflect.Method;

/**
 * One-shot, local root-module feasibility probe. Creates no app tasks and
 * never injects input. The display is always released before process exit.
 *
 * This is deliberately not a production display daemon. Android vendor
 * builds may reject trusted display flags for the calling UID.
 */
public final class WorkspaceDisplayProbe {
    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;
    private static final int DENSITY = 240;

    // Android internal virtual-display policy bits, only for the temporary
    // privileged *feasibility* probe. If permission is missing, fail closed.
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_DESTROY_CONTENT = 1 << 8;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_OWN_FOCUS = 1 << 14;

    private WorkspaceDisplayProbe() {}

    private static Context systemContext() throws Exception {
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = threadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object thread = systemMain.invoke(null);
        Method getSystemContext = threadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        return (Context) getSystemContext.invoke(thread);
    }

    public static void main(String[] arguments) {
        // Keep probe explicitly opt-in, never activated as a boot side effect.
        if (arguments.length != 1 || !"--probe-only".equals(arguments[0])) {
            System.out.println("RESULT code=INVALID_ARGUMENT");
            return;
        }
        HandlerThread captureThread = null;
        ImageReader imageReader = null;
        VirtualDisplay display = null;
        try {
            final Context context = systemContext();
            final DisplayManager manager =
                    (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) {
                System.out.println("RESULT code=NO_DISPLAY_MANAGER");
                return;
            }
            captureThread = new HandlerThread("eta-display-probe-capture");
            captureThread.start();

            imageReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 3);
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image frame = reader.acquireLatestImage()) {
                    // Discard immediately. Never persist or transmit frames.
                } catch (Exception ignored) {
                    // No app content is ever scheduled on this probe display.
                }
            }, new Handler(captureThread.getLooper()));

            final int flags =
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION |
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                    FLAG_SUPPORTS_TOUCH |
                    FLAG_DESTROY_CONTENT |
                    FLAG_TRUSTED |
                    FLAG_OWN_DISPLAY_GROUP |
                    (Build.VERSION.SDK_INT >= 34 ? FLAG_OWN_FOCUS : 0);
            display = manager.createVirtualDisplay(
                    "ETA-Workspace-Feasibility",
                    WIDTH, HEIGHT, DENSITY, imageReader.getSurface(), flags
            );
            if (display == null || display.getDisplay() == null ||
                    display.getDisplay().getDisplayId() <= Display.DEFAULT_DISPLAY) {
                System.out.println("RESULT code=NO_SECONDARY_DISPLAY");
                return;
            }
            System.out.println("RESULT code=DISPLAY_CREATED id=" +
                    display.getDisplay().getDisplayId() + " trusted_flags_requested=true");
            // Keep the binder/display alive only long enough for a human to
            // inspect Android diagnostics; never launch any third-party app.
            Thread.sleep(1500);
        } catch (SecurityException denied) {
            System.out.println("RESULT code=DISPLAY_PERMISSION_DENIED");
        } catch (ReflectiveOperationException denied) {
            System.out.println("RESULT code=ANDROID_FRAMEWORK_UNAVAILABLE");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            System.out.println("RESULT code=INTERRUPTED");
        } catch (Exception failure) {
            System.out.println("RESULT code=DISPLAY_PROBE_FAILED type=" +
                    failure.getClass().getSimpleName());
        } finally {
            if (display != null) display.release();
            if (imageReader != null) imageReader.close();
            if (captureThread != null) captureThread.quitSafely();
            System.out.println("RESULT code=PROBE_CLEANED_UP");
            // app_process otherwise may remain alive due to Binder background
            // threads after this one-shot diagnostic returns.
            System.exit(0);
        }
    }
}
