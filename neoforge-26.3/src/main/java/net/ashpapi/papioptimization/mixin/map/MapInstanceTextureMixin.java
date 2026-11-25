package net.ashpapi.papioptimization.mixin.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.ashpapi.papioptimization.access.PapiMapOpacity;
import net.ashpapi.papioptimization.client.map.PapiMapAtlasManager;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.ashpapi.papioptimization.map.FastMapColorCache;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(targets = "net.minecraft.client.resources.MapTextureManager$MapInstance", priority = 1500)
public abstract class MapInstanceTextureMixin {

    @Shadow
    private MapItemSavedData data;

    @Shadow
    @Final
    private DynamicTexture texture;

    @Shadow
    private boolean requiresUpload;

    @Unique
    private int papi$mapId = Integer.MIN_VALUE;

    @Unique
    private boolean papi$uploadedOnce;

    @Unique
    private @Nullable MapItemSavedData papi$lastData;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void papi$captureMapId(MapTextureManager outer, int id, MapItemSavedData data, CallbackInfo ci) {
        this.papi$mapId = id;
    }

    @Inject(method = "updateTextureIfNeeded", at = @At("HEAD"), cancellable = true)
    private void papi$incrementalDirtyRectAndAtlasUpload(CallbackInfo ci) {
        boolean useAtlas = PapiConfig.mapAtlas() && this.papi$mapId != Integer.MIN_VALUE;
        if (!this.requiresUpload) {
            if (useAtlas && this.papi$uploadedOnce && PapiMapAtlasManager.getPackedSlot(this.papi$mapId) == -1) {
                NativeImage pixels = this.texture.getPixels();
                if (pixels != null && !pixels.isClosed()) {
                    PapiMapAtlasManager.uploadSlot(this.papi$mapId, pixels);
                }
            }
            ci.cancel();
            return;
        }

        NativeImage pixels = this.texture.getPixels();
        MapItemSavedData currentData = this.data;
        if (pixels == null || pixels.isClosed() || currentData == null || currentData.colors == null || currentData.colors.length < 16384) {
            this.requiresUpload = false;
            ci.cancel();
            return;
        }

        int dirtyRect = currentData instanceof PapiMapOpacity op
                ? op.papi$consumeDirtyRect()
                : PapiMapOpacity.PAPI$FULL_DIRTY_RECT;
        boolean fullScan = !this.papi$uploadedOnce || currentData != this.papi$lastData;
        byte[] colors = currentData.colors;
        boolean changed = false;

        if (fullScan) {
            for (int y = 0; y < 128; y++) {
                int rowOffset = y << 7;
                for (int x = 0; x < 128; x++) {
                    int argb = FastMapColorCache.getArgbColor(colors[rowOffset + x] & 0xFF);
                    pixels.setPixel(x, y, argb);
                }
            }
            this.papi$uploadedOnce = true;
            this.papi$lastData = currentData;
            changed = true;
        } else if (dirtyRect != PapiMapOpacity.PAPI$EMPTY_DIRTY_RECT) {
            int minX = Math.clamp(dirtyRect & 0xFF, 0, 127);
            int minY = Math.clamp((dirtyRect >>> 8) & 0xFF, 0, 127);
            int maxX = Math.clamp((dirtyRect >>> 16) & 0xFF, 0, 127);
            int maxY = Math.clamp((dirtyRect >>> 24) & 0xFF, 0, 127);
            if (minX <= maxX && minY <= maxY) {
                for (int y = minY; y <= maxY; y++) {
                    int rowOffset = y << 7;
                    for (int x = minX; x <= maxX; x++) {
                        int argb = FastMapColorCache.getArgbColor(colors[rowOffset + x] & 0xFF);
                        if (pixels.getPixel(x, y) != argb) {
                            pixels.setPixel(x, y, argb);
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed) {
            this.texture.upload();
            if (useAtlas) {
                PapiMapAtlasManager.uploadSlot(this.papi$mapId, pixels);
            }
        } else if (useAtlas && PapiMapAtlasManager.getPackedSlot(this.papi$mapId) == -1) {
            PapiMapAtlasManager.uploadSlot(this.papi$mapId, pixels);
        }

        this.requiresUpload = false;
        ci.cancel();
    }
}
