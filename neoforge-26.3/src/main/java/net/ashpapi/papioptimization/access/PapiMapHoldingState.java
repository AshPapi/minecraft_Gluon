package net.ashpapi.papioptimization.access;

public interface PapiMapHoldingState {

    int papi$getUnobservedSinceTick();

    void papi$setUnobservedSinceTick(int serverTick);

    void papi$clearUnobserved();
}
