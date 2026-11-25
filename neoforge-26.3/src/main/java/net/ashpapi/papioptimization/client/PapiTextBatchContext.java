package net.ashpapi.papioptimization.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiTextBatchContext {

    private static int depth;
    private static int activeGeneration;
    private static int nextGeneration;

    private PapiTextBatchContext() {}

    public static int pushBuildGroup() {
        if (!RenderSystem.isOnRenderThread()) {
            return 0;
        }
        int prev = activeGeneration;
        int gen = ++nextGeneration;
        if (gen == 0) {
            gen = ++nextGeneration;
        }
        activeGeneration = gen;
        depth++;
        return prev;
    }

    public static void popBuildGroup(int prevGeneration) {
        if (!RenderSystem.isOnRenderThread()) {
            return;
        }
        if (depth > 0) {
            depth--;
            activeGeneration = depth == 0 ? 0 : prevGeneration;
        }
    }

    public static int currentGeneration() {
        return RenderSystem.isOnRenderThread() ? activeGeneration : 0;
    }
}
