package net.ashpapi.papioptimization.mixin.map;

import net.ashpapi.papioptimization.access.PapiFramedMapPlayer;
import net.ashpapi.papioptimization.access.PapiMapHoldingState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = MapItemSavedData.HoldingPlayer.class, priority = 1500)
public abstract class HoldingPlayerMixin implements PapiMapHoldingState {

    @Shadow
    @Final
    public Player player;

    @Unique
    private int papi$unobservedSinceTick = Integer.MIN_VALUE;

    @Override
    public int papi$getUnobservedSinceTick() {
        return this.papi$unobservedSinceTick;
    }

    @Override
    public void papi$setUnobservedSinceTick(int serverTick) {
        this.papi$unobservedSinceTick = serverTick;
    }

    @Override
    public void papi$clearUnobserved() {
        this.papi$unobservedSinceTick = Integer.MIN_VALUE;
    }

    @Unique
    private static boolean papi$hasMapIdInInventoryOrCarried(Player player, MapId mapId) {
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

    @Inject(method = "nextUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void papi$skipUnobservedMapUpdatePacket(MapId mapId, CallbackInfoReturnable<Packet<?>> cir) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.isRemoved() || serverPlayer.hasDisconnected()) {
            cir.setReturnValue(null);
            return;
        }
        if (this.papi$unobservedSinceTick != Integer.MIN_VALUE) {
            if ((serverPlayer instanceof PapiFramedMapPlayer fp && fp.papi$isTrackingFramedMap(mapId))
                    || papi$hasMapIdInInventoryOrCarried(serverPlayer, mapId)) {
                this.papi$unobservedSinceTick = Integer.MIN_VALUE;
                return;
            }
            cir.setReturnValue(null);
        }
    }
}
