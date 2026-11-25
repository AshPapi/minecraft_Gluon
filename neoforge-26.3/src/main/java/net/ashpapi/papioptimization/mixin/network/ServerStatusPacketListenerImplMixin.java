package net.ashpapi.papioptimization.mixin.network;

import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.network.protocol.status.ServerboundStatusRequestPacket;
import net.minecraft.server.network.ServerStatusPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerStatusPacketListenerImpl.class, priority = 1500)
public abstract class ServerStatusPacketListenerImplMixin {

    @Shadow
    @Final
    private ServerStatus status;

    @Inject(method = "handleStatusRequest", at = @At("HEAD"), cancellable = true)
    private void papi$guardIncompleteStatus(ServerboundStatusRequestPacket packet, CallbackInfo ci) {
        if (this.status == null || this.status.version().isEmpty()) {
            ci.cancel();
        }
    }
}
