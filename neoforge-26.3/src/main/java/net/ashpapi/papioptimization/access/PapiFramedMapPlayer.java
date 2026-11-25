package net.ashpapi.papioptimization.access;

import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public interface PapiFramedMapPlayer {

    void papi$incrementFrameMapCount(MapId mapId);

    int papi$decrementFrameMapCount(MapId mapId);

    boolean papi$isTrackingFramedMap(MapId mapId);

    void papi$registerHoldingMap(MapItemSavedData mapData);

    void papi$unregisterHoldingMap(MapItemSavedData mapData);

    default void papi$cleanupAllTrackedMaps() {
    }
}
