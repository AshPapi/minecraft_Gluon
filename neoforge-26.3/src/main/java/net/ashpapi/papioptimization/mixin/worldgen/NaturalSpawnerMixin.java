package net.ashpapi.papioptimization.mixin.worldgen;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NaturalSpawner.class, priority = 1500)
public abstract class NaturalSpawnerMixin {

    @Inject(method = "spawnForChunk", at = @At("HEAD"), cancellable = true)
    private static void papi$fastChunkSpawnCheck(
            ServerLevel level, LevelChunk chunk, NaturalSpawner.SpawnState spawnState,
            List<MobCategory> categories, CallbackInfo ci
    ) {
        if (categories == null || categories.isEmpty() || chunk.isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "isInNetherFortressBounds", at = @At("HEAD"), cancellable = true)
    private static void papi$nonBlockingFortressCheck(
            BlockPos pos,
            ServerLevel level,
            MobCategory category,
            StructureManager structureManager,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (category != MobCategory.MONSTER) {
            cir.setReturnValue(false);
            return;
        }
        int belowY = pos.getY() - 1;
        ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || chunk.isOutsideBuildHeight(belowY)) {
            cir.setReturnValue(false);
            return;
        }
        if (!chunk.getSection((belowY >> 4) - chunk.getMinSectionY())
                .getBlockState(pos.getX() & 15, belowY & 15, pos.getZ() & 15)
                .is(Blocks.NETHER_BRICKS)) {
            cir.setReturnValue(false);
        }
    }
}
