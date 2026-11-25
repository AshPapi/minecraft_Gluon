package net.ashpapi.papioptimization.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiTextSortBypass {

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private PapiTextSortBypass() {}

    public static void enter() {
        DEPTH.get()[0]++;
    }

    public static void exit() {
        int[] holder = DEPTH.get();
        if (holder[0] > 0) {
            holder[0]--;
        }
    }

    public static boolean isActive() {
        return DEPTH.get()[0] > 0;
    }
}
