package net.ashpapi.papioptimization.mixin.sculk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DynamicGameEventListener.class, priority = 1500)
public abstract class DynamicGameEventListenerSyncMixin {

    @WrapOperation(
            method = "ifChunkExists",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/LevelReader;getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;"
            )
    )
    private static @Nullable ChunkAccess papi$nonBlockingListenerChunkLookup(
            LevelReader level,
            int x,
            int z,
            ChunkStatus status,
            boolean load,
            Operation<ChunkAccess> original
    ) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getChunkSource().getChunkNow(x, z);
        }
        return original.call(level, x, z, status, load);
    }
}
