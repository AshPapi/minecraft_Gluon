package net.ashpapi.papioptimization.access;

public interface PapiShulkerRenderState {

    default boolean papi$isBottomOccluded() {
        return false;
    }

    default void papi$setBottomOccluded(boolean occluded) {
    }
}
