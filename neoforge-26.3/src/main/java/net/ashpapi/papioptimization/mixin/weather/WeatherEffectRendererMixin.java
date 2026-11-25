package net.ashpapi.papioptimization.mixin.weather;

import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.ashpapi.papioptimization.core.BlockPosPool;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer.ColumnInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = WeatherEffectRenderer.class, priority = 1500)
public abstract class WeatherEffectRendererMixin {

    @Shadow
    protected abstract ColumnInstance createRainColumnInstance(RandomSource random, long ticks, int x, int bottomY, int topY, int z, int lightCoords, float partialTicks);

    @Shadow
    protected abstract ColumnInstance createSnowColumnInstance(RandomSource random, long ticks, int x, int bottomY, int topY, int z, int lightCoords, float partialTicks);

    @Unique
    private static final ThreadLocal<SingleThreadedRandomSource> PAPI$RANDOM =
            ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(0L));

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void papi$fastExtractRenderState(ClientLevel level, float partialTicks, Vec3 cameraPos, WeatherRenderState renderState, CallbackInfo ci) {
        renderState.rainColumns.clear();
        renderState.snowColumns.clear();
        renderState.intensity = level.getRainLevel(partialTicks);
        if (renderState.intensity <= 0.0F) {
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int radius = mc.options.weatherRadius().get();
        renderState.radius = radius;
        if (radius <= 0) {
            ci.cancel();
            return;
        }

        int cameraBlockX = Mth.floor(cameraPos.x);
        int cameraBlockY = Mth.floor(cameraPos.y);
        int cameraBlockZ = Mth.floor(cameraPos.z);

        Frustum cullFrustum = PapiOcclusionEngine.getCurrentWeatherCullFrustum(cameraPos);
        if (cullFrustum == null && !PapiOcclusionEngine.isShadowPass() && mc.gameRenderer != null) {
            Camera cam = mc.gameRenderer.mainCamera();
            if (cam != null && !cam.isPanoramicMode() && cam.position().distanceToSqr(cameraPos) <= 1.0E-4) {
                Frustum candidate = cam.getCullFrustum();
                if (candidate != null && !PapiOcclusionEngine.isShadowFrustum(candidate)) {
                    cullFrustum = candidate;
                }
            }
        }

        int minX = cameraBlockX - radius;
        int maxX = cameraBlockX + radius;
        int minZ = cameraBlockZ - radius;
        int maxZ = cameraBlockZ + radius;
        int minColY = cameraBlockY - radius;
        int maxColY = cameraBlockY + radius;

        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        ClientChunkCache chunkSource = level.getChunkSource();
        int poolMark = BlockPosPool.mark();
        try {
            BlockPos.MutableBlockPos mutablePos = BlockPosPool.get();
            SingleThreadedRandomSource random = PAPI$RANDOM.get();
            long gameTime = level.getGameTime();

            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int tileMinZ = Math.max(minZ, cz << 4);
                int tileMaxZ = Math.min(maxZ, (cz << 4) + 15);
                for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                    ChunkAccess chunk = chunkSource.getChunk(cx, cz, false);
                    if (chunk == null) {
                        continue;
                    }
                    int tileMinX = Math.max(minX, cx << 4);
                    int tileMaxX = Math.min(maxX, (cx << 4) + 15);

                    int tileFrustumState = -2;
                    if (cullFrustum != null) {
                        tileFrustumState = PapiOcclusionEngine.classifyBoxInFrustum(
                                cullFrustum,
                                tileMinX,
                                minColY,
                                tileMinZ,
                                tileMaxX + 1.0,
                                maxColY,
                                tileMaxZ + 1.0
                        );
                        if (tileFrustumState == 0) {
                            continue;
                        }
                    }

                    boolean checkPerColumnFrustum = (tileFrustumState == -1);
                    for (int z = tileMinZ; z <= tileMaxZ; z++) {
                        int localZ = z & 15;
                        int zHash = z * z * 418711 + z * 13761;
                        for (int x = tileMinX; x <= tileMaxX; x++) {
                            int terrainHeight = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, localZ) + 1;
                            if (terrainHeight >= maxColY) {
                                continue;
                            }
                            int y0 = Math.max(minColY, terrainHeight);
                            int y1 = maxColY;

                            if (checkPerColumnFrustum
                                    && !PapiOcclusionEngine.isBoxInFrustum(cullFrustum, x, y0, z, x + 1.0, y1, z + 1.0)) {
                                continue;
                            }

                            Biome.Precipitation precipitation = level.getPrecipitationAt(mutablePos.set(x, cameraBlockY, z));
                            if (precipitation == Biome.Precipitation.NONE) {
                                continue;
                            }

                            int seed = x * x * 3121 + x * 45238971 ^ zHash;
                            random.setSeed((long) seed);
                            int lightSampleY = Math.max(cameraBlockY, terrainHeight);
                            int lightCoords = LightCoordsUtil.getLightCoords((BlockAndLightGetter) level, mutablePos.set(x, lightSampleY, z));

                            if (precipitation == Biome.Precipitation.RAIN) {
                                renderState.rainColumns.add(this.createRainColumnInstance(random, gameTime, x, y0, y1, z, lightCoords, partialTicks));
                            } else if (precipitation == Biome.Precipitation.SNOW) {
                                renderState.snowColumns.add(this.createSnowColumnInstance(random, gameTime, x, y0, y1, z, lightCoords, partialTicks));
                            }
                        }
                    }
                }
            }
        } finally {
            BlockPosPool.reset(poolMark);
        }

        ci.cancel();
    }
}
