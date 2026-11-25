package net.ashpapi.papioptimization.pathfinding;

import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.jspecify.annotations.Nullable;

public interface PapiTrackedNavigatingMob {
    boolean papi$isTrackedInServerLevel();
    void papi$setTrackedInServerLevel(boolean tracked);
    @Nullable PathNavigation papi$getRawNavigation();
    void papi$syncNavigation();
    void papi$unregisterNavigation();
}
