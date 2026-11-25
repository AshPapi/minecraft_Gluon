package net.ashpapi.papioptimization.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.client.occlusion.PapiOcclusionEngine;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@OnlyIn(Dist.CLIENT)
@Mixin(value = WeatherEffectRenderer.class, priority = 1500)
public abstract class WeatherPrecipitationCullingMixin {

    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getPrecipitationAt(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/biome/Biome$Precipitation;"
            )
    )
    private Biome.Precipitation papi$cullOutOfFrustumWeatherColumn(
            ClientLevel level,
            BlockPos pos,
            Operation<Biome.Precipitation> original,
            @Local(argsOnly = true) Vec3 cameraPos,
            @Local(ordinal = 6) int y0,
            @Local(ordinal = 7) int y1
    ) {
        if (!PapiOcclusionEngine.isShadowPass()) {
            Frustum frustum = PapiOcclusionEngine.getCurrentWeatherCullFrustum(cameraPos);
            if (frustum != null) {
                int x = pos.getX();
                int z = pos.getZ();
                if (!PapiOcclusionEngine.isBoxInFrustum(frustum, x, y0, z, x + 1.0, y1, z + 1.0)) {
                    return Biome.Precipitation.NONE;
                }
            }
        }
        return original.call(level, pos);
    }
}
