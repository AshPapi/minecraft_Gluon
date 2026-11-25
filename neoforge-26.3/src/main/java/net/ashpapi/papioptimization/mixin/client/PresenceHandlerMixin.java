package net.ashpapi.papioptimization.mixin.client;

import net.minecraft.client.gui.screens.social.PresenceHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PresenceHandler.class)
public abstract class PresenceHandlerMixin {

    @Unique
    private static final long PAPI$CHECK_INTERVAL_NANOS = 2_000_000_000L;

    @Unique
    private long papi$lastCheckNanos;

    @Unique
    private boolean papi$hasChecked;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void papi$throttlePresenceTick(CallbackInfo ci) {
        long now = System.nanoTime();
        if (this.papi$hasChecked && (now - this.papi$lastCheckNanos) < PAPI$CHECK_INTERVAL_NANOS) {
            ci.cancel();
            return;
        }
        this.papi$hasChecked = true;
        this.papi$lastCheckNanos = now;
    }

    @Inject(method = "tryUpdatePresence", at = @At("TAIL"))
    private void papi$resetPresenceThrottle(CallbackInfo ci) {
        this.papi$hasChecked = false;
    }
}
