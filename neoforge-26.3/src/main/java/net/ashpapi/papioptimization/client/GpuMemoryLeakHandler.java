package net.ashpapi.papioptimization.client;

import net.ashpapi.papioptimization.client.ebe.PapiEbeRuntime;
import net.ashpapi.papioptimization.client.ebe.PapiSodiumEbeBridge;
import net.ashpapi.papioptimization.client.map.PapiMapAtlasManager;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class GpuMemoryLeakHandler {

    private static final GpuMemoryLeakHandler INSTANCE = new GpuMemoryLeakHandler();

    public static GpuMemoryLeakHandler getInstance() {
        return INSTANCE;
    }

    private static void clearDebugChunkCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            DebugScreenOverlay debugOverlay = mc.getDebugOverlay();
            if (debugOverlay != null) {
                debugOverlay.clearChunkCache();
            }
        }
    }

    private static void cleanupCaches() {
        MapDecorationCache.clear();
        PapiMapAtlasManager.reset();
        PapiOcclusionEngine.clear();
        PapiEbeRuntime.clearCache();
        PapiQuadCanonicalizer.clear();
        PapiClientCaches.invalidateAll();
        PapiClientCaches.invalidateEncoderCache();
        clearDebugChunkCache();
    }

    private static void cleanupLevelCaches() {
        PapiSodiumEbeBridge.clearPending();
        PapiEbeRuntime.resetFrameContext();
        PapiOcclusionEngine.clear();
        MapDecorationCache.clear();
        clearDebugChunkCache();
    }

    public void onLevelChange() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(GpuMemoryLeakHandler::cleanupLevelCaches);
        } else {
            cleanupLevelCaches();
        }
    }

    public void onDisconnect() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(GpuMemoryLeakHandler::cleanupCaches);
        } else {
            cleanupCaches();
        }
    }
}
