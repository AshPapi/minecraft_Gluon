package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.PapiClientLevelLeakCleaner;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientChunkCache.class, priority = 1500)
public abstract class ClientChunkCacheLeakMixin implements PapiClientLevelLeakCleaner {

    @Unique
    private volatile @Nullable PapiClientLevelLeakCleaner papi$activeStorage;

    @Override
    public void papi$setActiveStorage(@Nullable PapiClientLevelLeakCleaner storage) {
        this.papi$activeStorage = storage;
    }

    @Override
    public void papi$clearRetainedLevelReferences() {
        PapiClientLevelLeakCleaner storage = this.papi$activeStorage;
        this.papi$activeStorage = null;
        if (storage != null) {
            storage.papi$clearRetainedLevelReferences();
        }
    }
}
