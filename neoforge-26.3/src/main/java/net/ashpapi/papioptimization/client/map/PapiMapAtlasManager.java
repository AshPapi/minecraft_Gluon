package net.ashpapi.papioptimization.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.ashpapi.papioptimization.access.PapiMapRenderState;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public final class PapiMapAtlasManager {

    private static final int MAX_ATLAS_TEXTURE_SIZE = 4096;
    private static final int MAX_ATLAS_COUNT = 16;

    private static final List<PapiMapAtlas> ATLASES = new ArrayList<>(4);
    private static final Int2IntOpenHashMap MAP_SLOTS = new Int2IntOpenHashMap(256);

    static {
        MAP_SLOTS.defaultReturnValue(-1);
    }

    private PapiMapAtlasManager() {}

    public static int getPackedSlot(int mapId) {
        return RenderSystem.isOnRenderThread() ? MAP_SLOTS.get(mapId) : -1;
    }

    public static void uploadSlot(int mapId, NativeImage pixels) {
        if (!RenderSystem.isOnRenderThread() || !PapiConfig.mapAtlas() || pixels.isClosed()) {
            return;
        }
        int packed = MAP_SLOTS.get(mapId);
        if (packed == -1) {
            packed = allocateSlot(mapId);
            if (packed == -1) {
                return;
            }
        }
        int atlasIdx = packed >>> 16;
        if (atlasIdx >= 0 && atlasIdx < ATLASES.size()) {
            PapiMapAtlas atlas = ATLASES.get(atlasIdx);
            atlas.uploadSlot((packed >>> 8) & 0xFF, packed & 0xFF, pixels);
        }
    }

    public static void populateRenderState(int mapId, PapiMapRenderState renderState) {
        if (!RenderSystem.isOnRenderThread() || !PapiConfig.mapAtlas()) {
            renderState.papi$setAtlasRegion(null, 0.0F, 0.0F, 1.0F, 1.0F);
            return;
        }
        int packed = MAP_SLOTS.get(mapId);
        if (packed == -1) {
            renderState.papi$setAtlasRegion(null, 0.0F, 0.0F, 1.0F, 1.0F);
            return;
        }
        int atlasIdx = packed >>> 16;
        if (atlasIdx < 0 || atlasIdx >= ATLASES.size()) {
            renderState.papi$setAtlasRegion(null, 0.0F, 0.0F, 1.0F, 1.0F);
            return;
        }
        PapiMapAtlas atlas = ATLASES.get(atlasIdx);
        int gx = (packed >>> 8) & 0xFF;
        int gy = packed & 0xFF;
        float step = (float) PapiMapAtlas.MAP_SIZE * atlas.invSize();
        float u0 = (float) gx * step;
        float v0 = (float) gy * step;
        renderState.papi$setAtlasRegion(atlas.location(), u0, v0, u0 + step, v0 + step);
    }

    private static int allocateSlot(int mapId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return -1;
        }
        PapiMapAtlas target = null;
        for (int i = 0, n = ATLASES.size(); i < n; i++) {
            PapiMapAtlas candidate = ATLASES.get(i);
            if (candidate.hasFreeSlot()) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            if (ATLASES.size() >= MAX_ATLAS_COUNT) {
                return -1;
            }
            int size = computeAtlasTextureSize();
            if (size < PapiMapAtlas.MAP_SIZE) {
                return -1;
            }
            int id = ATLASES.size();
            try {
                target = new PapiMapAtlas(id, size);
            } catch (Throwable t) {
                return -1;
            }
            mc.getTextureManager().register(target.location(), target);
            ATLASES.add(target);
        }
        int gridPos = target.allocateNextGridPos();
        if (gridPos < 0) {
            return -1;
        }
        int packed = (target.atlasId() << 16) | gridPos;
        MAP_SLOTS.put(mapId, packed);
        return packed;
    }

    private static int computeAtlasTextureSize() {
        try {
            GpuDevice device = RenderSystem.getDevice();
            int maxTex = device.getDeviceInfo().limits().maxTextureSizeForFormat(GpuFormat.RGBA8_UNORM);
            int clamped = Math.min(MAX_ATLAS_TEXTURE_SIZE, maxTex);
            int aligned = (clamped / PapiMapAtlas.MAP_SIZE) * PapiMapAtlas.MAP_SIZE;
            if (aligned >= 512) {
                return Integer.highestOneBit(aligned);
            }
            return aligned;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void reset() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(PapiMapAtlasManager::resetOnRenderThread);
            }
            return;
        }
        resetOnRenderThread();
    }

    private static void resetOnRenderThread() {
        MAP_SLOTS.clear();
        if (ATLASES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        @Nullable TextureManager texManager = mc != null ? mc.getTextureManager() : null;
        for (int i = 0, n = ATLASES.size(); i < n; i++) {
            PapiMapAtlas atlas = ATLASES.get(i);
            if (texManager != null) {
                texManager.release(atlas.location());
            } else {
                atlas.close();
            }
        }
        ATLASES.clear();
    }
}
