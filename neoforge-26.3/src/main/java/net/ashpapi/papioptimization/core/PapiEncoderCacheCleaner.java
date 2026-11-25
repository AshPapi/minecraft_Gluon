package net.ashpapi.papioptimization.core;

import com.google.common.cache.LoadingCache;
import net.ashpapi.papioptimization.mixin.core.DataComponentsAccessor;
import net.ashpapi.papioptimization.mixin.core.EncoderCacheAccessor;
import net.minecraft.util.EncoderCache;

public final class PapiEncoderCacheCleaner {

    private PapiEncoderCacheCleaner() {
    }

    public static void clear() {
        try {
            EncoderCache encoderCache = DataComponentsAccessor.papi$getEncoderCache();
            if (encoderCache instanceof EncoderCacheAccessor accessor) {
                LoadingCache<?, ?> cache = accessor.papi$getCache();
                if (cache != null) {
                    cache.invalidateAll();
                    cache.cleanUp();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
