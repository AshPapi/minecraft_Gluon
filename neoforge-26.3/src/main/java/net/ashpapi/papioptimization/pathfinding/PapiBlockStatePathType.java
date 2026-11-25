package net.ashpapi.papioptimization.pathfinding;

import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

public interface PapiBlockStatePathType {
    boolean papi$isCacheable();
    @Nullable PathType papi$getCachedPathType(int epoch);
    void papi$setCachedPathType(PathType type, int epoch);
    int papi$getCachedNeighborSlot(int epoch);
    void papi$setCachedNeighborSlot(int slot, int epoch);
}
