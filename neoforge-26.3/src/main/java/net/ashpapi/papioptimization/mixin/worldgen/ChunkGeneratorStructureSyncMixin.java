package net.ashpapi.papioptimization.mixin.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkGenerator.class, priority = 1500)
public abstract class ChunkGeneratorStructureSyncMixin {

    @Inject(method = "getMobsAt", at = @At("HEAD"), cancellable = true)
    private void papi$fastNoStructureMobs(
            Level level,
            StructureManager structureManager,
            MobCategory category,
            BlockPos pos,
            CallbackInfoReturnable<WeightedList<MobSpawnSettings.SpawnerData>> cir
    ) {
        ChunkAccess chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && chunk.getAllReferences().isEmpty()) {
            MobSpawnSettings settings = level.environmentAttributes().getValue(EnvironmentAttributes.NATURAL_MOB_SPAWNS, pos);
            cir.setReturnValue(settings.getMobsToSpawn(category));
        }
    }
}
