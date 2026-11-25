package net.ashpapi.papioptimization.mixin.map;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.ashpapi.papioptimization.access.PapiFramedMapPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayer.class, priority = 1500)
public abstract class ServerPlayerMapTrackingMixin implements PapiFramedMapPlayer {

    @Unique
    private @Nullable Object2IntOpenHashMap<MapId> papi$frameMapCounts;

    @Unique
    private @Nullable ReferenceOpenHashSet<MapItemSavedData> papi$activeMaps;

    @Override
    public void papi$incrementFrameMapCount(MapId mapId) {
        Object2IntOpenHashMap<MapId> counts = this.papi$frameMapCounts;
        if (counts == null) {
            counts = new Object2IntOpenHashMap<>(4);
            this.papi$frameMapCounts = counts;
        }
        counts.addTo(mapId, 1);
    }

    @Override
    public int papi$decrementFrameMapCount(MapId mapId) {
        Object2IntOpenHashMap<MapId> counts = this.papi$frameMapCounts;
        if (counts == null) {
            return 0;
        }
        int remaining = counts.addTo(mapId, -1) - 1;
        if (remaining <= 0) {
            counts.removeInt(mapId);
            if (counts.isEmpty()) {
                this.papi$frameMapCounts = null;
            }
            return 0;
        }
        return remaining;
    }

    @Override
    public boolean papi$isTrackingFramedMap(MapId mapId) {
        Object2IntOpenHashMap<MapId> counts = this.papi$frameMapCounts;
        return counts != null && counts.getInt(mapId) > 0;
    }

    @Override
    public void papi$registerHoldingMap(MapItemSavedData mapData) {
        ReferenceOpenHashSet<MapItemSavedData> set = this.papi$activeMaps;
        if (set == null) {
            set = new ReferenceOpenHashSet<>(4);
            this.papi$activeMaps = set;
        }
        set.add(mapData);
    }

    @Override
    public void papi$unregisterHoldingMap(MapItemSavedData mapData) {
        ReferenceOpenHashSet<MapItemSavedData> set = this.papi$activeMaps;
        if (set != null && set.remove(mapData) && set.isEmpty()) {
            this.papi$activeMaps = null;
        }
    }

    @Override
    public void papi$cleanupAllTrackedMaps() {
        this.papi$frameMapCounts = null;
        ReferenceOpenHashSet<MapItemSavedData> set = this.papi$activeMaps;
        if (set == null || set.isEmpty()) {
            this.papi$activeMaps = null;
            return;
        }
        this.papi$activeMaps = null;
        ServerPlayer self = (ServerPlayer) (Object) this;
        String name = self.getPlainTextName();
        for (MapItemSavedData data : set) {
            MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) data;
            MapItemSavedData.HoldingPlayer hp = accessor.getCarriedByPlayers().remove(self);
            if (hp != null) {
                accessor.getCarriedBy().remove(hp);
            }
            data.removeDecoration(name);
        }
    }

    @Inject(method = "disconnect", at = @At("RETURN"))
    private void papi$onDisconnectCleanupMaps(CallbackInfo ci) {
        this.papi$cleanupAllTrackedMaps();
    }
}
