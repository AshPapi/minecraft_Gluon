package net.ashpapi.papioptimization.mixin.map;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.ashpapi.papioptimization.access.PapiFramedMapPlayer;
import net.ashpapi.papioptimization.access.PapiMapHoldingState;
import net.ashpapi.papioptimization.access.PapiMapOpacity;
import net.ashpapi.papioptimization.map.FastMapColorCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapFrame;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MapItemSavedData.class, priority = 1500)
public abstract class MapItemSavedDataMixin implements PapiMapOpacity {

    @Unique
    private static final int PAPI$UNOBSERVED_GRACE_TICKS = 100;

    @Shadow
    public byte[] colors;

    @Shadow
    @Final
    private Map<String, MapFrame> frameMarkers;

    @Shadow
    @Final
    private Map<String, MapDecoration> decorations;

    @Shadow
    @Final
    private Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers;

    @Shadow
    @Final
    private List<MapItemSavedData.HoldingPlayer> carriedBy;

    @Shadow
    private boolean trackingPosition;

    @Unique
    private int papi$lastCleanupServerTick = Integer.MIN_VALUE;

    @Unique
    private int papi$fallbackTickCounter;

    @Unique
    private int papi$transparentCount = -1;

    @Unique
    private boolean papi$inSetColor;

    @Unique
    private int papi$dirtyMinX = 0;

    @Unique
    private int papi$dirtyMinY = 0;

    @Unique
    private int papi$dirtyMaxX = 127;

    @Unique
    private int papi$dirtyMaxY = 127;

    @Unique
    private @Nullable MapDecorations papi$lastStaticDecorations;

    @Shadow
    public abstract void removeDecoration(String id);

    @Shadow
    private static boolean hasMapInvisibilityItemEquipped(Player player) {
        throw new AssertionError();
    }

    @Unique
    private static boolean papi$matchesMapStack(ItemStack candidate, ItemStack refStack, Item refItem, @Nullable MapId mapId) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (candidate == refStack) {
            return true;
        }
        return candidate.is(refItem) && Objects.equals(mapId, candidate.get(DataComponents.MAP_ID));
    }

    @Unique
    private static boolean papi$playerHoldsMatchingMap(Player player, ItemStack refStack, @Nullable MapId mapId) {
        Item refItem = refStack.getItem();
        Inventory inv = player.getInventory();
        for (int i = 0, n = inv.getContainerSize(); i < n; i++) {
            if (papi$matchesMapStack(inv.getItem(i), refStack, refItem, mapId)) {
                return true;
            }
        }
        if (player.containerMenu != null) {
            return papi$matchesMapStack(player.containerMenu.getCarried(), refStack, refItem, mapId);
        }
        return false;
    }

    @Inject(method = "getHoldingPlayer", at = @At("RETURN"))
    private void papi$onGetHoldingPlayer(Player player, CallbackInfoReturnable<MapItemSavedData.HoldingPlayer> cir) {
        if (player instanceof PapiFramedMapPlayer fp) {
            fp.papi$registerHoldingMap((MapItemSavedData) (Object) this);
        }
        MapItemSavedData.HoldingPlayer hp = cir.getReturnValue();
        if (hp instanceof PapiMapHoldingState hs) {
            hs.papi$clearUnobserved();
        }
    }

    @Inject(method = "removeDecoration", at = @At("HEAD"), cancellable = true)
    private void papi$skipRedundantRemoveDecoration(String id, CallbackInfo ci) {
        if (!this.decorations.containsKey(id)) {
            ci.cancel();
            return;
        }
        this.papi$lastStaticDecorations = null;
    }

    @Inject(method = "tickCarriedBy", at = @At("HEAD"), cancellable = true)
    private void papi$fastTickCarriedBy(
            Player tickingPlayer,
            ItemStack itemStack,
            @Nullable ItemFrame placedInFrame,
            CallbackInfo ci
    ) {
        if (placedInFrame == null) {
            MapItemSavedData.HoldingPlayer hp = this.carriedByPlayers.get(tickingPlayer);
            if (hp instanceof PapiMapHoldingState hs) {
                hs.papi$clearUnobserved();
            }
            return;
        }

        MapItemSavedData self = (MapItemSavedData) (Object) this;
        MapItemSavedData.HoldingPlayer tickingHp = this.carriedByPlayers.get(tickingPlayer);
        if (tickingHp == null) {
            tickingHp = self.getHoldingPlayer(tickingPlayer);
        }
        if (tickingHp instanceof PapiMapHoldingState ths) {
            ths.papi$clearUnobserved();
        }

        int serverTick = (tickingPlayer.level() instanceof ServerLevel sl)
                ? sl.getServer().getTickCount()
                : ++this.papi$fallbackTickCounter;

        if (serverTick != this.papi$lastCleanupServerTick) {
            this.papi$lastCleanupServerTick = serverTick;
            MapId mapId = itemStack.get(DataComponents.MAP_ID);
            for (int i = this.carriedBy.size() - 1; i >= 0; i--) {
                MapItemSavedData.HoldingPlayer hp = this.carriedBy.get(i);
                Player p = hp.player;
                if (p.isRemoved() || (p instanceof ServerPlayer sp && sp.hasDisconnected())) {
                    this.carriedBy.remove(i);
                    this.carriedByPlayers.remove(p);
                    if (p instanceof PapiFramedMapPlayer fp) {
                        fp.papi$unregisterHoldingMap(self);
                    }
                    this.removeDecoration(p.getPlainTextName());
                    continue;
                }

                boolean hasMapInInv = papi$playerHoldsMatchingMap(p, itemStack, mapId);
                String pName = p.getPlainTextName();
                if (this.decorations.containsKey(pName) && (!hasMapInInv || hasMapInvisibilityItemEquipped(p))) {
                    this.removeDecoration(pName);
                }

                if (hp instanceof PapiMapHoldingState hs) {
                    boolean observed = hasMapInInv
                            || p == tickingPlayer
                            || (mapId != null && p instanceof PapiFramedMapPlayer fp && fp.papi$isTrackingFramedMap(mapId));
                    if (observed) {
                        hs.papi$clearUnobserved();
                    } else {
                        int since = hs.papi$getUnobservedSinceTick();
                        if (since == Integer.MIN_VALUE) {
                            hs.papi$setUnobservedSinceTick(serverTick);
                        } else if (serverTick - since >= PAPI$UNOBSERVED_GRACE_TICKS) {
                            this.carriedBy.remove(i);
                            this.carriedByPlayers.remove(p);
                            if (p instanceof PapiFramedMapPlayer fp) {
                                fp.papi$unregisterHoldingMap(self);
                            }
                            this.removeDecoration(pName);
                        }
                    }
                }
            }
        }

        if (this.trackingPosition) {
            BlockPos pos = placedInFrame.getPos();
            String frameId = MapFrame.frameId(pos);
            MapFrame existingFrame = this.frameMarkers.get(frameId);
            int rot = placedInFrame.getDirection().get2DDataValue() * 90;

            if (existingFrame == null || existingFrame.entityId() != placedInFrame.getId() || existingFrame.rotation() != rot) {
                if (existingFrame != null && existingFrame.entityId() != placedInFrame.getId()) {
                    this.removeDecoration("frame-" + existingFrame.entityId());
                }
                MapFrame mapFrame = new MapFrame(pos, rot, placedInFrame.getId());
                self.addDecoration(
                        MapDecorationTypes.FRAME,
                        (LevelAccessor) tickingPlayer.level(),
                        "frame-" + placedInFrame.getId(),
                        pos.getX(),
                        pos.getZ(),
                        rot,
                        null
                );
                this.frameMarkers.put(frameId, mapFrame);
                self.setDirty();
            }
        }

        MapDecorations staticDecorations = itemStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!staticDecorations.decorations().isEmpty() && staticDecorations != this.papi$lastStaticDecorations) {
            LevelAccessor levelAccessor = (LevelAccessor) tickingPlayer.level();
            for (var entry : staticDecorations.decorations().entrySet()) {
                String id = entry.getKey();
                if (!this.decorations.containsKey(id)) {
                    MapDecorations.Entry val = entry.getValue();
                    self.addDecoration(val.type(), levelAccessor, id, val.x(), val.z(), val.rotation(), null);
                }
            }
            this.papi$lastStaticDecorations = staticDecorations;
        }

        ci.cancel();
    }

    @Inject(method = "setColor", at = @At("HEAD"))
    private void papi$trackColorTransparencyHead(int x, int y, byte color, CallbackInfo ci) {
        this.papi$inSetColor = true;
        int count = this.papi$transparentCount;
        byte[] arr = this.colors;
        if (count >= 0 && ((x | y) & ~127) == 0 && arr != null && arr.length >= 16384) {
            int idx = x + (y << 7);
            boolean wasTrans = FastMapColorCache.isTransparent(arr[idx]);
            boolean nowTrans = FastMapColorCache.isTransparent(color);
            if (wasTrans != nowTrans) {
                this.papi$transparentCount = nowTrans ? (count + 1) : Math.max(0, count - 1);
            }
        }
    }

    @Inject(method = "setColor", at = @At("RETURN"))
    private void papi$trackColorTransparencyReturn(int x, int y, byte color, CallbackInfo ci) {
        this.papi$inSetColor = false;
    }

    @Inject(method = "setColorsDirty", at = @At("HEAD"))
    private void papi$onSetColorsDirty(int x, int y, CallbackInfo ci) {
        if (!this.papi$inSetColor) {
            this.papi$transparentCount = -1;
        }
        int cx = x < 0 ? 0 : (x > 127 ? 127 : x);
        int cy = y < 0 ? 0 : (y > 127 ? 127 : y);
        if (cx < this.papi$dirtyMinX) {
            this.papi$dirtyMinX = cx;
        }
        if (cy < this.papi$dirtyMinY) {
            this.papi$dirtyMinY = cy;
        }
        if (cx > this.papi$dirtyMaxX) {
            this.papi$dirtyMaxX = cx;
        }
        if (cy > this.papi$dirtyMaxY) {
            this.papi$dirtyMaxY = cy;
        }
    }

    @Override
    public int papi$consumeDirtyRect() {
        int minX = this.papi$dirtyMinX;
        int minY = this.papi$dirtyMinY;
        int maxX = this.papi$dirtyMaxX;
        int maxY = this.papi$dirtyMaxY;
        if (minX > maxX || minY > maxY) {
            return PAPI$EMPTY_DIRTY_RECT;
        }
        this.papi$dirtyMinX = 128;
        this.papi$dirtyMinY = 128;
        this.papi$dirtyMaxX = -1;
        this.papi$dirtyMaxY = -1;
        return (minX & 0xFF) | ((minY & 0xFF) << 8) | ((maxX & 0xFF) << 16) | ((maxY & 0xFF) << 24);
    }

    @Override
    public boolean papi$hasTransparency() {
        byte[] arr = this.colors;
        if (arr == null || arr.length < 16384) {
            return true;
        }
        int count = this.papi$transparentCount;
        if (count < 0) {
            int c = 0;
            for (int i = 0; i < 16384; i++) {
                if (FastMapColorCache.isTransparent(arr[i])) {
                    c++;
                }
            }
            this.papi$transparentCount = c;
            count = c;
        }
        return count > 0;
    }
}
