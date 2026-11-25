package net.ashpapi.papioptimization.mixin.worldgen;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.ashpapi.papioptimization.worldgen.PapiRingSetAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ConcentricRingsStructurePlacement.class, priority = 1500)
public abstract class ConcentricRingsPlacementMixin {

    @WrapMethod(method = "isPlacementChunk")
    private boolean papi$fastIsPlacementChunk(
            ChunkGeneratorStructureState state,
            int chunkX,
            int chunkZ,
            Operation<Boolean> original
    ) {
        if (state instanceof PapiRingSetAccess ringAccess) {
            LongSet set = ringAccess.papi$getRingChunkSet((ConcentricRingsStructurePlacement) (Object) this);
            return set != null && set.contains(ChunkPos.pack(chunkX, chunkZ));
        }
        return original.call(state, chunkX, chunkZ);
    }
}
