package net.ashpapi.papioptimization.access;

public interface PapiEbeDynamicTracker {

    boolean papi$wasEbeDynamic();

    void papi$setEbeDynamic(boolean dynamic);

    int papi$getEbeRebuildTick();

    void papi$setEbeRebuildTick(int tick);

    boolean papi$isEbeBaked();

    void papi$setEbeBaked(boolean baked);
}
