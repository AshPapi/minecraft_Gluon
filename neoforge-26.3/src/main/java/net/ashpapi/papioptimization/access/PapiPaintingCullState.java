package net.ashpapi.papioptimization.access;

import org.jspecify.annotations.Nullable;

public interface PapiPaintingCullState {

    default long papi$getSolidBackMask() {
        return 0L;
    }

    default void papi$setSolidBackMask(long mask) {
    }

    default boolean @Nullable [] papi$getLargeSolidBack() {
        return null;
    }

    default void papi$setLargeSolidBack(boolean @Nullable [] mask) {
    }

    default byte papi$getOccludedEdgesMask() {
        return 0;
    }

    default void papi$setOccludedEdgesMask(byte mask) {
    }
}
