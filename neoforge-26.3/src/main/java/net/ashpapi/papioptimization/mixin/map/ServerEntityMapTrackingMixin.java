package net.ashpapi.papioptimization.mixin.map;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.ashpapi.papioptimization.access.PapiFramedMapPlayer;
import net.ashpapi.papioptimization.access.PapiMapHoldingState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerEntity.class, priority = 1500)
public abstract class ServerEntityMapTrackingMixin {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private final Set<ServerPlayer> papi$trackingPlayers = new ReferenceOpenHashSet<>();

    @Unique
    private final ObjectArrayList<ServerPlayer> papi$trackingPlayerList = new ObjectArrayList<>();

    @Unique
    private final List<ServerPlayer> papi$trackingPlayersView = Collections.unmodifiableList(this.papi$trackingPlayerList);

    @Unique
    private final Reference2ObjectOpenHashMap<ServerPlayer, MapId> papi$countedFrameMaps =
            new Reference2ObjectOpenHashMap<>();

    @Unique
    private @Nullable MapId papi$lastSyncedFrameMapId;

    @Unique
    private boolean papi$pairingDirty;

    @Unique
    private static boolean papi$playerHasMapById(ServerPlayer player, MapId mapId) {
        Inventory inv = player.getInventory();
        for (int i = 0, n = inv.getContainerSize(); i < n; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && mapId.equals(s.get(DataComponents.MAP_ID))) {
                return true;
            }
        }
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && mapId.equals(carried.get(DataComponents.MAP_ID))) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private void papi$evictPlayerIfUntracked(ServerPlayer player, MapId mapId) {
        PapiFramedMapPlayer framedPlayer = (player instanceof PapiFramedMapPlayer fp) ? fp : null;
        if (framedPlayer != null && framedPlayer.papi$isTrackingFramedMap(mapId)) {
            return;
        }
        if (papi$playerHasMapById(player, mapId)) {
            return;
        }
        MapItemSavedData data = this.level.getMapData(mapId);
        if (data == null) {
            return;
        }
        data.removeDecoration(player.getPlainTextName());
        MapItemSavedDataAccessor accessor = (MapItemSavedDataAccessor) data;
        if (player.isRemoved() || player.hasDisconnected()) {
            MapItemSavedData.HoldingPlayer hp = accessor.getCarriedByPlayers().remove(player);
            if (hp != null) {
                accessor.getCarriedBy().remove(hp);
            }
            if (framedPlayer != null) {
                framedPlayer.papi$unregisterHoldingMap(data);
            }
            return;
        }
        MapItemSavedData.HoldingPlayer hp = accessor.getCarriedByPlayers().get(player);
        if (hp instanceof PapiMapHoldingState hs) {
            if (hs.papi$getUnobservedSinceTick() == Integer.MIN_VALUE) {
                hs.papi$setUnobservedSinceTick(this.level.getServer().getTickCount());
            }
        } else if (hp != null) {
            accessor.getCarriedByPlayers().remove(player);
            accessor.getCarriedBy().remove(hp);
            if (framedPlayer != null) {
                framedPlayer.papi$unregisterHoldingMap(data);
            }
        }
    }

    @Inject(method = "addPairing", at = @At("HEAD"))
    private void papi$onAddPairing(ServerPlayer player, CallbackInfo ci) {
        boolean added = this.papi$trackingPlayers.add(player);
        if (added) {
            this.papi$trackingPlayerList.add(player);
        }
        if (this.entity instanceof ItemFrame frame) {
            this.papi$pairingDirty = true;
            if (!this.papi$countedFrameMaps.containsKey(player)) {
                MapId mapId = frame.getItem().get(DataComponents.MAP_ID);
                if (added && this.papi$trackingPlayerList.size() == 1) {
                    this.papi$lastSyncedFrameMapId = mapId;
                }
                if (mapId != null) {
                    this.papi$countedFrameMaps.put(player, mapId);
                    if (player instanceof PapiFramedMapPlayer fp) {
                        fp.papi$incrementFrameMapCount(mapId);
                    }
                    MapItemSavedData data = this.level.getMapData(mapId);
                    if (data != null) {
                        MapItemSavedData.HoldingPlayer hp = ((MapItemSavedDataAccessor) data).getCarriedByPlayers().get(player);
                        if (hp instanceof PapiMapHoldingState hs) {
                            hs.papi$clearUnobserved();
                        }
                    }
                }
            }
        }
    }

    @Inject(method = "removePairing", at = @At("HEAD"))
    private void papi$onRemovePairing(ServerPlayer player, CallbackInfo ci) {
        if (this.papi$trackingPlayers.remove(player)) {
            int idx = this.papi$trackingPlayerList.indexOf(player);
            if (idx >= 0) {
                int last = this.papi$trackingPlayerList.size() - 1;
                if (idx != last) {
                    this.papi$trackingPlayerList.set(idx, this.papi$trackingPlayerList.get(last));
                }
                this.papi$trackingPlayerList.remove(last);
            }
        }

        if (this.entity instanceof ItemFrame frame) {
            MapId countedId = this.papi$countedFrameMaps.remove(player);
            if (countedId != null) {
                if (player instanceof PapiFramedMapPlayer fp) {
                    fp.papi$decrementFrameMapCount(countedId);
                }
                this.papi$evictPlayerIfUntracked(player, countedId);
            }

            ItemStack itemStack = frame.getItem();
            MapId currentId = itemStack.get(DataComponents.MAP_ID);
            if (currentId != null && !currentId.equals(countedId)) {
                this.papi$evictPlayerIfUntracked(player, currentId);
            }

            if (this.papi$trackingPlayerList.isEmpty()) {
                this.papi$lastSyncedFrameMapId = null;
                this.papi$pairingDirty = false;
            }
        }
    }

    @Inject(method = "sendChanges", at = @At("HEAD"))
    private void papi$syncFrameMapIdOnChanges(CallbackInfo ci) {
        if (!(this.entity instanceof ItemFrame frame) || this.papi$trackingPlayerList.isEmpty()) {
            return;
        }
        MapId currentId = frame.getItem().get(DataComponents.MAP_ID);
        if (!this.papi$pairingDirty && Objects.equals(currentId, this.papi$lastSyncedFrameMapId)) {
            return;
        }
        this.papi$pairingDirty = false;
        this.papi$lastSyncedFrameMapId = currentId;

        for (int i = 0, n = this.papi$trackingPlayerList.size(); i < n; i++) {
            ServerPlayer player = this.papi$trackingPlayerList.get(i);
            MapId oldId = this.papi$countedFrameMaps.get(player);
            if (Objects.equals(oldId, currentId)) {
                continue;
            }
            if (currentId == null) {
                this.papi$countedFrameMaps.remove(player);
            } else {
                this.papi$countedFrameMaps.put(player, currentId);
            }
            if (oldId != null) {
                if (player instanceof PapiFramedMapPlayer fp) {
                    fp.papi$decrementFrameMapCount(oldId);
                }
                this.papi$evictPlayerIfUntracked(player, oldId);
            }
            if (currentId != null) {
                if (player instanceof PapiFramedMapPlayer fp) {
                    fp.papi$incrementFrameMapCount(currentId);
                }
                MapItemSavedData data = this.level.getMapData(currentId);
                if (data != null) {
                    MapItemSavedData.HoldingPlayer hp = ((MapItemSavedDataAccessor) data).getCarriedByPlayers().get(player);
                    if (hp instanceof PapiMapHoldingState hs) {
                        hs.papi$clearUnobserved();
                    }
                }
            }
        }
    }

    @WrapOperation(
            method = "sendChanges",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;players()Ljava/util/List;")
    )
    private List<ServerPlayer> papi$wrapTrackingPlayers(ServerLevel serverLevel, Operation<List<ServerPlayer>> original) {
        if (this.entity instanceof ItemFrame) {
            return this.papi$trackingPlayerList.isEmpty()
                    ? Collections.emptyList()
                    : this.papi$trackingPlayersView;
        }
        return original.call(serverLevel);
    }
}
