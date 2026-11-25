package net.ashpapi.papioptimization.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface PapiClientLevelLeakCleaner {

    void papi$clearRetainedLevelReferences();

    default void papi$setActiveStorage(@Nullable PapiClientLevelLeakCleaner storage) {}
}
