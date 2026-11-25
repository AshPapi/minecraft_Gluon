package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.telemetry.ClientTelemetryManager;
import net.minecraft.client.telemetry.TelemetryEventSender;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@OnlyIn(Dist.CLIENT)
@Mixin(value = ClientTelemetryManager.class, priority = 1500)
public abstract class ClientTelemetryStubMixin {

    @Inject(method = "getOutsideSessionSender", at = @At("HEAD"), cancellable = true)
    private void papi$disableOutsideTelemetry(CallbackInfoReturnable<TelemetryEventSender> cir) {
        cir.setReturnValue(TelemetryEventSender.DISABLED);
    }

    @Inject(method = "createEventSender", at = @At("HEAD"), cancellable = true)
    private void papi$disableEventSender(CallbackInfoReturnable<TelemetryEventSender> cir) {
        cir.setReturnValue(TelemetryEventSender.DISABLED);
    }
}
