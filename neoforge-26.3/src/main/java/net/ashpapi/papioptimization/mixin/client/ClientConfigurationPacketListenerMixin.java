package net.ashpapi.papioptimization.mixin.client;

import net.ashpapi.papioptimization.client.PapiClientCaches;
import net.ashpapi.papioptimization.config.PapiConfig;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientConfigurationPacketListenerImpl.class, priority = 1500)
public abstract class ClientConfigurationPacketListenerMixin {

    @Inject(method = "handleConfigurationFinished", at = @At("RETURN"))
    private void papi$invalidateEncoderCacheAfterConfig(ClientboundFinishConfigurationPacket packet, CallbackInfo ci) {
        if (PapiConfig.modernFix()) {
            PapiClientCaches.invalidateEncoderCache();
        }
    }
}
