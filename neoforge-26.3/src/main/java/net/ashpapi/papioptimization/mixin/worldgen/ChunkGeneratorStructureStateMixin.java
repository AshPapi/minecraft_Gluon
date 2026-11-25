package net.ashpapi.papioptimization.mixin.worldgen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.ashpapi.papioptimization.worldgen.PapiRingSetAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ChunkGeneratorStructureState.class, priority = 1500)
public abstract class ChunkGeneratorStructureStateMixin implements PapiRingSetAccess {

    @Shadow
    public abstract @Nullable List<ChunkPos> getRingPositionsFor(ConcentricRingsStructurePlacement placement);

    @Unique
    private final Map<ConcentricRingsStructurePlacement, LongSet> papi$ringSets = new ConcurrentHashMap<>();

    @Override
    public @Nullable LongSet papi$getRingChunkSet(ConcentricRingsStructurePlacement placement) {
        LongSet cached = this.papi$ringSets.get(placement);
        if (cached != null) {
            return cached;
        }
        List<ChunkPos> positions = this.getRingPositionsFor(placement);
        if (positions == null) {
            return null;
        }
        LongSet built;
        if (positions.isEmpty()) {
            built = LongSets.EMPTY_SET;
        } else {
            LongOpenHashSet set = new LongOpenHashSet(positions.size());
            for (int i = 0, len = positions.size(); i < len; i++) {
                set.add(positions.get(i).pack());
            }
            built = set;
        }
        this.papi$ringSets.put(placement, built);
        return built;
    }
}
