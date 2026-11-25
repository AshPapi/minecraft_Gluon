package net.ashpapi.papioptimization.chunk;

import net.ashpapi.papioptimization.mixin.chunk.PalettedContainerDataAccessor;
import org.jspecify.annotations.Nullable;

public interface PapiPalettedContainerAccess<T> {
    @Nullable PalettedContainerDataAccessor<T> papi$getDataAccessor();
}
