package net.ashpapi.papioptimization.ai;

import java.util.Optional;

public interface PapiPoiColumnIndex<R> {
    long papi$getColumnNonEmptyMask(long chunkKey);
    void papi$setSectionNonEmpty(long chunkKey, int bit, boolean nonEmpty);
    Optional<R> papi$getLoadedSectionDirect(long sectionKey);
}
