package net.ashpapi.papioptimization.pathfinding;

import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;

public interface PapiNavigationRegionAccess {
    @Nullable LevelChunkSection papi$getSectionIfLoaded(int sectionX, int sectionY, int sectionZ);
    boolean papi$isSectionGuaranteedAir(int sectionX, int sectionY, int sectionZ);
}
