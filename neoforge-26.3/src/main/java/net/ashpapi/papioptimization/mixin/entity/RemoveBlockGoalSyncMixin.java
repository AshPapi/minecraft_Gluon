package net.ashpapi.papioptimization.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RemoveBlockGoal.class, priority = 1500)
public abstract class RemoveBlockGoalSyncMixin {

    @WrapOperation(
        method = "isValidTarget",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"
        )
    )
    private @Nullable ChunkAccess papi$avoidSyncChunkLoad(
            LevelReader level,
            int x,
            int z,
            ChunkStatus status,
            boolean load,
            Operation<@Nullable ChunkAccess> original
    ) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(x, z);
        }
        return original.call(level, x, z, status, load);
    }
}
