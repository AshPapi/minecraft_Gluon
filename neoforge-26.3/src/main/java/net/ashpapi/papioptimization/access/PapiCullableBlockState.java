package net.ashpapi.papioptimization.access;

import org.jspecify.annotations.Nullable;

public interface PapiCullableBlockState {

    default int papi$getCullFlags() {
        return 0;
    }

    default void papi$setCullFlags(int flags) {
    }

    default void papi$setCullData(int flags, long[] @Nullable [] faceMasks) {
    }

    default long @Nullable [] papi$getFaceMask(int slot) {
        return null;
    }
}
