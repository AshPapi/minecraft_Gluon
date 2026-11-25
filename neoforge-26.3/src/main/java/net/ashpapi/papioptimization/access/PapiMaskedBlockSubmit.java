package net.ashpapi.papioptimization.access;

public interface PapiMaskedBlockSubmit {

    default byte papi$getFaceMask() {
        return (byte) 0x7F;
    }

    default void papi$setFaceMask(byte mask) {
    }
}
