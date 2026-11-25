package net.ashpapi.papioptimization.worldgen;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.jspecify.annotations.Nullable;

public interface PapiRingSetAccess {
    @Nullable LongSet papi$getRingChunkSet(ConcentricRingsStructurePlacement placement);
}
