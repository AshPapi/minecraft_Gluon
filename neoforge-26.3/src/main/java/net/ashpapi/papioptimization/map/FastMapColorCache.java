package net.ashpapi.papioptimization.map;

import net.minecraft.world.level.material.MapColor;

public final class FastMapColorCache {
    private static final int[] PACKED_COLOR_CACHE = new int[256];

    static {
        for (int i = 0; i < 256; i++) {
            PACKED_COLOR_CACHE[i] = MapColor.getColorFromPackedId(i);
        }
    }

    private FastMapColorCache() {}

    public static int getArgbColor(int packedId) {
        return PACKED_COLOR_CACHE[packedId & 0xFF];
    }

    public static boolean isTransparent(byte packedByte) {
        return (PACKED_COLOR_CACHE[packedByte & 0xFF] >>> 24) == 0;
    }
}
