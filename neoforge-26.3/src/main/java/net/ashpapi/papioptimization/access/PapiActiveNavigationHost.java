package net.ashpapi.papioptimization.access;

import java.util.Set;
import net.minecraft.world.entity.ai.navigation.PathNavigation;

public interface PapiActiveNavigationHost {
    void papi$addActiveNavigation(PathNavigation navigation);
    void papi$removeActiveNavigation(PathNavigation navigation);
    Set<PathNavigation> papi$getActiveNavigations();
}
