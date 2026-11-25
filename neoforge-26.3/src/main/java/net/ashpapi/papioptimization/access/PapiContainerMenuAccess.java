package net.ashpapi.papioptimization.access;

import org.jspecify.annotations.Nullable;

public interface PapiContainerMenuAccess {

    boolean papi$isSuppressingRemoteUpdates();

    default @Nullable PapiBatchCraftingContainer papi$getBatchCraftSlots() {
        return null;
    }
}
