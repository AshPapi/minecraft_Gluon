package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.bee.Bee;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Bee.class, priority = 1500)
public abstract class BeeSyncLoadMixin {

    @Shadow
    private @Nullable BlockPos hivePos;

    @ModifyExpressionValue(
            method = "getBeehiveBlockEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/bee/Bee;isTooFarAway(Lnet/minecraft/core/BlockPos;)Z"
            )
    )
    private boolean papi$skipUnloadedBeehiveChunk(boolean isTooFar) {
        if (isTooFar) {
            return true;
        }
        BlockPos pos = this.hivePos;
        if (pos != null && ((Bee) (Object) this).level() instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null;
        }
        return false;
    }
}
