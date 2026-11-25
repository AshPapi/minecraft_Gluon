package net.ashpapi.papioptimization.mixin.redstone;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = Level.class, priority = 1500)
public abstract class LevelComparatorSyncLoadMixin {

    @WrapOperation(
            method = "updateNeighbourForOutputSignal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;hasChunkAt(Lnet/minecraft/core/BlockPos;)Z")
    )
    private boolean papi$nonBlockingComparatorChunkCheck(Level instance, BlockPos pos, Operation<Boolean> original) {
        if (instance instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
        }
        return original.call(instance, pos);
    }
}
