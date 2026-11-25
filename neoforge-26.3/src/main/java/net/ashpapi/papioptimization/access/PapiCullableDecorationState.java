package net.ashpapi.papioptimization.access;

public interface PapiCullableDecorationState {

    default boolean papi$isBackOccluded() {
        return false;
    }

    default void papi$setBackOccluded(boolean occluded) {
    }
}
