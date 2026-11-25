package net.ashpapi.papioptimization.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.ashpapi.papioptimization.access.PapiLightningChunk;
import net.ashpapi.papioptimization.core.FastWeatherHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerLevel.class, priority = 1500)
public abstract class ServerLevelWeatherMixin {

    @Inject(method = "tickThunder", at = @At("HEAD"), cancellable = true)
    private void papi$fastThunder(LevelChunk chunk, CallbackInfo ci) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!self.isRaining() || !self.isThundering()) {
            ci.cancel();
        }
    }

    @WrapOperation(
            method = "tickThunder",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/RandomSource;nextInt(I)I",
                    ordinal = 0
            )
    )
    private int papi$perChunkLightningCountdown(
            RandomSource random,
            int bound,
            Operation<Integer> original,
            @Local(argsOnly = true) LevelChunk chunk
    ) {
        if (chunk instanceof PapiLightningChunk lightningChunk) {
            return lightningChunk.papi$shouldDoLightning(random, bound) ? 0 : 1;
        }
        return original.call(random, bound);
    }

    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void papi$fastPrecipitation(BlockPos pos, CallbackInfo ci) {
        FastWeatherHandler.tickPrecipitationFast((ServerLevel) (Object) this, pos);
        ci.cancel();
    }
}
