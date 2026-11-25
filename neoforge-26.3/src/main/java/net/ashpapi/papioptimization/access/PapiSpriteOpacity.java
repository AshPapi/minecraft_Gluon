package net.ashpapi.papioptimization.access;

public interface PapiSpriteOpacity {

    default long papi$getOpacityWord(int index) {
        return -1L;
    }

    default byte papi$getOpacityFlags() {
        return 1;
    }

    default void papi$setOpacityData(long w0, long w1, long w2, long w3, byte flags) {
    }
}
