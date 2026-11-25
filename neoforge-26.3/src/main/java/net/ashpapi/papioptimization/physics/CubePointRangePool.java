package net.ashpapi.papioptimization.physics;

import net.minecraft.world.phys.shapes.CubePointRange;

public final class CubePointRangePool {
    private static final int MAX_POOLED = 64;
    private static final CubePointRange[] POOL = new CubePointRange[MAX_POOLED + 1];

    static {
        for (int i = 1; i <= MAX_POOLED; i++) {
            POOL[i] = new CubePointRange(i);
        }
    }

    private CubePointRangePool() {
    }

    public static CubePointRange get(int parts) {
        if (parts >= 1 && parts <= MAX_POOLED) {
            return POOL[parts];
        }
        return new CubePointRange(parts);
    }
}
