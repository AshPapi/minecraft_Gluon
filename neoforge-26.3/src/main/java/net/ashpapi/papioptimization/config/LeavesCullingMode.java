package net.ashpapi.papioptimization.config;

import java.util.Locale;

public enum LeavesCullingMode {
    DEFAULT,
    FAST,
    VERTICAL,
    STATE,
    CHECK,
    DEPTH;

    public static LeavesCullingMode fromString(String raw, LeavesCullingMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT);
        for (LeavesCullingMode mode : values()) {
            if (mode.name().equals(norm)) {
                return mode;
            }
        }
        return null;
    }
}
