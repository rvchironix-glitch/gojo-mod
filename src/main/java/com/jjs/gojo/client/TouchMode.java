package com.jjs.gojo.client;

import java.util.Locale;

/**
 * Decides whether the touch HUD is used instead of the PC HUD.
 *
 * Java Edition has no touchscreen API; on phones/tablets it runs through a launcher that emulates a
 * mouse and keyboard. Detection is therefore a best-effort check for well-known launcher environment
 * variables, and can always be overridden in Customize Controls (AUTO / ON / OFF).
 */
public final class TouchMode {
    private static Boolean detected;

    private TouchMode() {}

    public static boolean launcherDetected() {
        if (detected == null) {
            boolean found = false;
            try {
                for (String name : System.getenv().keySet()) {
                    String n = name.toUpperCase(Locale.ROOT);
                    if (n.startsWith("POJAV") || n.startsWith("FCL_") || n.startsWith("ZALITH")
                            || n.startsWith("MOBILEGLUES")) {
                        found = true;
                        break;
                    }
                }
            } catch (SecurityException ignored) {
                // treat as not detected
            }
            detected = found;
        }
        return detected;
    }

    public static boolean active() {
        int mode = HudLayout.get().touchMode;
        return mode == 1 || (mode == 0 && launcherDetected());
    }
}
