package net.ashpapi.papioptimization.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiMapAtlas extends AbstractTexture {

    public static final int MAP_SIZE = 128;

    private final int atlasId;
    private final Identifier location;
    private final int textureSize;
    private final int gridDim;
    private final int capacity;
    private final float invSize;
    private int allocatedCount;

    public PapiMapAtlas(int atlasId, int textureSize) {
        this.atlasId = atlasId;
        this.location = Identifier.fromNamespaceAndPath("gluon", "dynamic/map_atlas_" + atlasId);
        this.textureSize = textureSize;
        this.gridDim = textureSize / MAP_SIZE;
        this.capacity = this.gridDim * this.gridDim;
        this.invSize = 1.0F / (float) textureSize;

        GpuDevice device = RenderSystem.getDevice();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST;
        this.texture = device.createTexture(
                () -> "Gluon Map Atlas " + atlasId,
                usage,
                GpuFormat.RGBA8_UNORM,
                textureSize,
                textureSize,
                1,
                1
        );
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        this.textureView = device.createTextureView(this.texture);
    }

    public int atlasId() {
        return this.atlasId;
    }

    public Identifier location() {
        return this.location;
    }

    public float invSize() {
        return this.invSize;
    }

    public boolean hasFreeSlot() {
        return this.allocatedCount < this.capacity;
    }

    public int allocateNextGridPos() {
        if (this.allocatedCount >= this.capacity) {
            return -1;
        }
        int idx = this.allocatedCount++;
        int gx = idx % this.gridDim;
        int gy = idx / this.gridDim;
        return (gx << 8) | gy;
    }

    public void uploadSlot(int gridX, int gridY, NativeImage pixels) {
        GpuTexture gpuTex = this.texture;
        if (gpuTex != null && !gpuTex.isClosed() && !pixels.isClosed()) {
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToTexture(gpuTex, pixels, 0, 0, gridX * MAP_SIZE, gridY * MAP_SIZE);
        }
    }
}
