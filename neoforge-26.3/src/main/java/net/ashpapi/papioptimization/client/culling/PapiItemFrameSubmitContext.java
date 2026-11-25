package net.ashpapi.papioptimization.client.culling;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiItemFrameSubmitContext {

    private static boolean active;
    private static boolean farLod;
    private static boolean mediumLod;
    private static boolean solidBack;

    private PapiItemFrameSubmitContext() {}

    public static void begin(boolean isFarLod, boolean isMediumLod, boolean hasSolidBack) {
        active = true;
        farLod = isFarLod;
        mediumLod = isMediumLod;
        solidBack = hasSolidBack;
    }

    public static void end() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isFarLod() {
        return farLod;
    }

    public static boolean isMediumLod() {
        return mediumLod;
    }

    public static boolean isSolidBack() {
        return solidBack;
    }
}
