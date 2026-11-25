package net.ashpapi.papioptimization.access;

public interface PapiOcclusionSection {
    int papi$getOcclusionEpoch();

    void papi$bumpOcclusionEpoch();
}
