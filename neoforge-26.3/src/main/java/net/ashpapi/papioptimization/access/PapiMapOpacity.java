package net.ashpapi.papioptimization.access;

public interface PapiMapOpacity {

    int PAPI$FULL_DIRTY_RECT = (127 << 16) | (127 << 24);
    int PAPI$EMPTY_DIRTY_RECT = -1;

    default boolean papi$hasTransparency() {
        return true;
    }

    default int papi$consumeDirtyRect() {
        return PAPI$FULL_DIRTY_RECT;
    }
}
