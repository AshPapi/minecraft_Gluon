package net.ashpapi.papioptimization.client;

import com.google.common.cache.LoadingCache;
import java.util.concurrent.atomic.AtomicInteger;
import net.ashpapi.papioptimization.mixin.core.DataComponentsAccessor;
import net.ashpapi.papioptimization.mixin.core.EncoderCacheAccessor;
import net.minecraft.util.EncoderCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PapiClientCaches {

    private static final AtomicInteger EPOCH = new AtomicInteger(1);

    private PapiClientCaches() {}

    public static int epoch() {
        return EPOCH.get();
    }

    public static void invalidateAll() {
        EPOCH.incrementAndGet();
    }

    public static void invalidateEncoderCache() {
        EncoderCache encoderCache = DataComponentsAccessor.papi$getEncoderCache();
        if (encoderCache != null) {
            LoadingCache<?, ?> cache = ((EncoderCacheAccessor) encoderCache).papi$getCache();
            if (cache != null) {
                cache.invalidateAll();
            }
        }
    }
}
