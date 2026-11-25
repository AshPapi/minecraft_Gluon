package net.ashpapi.papioptimization.network;

public interface PapiAutoFlush {

    void papi$suspendAutoFlush();

    void papi$resumeAutoFlush();
}
