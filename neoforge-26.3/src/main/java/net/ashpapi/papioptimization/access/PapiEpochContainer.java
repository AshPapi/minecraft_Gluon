package net.ashpapi.papioptimization.access;

public interface PapiEpochContainer {

    long papi$getChangeEpoch();

    void papi$incrementEpoch();

    default int papi$getCachedRedstoneSignal(long epoch, int maxStackSize) {
        return -1;
    }

    default void papi$setCachedRedstoneSignal(long epoch, int maxStackSize, int signal) {
    }

    default int papi$getPairedRedstoneSignal(Object partner, long epoch, int maxStackSize) {
        return -1;
    }

    default void papi$setPairedRedstoneSignal(Object partner, long epoch, int maxStackSize, int signal) {
    }
}
